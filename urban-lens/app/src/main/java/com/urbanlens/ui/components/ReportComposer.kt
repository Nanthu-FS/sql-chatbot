package com.urbanlens.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.urbanlens.core.reports.ReportType
import com.urbanlens.ui.ReportDraft
import com.urbanlens.ui.theme.UrbanColors

@Composable
fun ReportComposer(
    draft: ReportDraft,
    onChange: ((ReportDraft) -> ReportDraft) -> Unit,
    onPickedPhoto: (Uri) -> Unit,
    onTookPhoto: (Bitmap) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickedPhoto(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) onTookPhoto(bitmap)
    }

    MapCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SectionLabel("New report")
                Text("What's happening here?", style = MaterialTheme.typography.headlineSmall, color = UrbanColors.Paper)
                Text(
                    "Tap the map to move the pin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UrbanColors.Muted,
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel report", tint = UrbanColors.Muted)
            }
        }
        Row(
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportType.entries.forEach { type ->
                FilterChip(
                    selected = draft.type == type,
                    onClick = { onChange { it.copy(type = type) } },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UrbanColors.hex(type.color).copy(alpha = 0.3f),
                        selectedLabelColor = UrbanColors.Paper,
                    ),
                )
            }
        }
        OutlinedTextField(
            value = draft.note,
            onValueChange = { text -> onChange { it.copy(note = text.take(280)) } },
            placeholder = { Text("Optional note, e.g. \"Left lane closed near the flyover\"") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            maxLines = 3,
        )
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rememberPhoto(draft.photoPath, maxPx = 300)?.let { photo ->
                Image(
                    bitmap = photo,
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
            IconButton(onClick = { takePhoto.launch(null) }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Take a photo", tint = UrbanColors.Paper)
            }
            IconButton(onClick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "Choose a photo", tint = UrbanColors.Paper)
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            "Reports are stored on this device for now and fade out unless confirmed.",
            style = MaterialTheme.typography.bodySmall,
            color = UrbanColors.Muted,
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryAction("Cancel", Icons.Filled.Close, onCancel)
            PrimaryAction(if (draft.saving) "Saving" else "Submit", Icons.Filled.Send, onSubmit, enabled = !draft.saving)
        }
    }
}
