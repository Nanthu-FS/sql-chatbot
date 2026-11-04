package com.pulse.tracker.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.pulse.tracker.PulseViewModel
import com.pulse.tracker.data.AppData
import com.pulse.tracker.data.Food
import com.pulse.tracker.data.FoodCatalog
import com.pulse.tracker.data.FoodEntry
import com.pulse.tracker.data.Meal
import com.pulse.tracker.data.OpenFoodFacts
import com.pulse.tracker.ui.components.PButton
import com.pulse.tracker.ui.components.PChip
import com.pulse.tracker.ui.components.PDialog
import com.pulse.tracker.ui.components.PIconButton
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PTextField
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.SectionTitle
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.theme.CardTint
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.ThemeId
import com.pulse.tracker.ui.theme.pulse
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFoodScreen(
    vm: PulseViewModel,
    data: AppData,
    date: LocalDate,
    initialMeal: Meal,
    onBack: () -> Unit,
) {
    val t = pulse
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var meal by remember { mutableStateOf(initialMeal) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Pair<Food, Double>?>(null) }
    var quickAdd by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val results = remember(query, data.customFoods) { FoodCatalog.search(query, data.customFoods).take(60) }
    val recents = remember(data.foods) { vm.recentFoods(data) }
    val logged = data.foods.filter { it.date == date && it.meal == meal }.sortedBy { it.loggedAt }

    fun scan() {
        status = null
        startScan(context, onCode = { code ->
            status = "Looking up $code…"
            scope.launch {
                when (val r = vm.lookupBarcode(code)) {
                    is OpenFoodFacts.Result.Found -> { status = null; selected = r.food to r.food.servingGrams }
                    OpenFoodFacts.Result.NotFound -> status = "Not in Open Food Facts yet — use Quick add."
                    is OpenFoodFacts.Result.Error -> status = r.message
                }
            }
        }, onError = { status = it })
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PIconButton(PIcons.Back, "Back", onBack)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    PText(if (t.id == ThemeId.NEON) "LOG_FOOD" else "Log food", size = 20.sp, display = true, italic = t.id == ThemeId.PAPER)
                    PText(
                        if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                        size = 12.sp, color = t.c.muted,
                    )
                }
                Box(Modifier.size(44.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { m -> PChip(m.label, m == meal, { meal = m }) }
            }
        }
        item { PTextField(query, { query = it }, "Search foods, brands, meals", Modifier.fillMaxWidth(), leading = PIcons.Search) }
        item {
            Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Scan barcode", "Packaged foods", PIcons.Scan, accent = true, modifier = Modifier.weight(1f)) { scan() }
                ActionTile("Quick add", "Calories & macros", PIcons.Bolt, accent = false, modifier = Modifier.weight(1f)) { quickAdd = true }
            }
        }
        status?.let { msg -> item { PText(msg, size = 13.sp, color = t.c.accentText) } }

        if (query.isBlank()) {
            if (logged.isNotEmpty()) {
                item { SectionTitle("In ${meal.label} · ${formatInt(logged.sumOf { it.kcal })} kcal") }
                items(logged, key = { "e" + it.id }) { e -> LoggedRow(e) { vm.deleteFood(e.id) } }
            }
            if (recents.isNotEmpty()) {
                item { SectionTitle("Recent") }
                items(recents, key = { "r" + it.first.id }) { (food, grams) ->
                    FoodRow(food, grams, onAdd = {
                        vm.logFood(food, grams, meal, date)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) { selected = food to grams }
                }
            }
            item { SectionTitle("Browse") }
        } else {
            item { SectionTitle("${results.size} results") }
        }
        items(results, key = { "f" + it.id }) { food ->
            FoodRow(food, food.servingGrams, onAdd = {
                vm.logFood(food, food.servingGrams, meal, date)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }) { selected = food to food.servingGrams }
        }
        if (query.isNotBlank() && results.isEmpty()) {
            item {
                PulseCard(Modifier.fillMaxWidth(), onClick = { quickAdd = true }) {
                    PText("No match for “$query”", size = 15.sp, weight = FontWeight.SemiBold)
                    PText("Tap to add it with Quick add.", size = 13.sp, color = LocalMuted.current)
                }
            }
        }
    }

    selected?.let { (food, grams) ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
            containerColor = if (t.id == ThemeId.PAPER || t.id == ThemeId.SWISS) t.c.bg else t.c.surface,
            contentColor = t.c.text,
            shape = RoundedCornerShape(topStart = if (t.radius > 0.dp) 32.dp else 0.dp, topEnd = if (t.radius > 0.dp) 32.dp else 0.dp),
        ) {
            PortionSheet(food, grams, meal) { g ->
                vm.logFood(food, g, meal, date)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch { sheetState.hide() }.invokeOnCompletion { selected = null }
            }
        }
    }

    if (quickAdd) QuickAddDialog(initialName = query, onDismiss = { quickAdd = false }) { food ->
        vm.logFood(food, 100.0, meal, date)
        quickAdd = false
        query = ""
    }
}

