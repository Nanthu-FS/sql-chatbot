package com.spendlens.app.ocr

import android.content.Context
import android.net.Uri
import com.spendlens.app.data.TransactionEntity
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.data.toEpochMillis
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CategoryClassifier
import com.spendlens.app.domain.Money
import com.spendlens.app.domain.PaymentParser
import com.spendlens.app.domain.PaymentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.abs

enum class ImportMode { PICKED, AUTO_FIND }

enum class ImportPhase { IDLE, FINDING, SCANNING, DONE, SAVING }

enum class DraftState { SCANNING, READY, ERROR }

enum class DraftFlag(val label: String, val excludeByDefault: Boolean) {
    DUPLICATE("Possible duplicate", true),
    INCOMING("Money received", true),
    FAILED("Failed payment", true),
    PENDING("Pending payment", false),
    NO_AMOUNT("Enter the amount", false),
    UNSURE("Doesn't look like a payment", true),
}

data class ImportDraft(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val stagedPath: String? = null,
    val state: DraftState = DraftState.SCANNING,
    val amountText: String = "",
    val merchant: String = "",
    val category: Category = Category.OTHER,
    val dateTime: LocalDateTime = LocalDateTime.now(),
    val paymentApp: String? = null,
    val reference: String? = null,
    val rawText: String = "",
    val include: Boolean = true,
    val flags: Set<DraftFlag> = emptySet(),
) {
    val amountMinor: Long? get() = Money.parseInput(amountText)
    val isValid: Boolean get() = state == DraftState.READY && amountMinor != null
}

data class ImportState(
    val mode: ImportMode = ImportMode.PICKED,
    val phase: ImportPhase = ImportPhase.IDLE,
    val total: Int = 0,
    val processed: Int = 0,
    val drafts: List<ImportDraft> = emptyList(),
    val skipped: Int = 0,
    val alreadyImported: Int = 0,
) {
    val selected: List<ImportDraft> get() = drafts.filter { it.include && it.isValid }
    val isWorking: Boolean get() = phase == ImportPhase.FINDING || phase == ImportPhase.SCANNING
}

