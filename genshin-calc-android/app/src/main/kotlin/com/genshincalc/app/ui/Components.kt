package com.genshincalc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genshincalc.core.model.CharacterData
import com.genshincalc.core.model.Element
import com.genshincalc.core.text.Format

@Composable
fun ElementDot(element: Element, size: Dp = 10.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(element.color),
    )
}

/** Small colored label such as "Pyro". */
@Composable
fun ElementTag(element: Element) {
    Surface(color = element.color.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ElementDot(element, 8.dp)
            Spacer(Modifier.width(4.dp))
            Text(element.display, style = MaterialTheme.typography.labelMedium, color = element.color)
        }
    }
}

/** Round avatar with the character's initials on its element color. */
@Composable
fun CharacterAvatar(character: CharacterData, size: Dp = 44.dp, selected: Boolean = false) {
    val initials = character.name
        .replace("(", " ").replace(")", " ")
        .split(" ").filter { it.isNotBlank() }
        .let { words -> if (words.size >= 2) "${words[0][0]}${words[1][0]}" else character.name.take(2) }
        .uppercase()
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(character.element.color.copy(alpha = 0.28f))
            .border(
                width = if (selected) 3.dp else 1.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else rarityColor(character.rarity),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.34f).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun Stars(rarity: Int) {
    Text("★".repeat(rarity.coerceIn(0, 5)), color = rarityColor(rarity), style = MaterialTheme.typography.labelMedium)
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                action?.invoke(this)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun StatLine(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, color: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/**
 * Text field for a number. Keeps the user's text while typing and reports parsed values.
 */
@Composable
fun NumberField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    percent: Boolean = false,
    suffix: String? = null,
    tag: String? = null,
) {
    val shown = if (percent) Format.trim(value * 100) else Format.trim(value)
    var text by remember { mutableStateOf(shown) }
    // Sync when the value changes from outside (e.g. reset), but not while it matches the text.
    LaunchedEffect(shown) {
        val parsed = Format.parse(text)
        val current = if (parsed == null) null else if (percent) parsed / 100 else parsed
        if (current == null || kotlin.math.abs(current - value) > 1e-9) text = shown
    }
    val suffixText = suffix ?: if (percent) "%" else null
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            Format.parse(t)?.let { onValue(if (percent) it / 100 else it) }
            if (t.isBlank()) onValue(0.0)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        suffix = if (suffixText != null) {
            { Text(suffixText) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

/** "- value +" control. */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier, valueText: String = value.toString()) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { if (value > range.first) onChange(value - 1) }, modifier = Modifier.size(32.dp)) {
                Text("−", fontWeight = FontWeight.Bold)
            }
            Text(valueText, modifier = Modifier.widthIn(min = 36.dp).padding(horizontal = 6.dp), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            FilledTonalIconButton(onClick = { if (value < range.last) onChange(value + 1) }, modifier = Modifier.size(32.dp)) {
                Text("+", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Compact dropdown showing the selected option. */
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().clickable { open = true },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(optionLabel(selected), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, textColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp))
}

val ContentPadding = Arrangement.spacedBy(12.dp)