private fun startScan(context: Context, onCode: (String) -> Unit, onError: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
    try {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { b -> b.rawValue?.let(onCode) ?: onError("Couldn’t read that barcode.") }
            .addOnFailureListener { onError("Scanner unavailable — Google Play services is needed.") }
    } catch (e: Exception) {
        onError("Scanner unavailable on this device.")
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = pulse
    val tint = if (accent) CardTint.HERO else CardTint.DEFAULT
    val accentFill = accent && t.id in setOf(ThemeId.VOLT, ThemeId.MATCHA)
    if (accentFill) {
        Column(
            modifier.height(96.dp).clip(RoundedCornerShape(t.radius)).background(t.c.accent).clickable(onClick = onClick).padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = t.c.onAccent)
            Column {
                PText(title, size = 15.sp, weight = FontWeight.Bold, color = t.c.onAccent)
                PText(subtitle, size = 12.sp, color = t.c.onAccent.copy(alpha = 0.75f))
            }
        }
    } else {
        PulseCard(modifier.height(96.dp), tint = tint, onClick = onClick, padding = 16.dp) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (accent && t.id == ThemeId.NEON) t.c.accent else androidx.compose.material3.LocalContentColor.current)
            Spacer(Modifier.weight(1f))
            PText(if (t.upperLabels) title.uppercase() else title, size = if (t.upperLabels) 13.sp else 15.sp, weight = FontWeight.Bold)
            PText(subtitle, size = 12.sp, color = LocalMuted.current)
        }
    }
}

@Composable
private fun MacroBars(p: Double, c: Double, f: Double, modifier: Modifier = Modifier) {
    val t = pulse
    val total = (p * 4 + c * 4 + f * 9).coerceAtLeast(1.0)
    Row(modifier.width(44.dp).height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
        listOf(p * 4 to t.c.protein, c * 4 to t.c.carbs, f * 9 to t.c.fat).forEach { (k, col) ->
            Box(
                Modifier.padding(bottom = 10.dp).width(5.dp).height((4 + 24 * (k / total)).dp)
                    .clip(RoundedCornerShape(if (t.radius > 0.dp) 2.dp else 0.dp)).background(col),
            )
        }
    }
}

@Composable
private fun FoodRow(food: Food, grams: Double, onAdd: () -> Unit, onOpen: () -> Unit) {
    val t = pulse
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(if (t.radius > 0.dp) 16.dp else 0.dp)).clickable(onClick = onOpen).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(if (t.radius > 0.dp) 14.dp else 0.dp))
                .background(if (t.cardStyle == com.pulse.tracker.ui.theme.CardStyle.RULED || t.cardStyle == com.pulse.tracker.ui.theme.CardStyle.SWISS) t.c.surfaceAlt else t.c.surface),
        ) { MacroBars(food.protein, food.carbs, food.fat) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PText(food.name, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1)
            PText(
                listOfNotNull(food.brand, food.servingName, "${food.kcalFor(grams).roundToInt()} kcal").joinToString(" · "),
                size = 12.sp, color = t.c.muted, maxLines = 1,
            )
        }
        PIconButton(PIcons.Plus, "Add ${food.name}", onAdd, size = 38.dp)
    }
}

@Composable
private fun LoggedRow(e: FoodEntry, onDelete: () -> Unit) {
    val t = pulse
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(t.c.accentText))
        Column(Modifier.weight(1f)) {
            PText(e.name, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
            PText("${e.grams.roundToInt()} g · P ${e.protein.roundToInt()} · C ${e.carbs.roundToInt()} · F ${e.fat.roundToInt()}", size = 12.sp, color = t.c.muted)
        }
        PText("${e.kcal.roundToInt()}", size = 16.sp, display = true, weight = FontWeight.SemiBold)
        PIconButton(PIcons.Trash, "Remove ${e.name}", onDelete, size = 36.dp)
    }
}

