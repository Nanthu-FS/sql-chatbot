package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.index
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.theme.Spend

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(onDismiss: () -> Unit, onPick: () -> Unit, onAutoFind: () -> Unit, onManual: () -> Unit, autoFindDays: Int = 30, onSms: () -> Unit = {}) {
    val colors = Spend.ink
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (com.spendlens.app.ui.components.LocalGlass.current > 0.01f) colors.surface.copy(alpha = 0.94f) else colors.surface,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
        dragHandle = { Hairline(Modifier.padding(vertical = 14.dp).width(40.dp), color = colors.lineStrong) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Statement("Add ", "payments", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Label("Read on this phone — nothing is uploaded", color = colors.faint)
            Spacer(Modifier.height(24.dp))
            Option(1, "Choose screenshots", "Pick one or many from your gallery", onPick)
            Option(2, "Auto-find", "Scan Screenshots from the last $autoFindDays days", onAutoFind)
            Option(3, "From bank SMS", "Read debit alerts from the last $autoFindDays days", onSms)
            Option(4, "Add manually", "Cash or card, typed in", onManual)
            Hairline()
            Spacer(Modifier.height(14.dp))
            Text(
                "Tip: share a screenshot to SpendLens straight from GPay, PhonePe, Paytm or your gallery.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.faint,
            )
        }
    }
}

@Composable
private fun Option(n: Int, title: String, body: String, onClick: () -> Unit) {
    val colors = Spend.ink
    Column(Modifier.reveal(n).pressable(pressedScale = 0.97f, onClick = onClick)) {
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
            Label(index(n), color = colors.faint, modifier = Modifier.width(38.dp).padding(top = 5.dp))
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = colors.text)
                Spacer(Modifier.height(3.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
            Text("→", style = MaterialTheme.typography.headlineSmall, color = colors.muted)
        }
    }
}