/** Runs screenshots through OCR + parsing and holds the drafts the user reviews before saving. */
class ImportManager(
    context: Context,
    private val repository: TransactionRepository,
    private val images: ImageStore,
    private val ocr: OcrEngine,
    private val finder: ScreenshotFinder,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private val seenPrefs = context.getSharedPreferences("auto_find", Context.MODE_PRIVATE)
    private var job: Job? = null

    fun startPicked(uris: List<Uri>) {
        discard()
        val drafts = uris.distinct().map { ImportDraft(sourceUri = it.toString()) }
        _state.value = ImportState(ImportMode.PICKED, ImportPhase.SCANNING, total = drafts.size, drafts = drafts)
        job = scope.launch {
            for (draft in drafts) {
                val result = analyze(Uri.parse(draft.sourceUri), draft, strict = false)
                // Removed by the user while it was being scanned.
                if (_state.value.drafts.none { it.id == draft.id }) result?.stagedPath?.let { File(it).delete() }
                _state.update { s ->
                    s.copy(processed = s.processed + 1, drafts = s.drafts.map { if (it.id == draft.id && result != null) result else it })
                }
            }
            _state.update { it.copy(phase = ImportPhase.DONE) }
        }
    }

    fun startAutoFind(days: Int = 30) {
        discard()
        _state.value = ImportState(ImportMode.AUTO_FIND, ImportPhase.FINDING)
        job = scope.launch {
            val known = repository.importedSourceUris() + seen()
            val shots = runCatching { finder.recent(days) }.getOrDefault(emptyList())
            val fresh = shots.filter { it.toString() !in known }
            _state.update { it.copy(phase = ImportPhase.SCANNING, total = fresh.size, alreadyImported = shots.size - fresh.size) }
            val notPayments = mutableListOf<String>()
            for (uri in fresh) {
                val result = analyze(uri, ImportDraft(sourceUri = uri.toString()), strict = true)
                if (result == null) notPayments += uri.toString()
                _state.update { s ->
                    s.copy(
                        processed = s.processed + 1,
                        skipped = s.skipped + if (result == null) 1 else 0,
                        drafts = if (result != null) s.drafts + result else s.drafts,
                    )
                }
            }
            remember(notPayments)
            _state.update { it.copy(phase = ImportPhase.DONE) }
        }
    }

    fun update(id: String, transform: (ImportDraft) -> ImportDraft) {
        _state.update { s -> s.copy(drafts = s.drafts.map { if (it.id == id) transform(it) else it }) }
    }

    fun remove(id: String) {
        val draft = _state.value.drafts.firstOrNull { it.id == id } ?: return
        draft.stagedPath?.let { File(it).delete() }
        _state.update { s -> s.copy(drafts = s.drafts.filterNot { it.id == id }) }
    }

    /** Saves every included draft and returns how many were stored. */
    suspend fun save(): Int {
        val current = _state.value
        val toSave = current.selected
        _state.update { it.copy(phase = ImportPhase.SAVING) }
        val entities = toSave.map { d ->
            TransactionEntity(
                amountMinor = d.amountMinor ?: 0,
                merchant = d.merchant.trim().ifBlank { "Unknown payee" },
                category = d.category.key,
                timestamp = d.dateTime.toEpochMillis(),
                paymentApp = d.paymentApp,
                reference = d.reference,
                imagePath = d.stagedPath?.let { images.persist(File(it))?.absolutePath },
                sourceUri = d.sourceUri,
                rawText = d.rawText,
            )
        }
        repository.saveAll(entities)
        if (current.mode == ImportMode.AUTO_FIND) remember(current.drafts.map { it.sourceUri })
        val savedIds = toSave.map { it.id }.toSet()
        images.discard(current.drafts.filter { it.id !in savedIds }.mapNotNull { it.stagedPath })
        _state.value = ImportState()
        return entities.size
    }

    fun discard() {
        job?.cancel()
        job = null
        val paths = _state.value.drafts.mapNotNull { it.stagedPath }
        _state.value = ImportState()
        scope.launch { images.discard(paths) }
    }

    private suspend fun analyze(uri: Uri, base: ImportDraft, strict: Boolean): ImportDraft? {
        val loaded = try {
            images.load(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (strict) null else base.copy(state = DraftState.ERROR, include = false)
        }
        val lines = try {
            ocr.read(loaded.bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        return withContext(Dispatchers.Default) {
            val meta = images.meta(uri)
            val shotTime = PaymentParser.dateFromFileName(meta.displayName) ?: meta.takenAt
            val parsed = PaymentParser.parse(lines, now = shotTime ?: LocalDateTime.now())
            val looksLikePayment = parsed.amountMinor != null && parsed.confidence >= 0.45f

            if (strict && (!looksLikePayment || parsed.isIncoming)) {
                loaded.staged.delete()
                return@withContext null
            }

            val parsedDate = parsed.dateTime
            val dateTime = when {
                parsedDate != null && parsed.hasTime -> parsedDate
                // The receipt only had a date: borrow the time from the screenshot if it's the same day.
                parsedDate != null && shotTime != null && shotTime.toLocalDate() == parsedDate.toLocalDate() -> shotTime
                parsedDate != null -> parsedDate
                else -> shotTime ?: LocalDateTime.now()
            }
            val fileApp = PaymentParser.appFromFileName(meta.displayName)
            val app = if (parsed.paymentApp == null || parsed.paymentApp == "UPI") fileApp ?: parsed.paymentApp else parsed.paymentApp

            val amount = parsed.amountMinor
            val duplicate = amount != null && (
                repository.isDuplicate(amount, dateTime, parsed.reference) || isBatchDuplicate(base.id, amount, dateTime, parsed.reference)
                )
            val flags = buildSet {
                if (amount == null) add(DraftFlag.NO_AMOUNT)
                if (duplicate) add(DraftFlag.DUPLICATE)
                if (parsed.isIncoming) add(DraftFlag.INCOMING)
                if (parsed.status == PaymentStatus.FAILED) add(DraftFlag.FAILED)
                if (parsed.status == PaymentStatus.PENDING) add(DraftFlag.PENDING)
                if (!looksLikePayment && amount != null) add(DraftFlag.UNSURE)
            }
            base.copy(
                stagedPath = loaded.staged.absolutePath,
                state = DraftState.READY,
                amountText = amount?.let { Money.toInput(it) }.orEmpty(),
                merchant = parsed.merchant.orEmpty(),
                category = CategoryClassifier.classify(parsed.merchant, parsed.rawText),
                dateTime = dateTime,
                paymentApp = app,
                reference = parsed.reference,
                rawText = parsed.rawText,
                include = flags.none { it.excludeByDefault },
                flags = flags,
            )
        }
    }

    private fun isBatchDuplicate(selfId: String, amount: Long, time: LocalDateTime, reference: String?): Boolean =
        _state.value.drafts.any { other ->
            other.id != selfId && other.state == DraftState.READY && (
                (reference != null && other.reference == reference) ||
                    (other.amountMinor == amount && abs(Duration.between(other.dateTime, time).toMinutes()) <= 3)
                )
        }

    private fun seen(): Set<String> = seenPrefs.getStringSet(KEY_SEEN, emptySet()).orEmpty()

    private fun remember(uris: Collection<String>) {
        if (uris.isEmpty()) return
        seenPrefs.edit().putStringSet(KEY_SEEN, seen() + uris).apply()
    }

    private companion object {
        const val KEY_SEEN = "seen_uris"
    }
}