@Composable
private fun PortionSheet(food: Food, initialGrams: Double, meal: Meal, onAdd: (Double) -> Unit) {
    val t = pulse
    var grams by remember(food.id) { mutableDoubleStateOf(initialGrams) }
    val presets = buildList {
        if (food.servingGrams != 100.0) add(food.servingName to food.servingGrams)
        add("100 g" to 100.0)
        if (food.servingGrams != 100.0) add("½ serving" to food.servingGrams / 2) else add("50 g" to 50.0)
        add("2× serving" to food.servingGrams * 2)
    }.distinctBy { it.second }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PText(food.name, size = 21.sp, display = true, weight = FontWeight.SemiBold, lineHeight = 24.sp)
                PText(listOfNotNull(food.brand, "${food.kcal.roundToInt()} kcal per 100 g").joinToString(" · "), size = 12.sp, color = t.c.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                PText("${food.kcalFor(grams).roundToInt()}", size = 32.sp, display = true, color = t.c.accentText)
                PText("kcal", size = 12.sp, color = t.c.muted)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PText("Portion", size = 12.sp, color = t.c.muted, weight = FontWeight.Medium)
                PText("${grams.roundToInt()} g", size = 13.sp, weight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, g) -> PChip(label, kotlin.math.abs(grams - g) < 0.5, { grams = g }, height = 38.dp) }
            }
            Slider(
                value = grams.toFloat().coerceIn(5f, 1000f),
                onValueChange = { grams = (it / 5).roundToInt() * 5.0 },
                valueRange = 5f..1000f,
                colors = SliderDefaults.colors(thumbColor = t.c.accentText, activeTrackColor = t.c.accentText, inactiveTrackColor = t.c.track),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroCell("Protein", food.proteinFor(grams), t.c.protein, Modifier.weight(1f))
            MacroCell("Carbs", food.carbsFor(grams), t.c.carbs, Modifier.weight(1f))
            MacroCell("Fat", food.fatFor(grams), t.c.fat, Modifier.weight(1f))
        }
        PButton("Add to ${meal.label}", { onAdd(grams) }, Modifier.fillMaxWidth(), icon = PIcons.Check)
    }
}

@Composable
private fun MacroCell(label: String, grams: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val t = pulse
    Column(
        modifier.clip(RoundedCornerShape(if (t.radius > 0.dp) 16.dp else 0.dp)).background(t.c.surfaceAlt).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PText(label, size = 11.sp, color = t.c.muted)
        PText("${grams.roundToInt()}g", size = 17.sp, display = true, weight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun QuickAddDialog(initialName: String, onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    val kcalValue = kcal.replace(',', '.').toDoubleOrNull()
    PDialog(
        title = "Quick add",
        onDismiss = onDismiss,
        confirmText = "Add",
        confirmEnabled = kcalValue != null && kcalValue > 0,
        onConfirm = {
            val food = Food(
                id = "custom_${System.currentTimeMillis()}",
                name = name.trim().ifBlank { "Quick add" },
                kcal = kcalValue ?: 0.0,
                protein = p.replace(',', '.').toDoubleOrNull() ?: 0.0,
                carbs = c.replace(',', '.').toDoubleOrNull() ?: 0.0,
                fat = f.replace(',', '.').toDoubleOrNull() ?: 0.0,
                servingName = "1 serving",
                servingGrams = 100.0,
            )
            onSave(food)
        },
    ) {
        PText("Values are for the whole portion. It’s saved so you can find it again.", size = 13.sp, color = pulse.c.muted)
        PTextField(name, { name = it }, "Name (e.g. Mum’s curry)", Modifier.fillMaxWidth())
        PTextField(kcal, { kcal = it }, "Calories (kcal)", Modifier.fillMaxWidth(), number = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PTextField(p, { p = it }, "Protein g", Modifier.weight(1f), number = true)
            PTextField(c, { c = it }, "Carbs g", Modifier.weight(1f), number = true)
            PTextField(f, { f = it }, "Fat g", Modifier.weight(1f), number = true)
        }
    }
}
