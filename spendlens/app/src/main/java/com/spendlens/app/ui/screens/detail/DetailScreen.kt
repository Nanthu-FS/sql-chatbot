package com.spendlens.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Txn
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.CategoryBadge
import com.spendlens.app.ui.components.ImageViewer
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.Pill
import com.spendlens.app.ui.components.color
import com.spendlens.app.ui.components.icon
import com.spendlens.app.ui.theme.SpendTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Loaded(val txn: Txn) : DetailState
}

class DetailViewModel(repository: TransactionRepository, id: Long) : ViewModel() {
    val state: StateFlow<DetailState> = repository.observe(id)
        .map { txn -> if (txn == null) DetailState.Missing else DetailState.Loaded(txn) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)
}

@Composable
fun DetailScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val vm = appViewModel(key = "detail-$id") { DetailViewModel(it.repository, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        DetailState.Loading -> Box(Modifier.fillMaxSize())
        DetailState.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This payment was deleted.", color = SpendTheme.colors.textMuted)
        }
        is DetailState.Loaded -> DetailContent(s.txn, onBack, onEdit, onDelete)
    }
}

@Composable
private fun DetailContent(txn: Txn, onBack: () -> Unit, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    var viewImage by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    val accent = txn.category.color

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Edit, "Edit") { onEdit(txn.id) }
            Spacer(Modifier.width(10.dp))
            RoundIcon(Icons.Rounded.DeleteOutline, "Delete", tint = colors.negative) { confirmDelete = true }
        }
        Spacer(Modifier.height(8.dp))

        // Amount header
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CategoryBadge(txn.category, size = 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(txn.merchant, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "-" + currency.format(txn.amountMinor),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(txn.category.label, accent, icon = txn.category.icon)
                txn.paymentApp?.let { Pill(it, MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(22.dp))

        // Screenshot
        if (txn.imagePath != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), colors.subtle)))
                    .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                    .clickable { viewImage = true },
            ) {
                AsyncImage(
                    model = File(txn.imagePath),
                    contentDescription = "Payment screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ZoomIn, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View", style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Facts
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            InfoRow("Paid to", txn.merchant)
            InfoRow("Date", Format.dayHeader(txn.dateTime.toLocalDate()) + " · " + Format.date(txn.dateTime.toLocalDate()))
            InfoRow("Time", Format.time(txn.dateTime))
            InfoRow("Category", txn.category.label)
            txn.paymentApp?.let { InfoRow("Paid via", it) }
            txn.reference?.let { ref ->
                InfoRow("Reference", ref, trailing = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(ref)) }) {
                        Icon(Icons.Rounded.ContentCopy, "Copy reference", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    }
                })
            }
            txn.note?.let { InfoRow("Note", it) }
        }

        val raw = txn.rawText
        if (!raw.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            val rotation by animateFloatAsState(if (showRaw) 180f else 0f, label = "chevron")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.subtle)
                    .clickable { showRaw = !showRaw }
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.TextSnippet, null, tint = colors.textMuted)
                    Spacer(Modifier.width(10.dp))
                    Text("Text read from the screenshot", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.rotate(rotation), tint = colors.textMuted)
                }
                AnimatedVisibility(visible = showRaw) {
                    Text(
                        raw,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    if (viewImage && txn.imagePath != null) {
        ImageViewer(model = File(txn.imagePath), onDismiss = { viewImage = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this payment?") },
            text = { Text("${currency.format(txn.amountMinor)} to ${txn.merchant} will be removed from your stats.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(txn.id)
                }) { Text("Delete", color = colors.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    description: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(SpendTheme.colors.subtle),
    ) {
        Icon(icon, description, tint = tint)
    }
}

@Composable
private fun InfoRow(label: String, value: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SpendTheme.colors.textMuted, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        trailing?.invoke()
    }
}
