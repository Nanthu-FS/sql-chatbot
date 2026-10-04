package com.urbanlens.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.urbanlens.core.alerts.AlertState
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.reports.CommunityReport
import com.urbanlens.core.reports.ReportPolicy
import com.urbanlens.core.reports.ReportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SavedPlace(
    val id: Long,
    val name: String,
    val location: LatLng,
    val alertState: AlertState,
    val lastAqi: Int?,
    val lastCheckedAtMillis: Long,
)

/** Community reports, stored on this device for now. */
class ReportRepository(private val context: Context, private val dao: ReportDao) {

    fun observe(): Flow<List<CommunityReport>> = dao.observeAll().map { rows -> rows.mapNotNull { it.toModel() } }

    suspend fun add(type: ReportType, location: LatLng, note: String, photoPath: String?): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            ReportEntity(
                type = type.name,
                lat = location.lat,
                lng = location.lng,
                note = note.trim(),
                photoPath = photoPath,
                createdAt = now,
                confirmations = 0,
                lastConfirmedAt = now,
            ),
        )
    }

    suspend fun confirm(id: Long) {
        val row = dao.get(id) ?: return
        dao.update(row.copy(confirmations = row.confirmations + 1, lastConfirmedAt = System.currentTimeMillis()))
    }

    suspend fun remove(id: Long) {
        dao.get(id)?.photoPath?.let { deletePhoto(it) }
        dao.delete(id)
    }

    /** Deletes reports (and their photos) that have expired. */
    suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis()) {
        dao.getAll().forEach { row ->
            val model = row.toModel()
            if (model == null || !ReportPolicy.isActive(model, nowMillis)) remove(row.id)
        }
    }

    suspend fun savePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_PHOTO_PX) }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        bitmap?.let { writeJpeg(it) }
    }

    suspend fun savePhoto(bitmap: Bitmap): String? = withContext(Dispatchers.IO) { writeJpeg(bitmap) }

    fun deletePhoto(path: String) {
        runCatching { File(path).delete() }
    }

    private fun writeJpeg(bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, "report_photos").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        file.absolutePath
    }.getOrNull()

    private fun ReportEntity.toModel(): CommunityReport? {
        val reportType = ReportType.entries.firstOrNull { it.name == type } ?: return null
        return CommunityReport(
            id = id,
            type = reportType,
            location = LatLng(lat, lng),
            note = note,
            photoPath = photoPath,
            createdAtMillis = createdAt,
            confirmations = confirmations,
            lastConfirmedAtMillis = lastConfirmedAt,
        )
    }

    companion object {
        private const val MAX_PHOTO_PX = 1600

        fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= maxPx || height / (sample * 2) >= maxPx) sample *= 2
            return sample
        }
    }
}

class SavedPlaceRepository(private val dao: SavedPlaceDao) {
    fun observe(): Flow<List<SavedPlace>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun add(name: String, location: LatLng): Long =
        dao.insert(SavedPlaceEntity(name = name.trim().ifEmpty { "Saved place" }, lat = location.lat, lng = location.lng))

    suspend fun remove(id: Long) = dao.delete(id)

    private fun SavedPlaceEntity.toModel() = SavedPlace(
        id = id,
        name = name,
        location = LatLng(lat, lng),
        alertState = AlertState(alerting, lastNotifiedAt),
        lastAqi = lastAqi,
        lastCheckedAtMillis = lastCheckedAt,
    )
}
