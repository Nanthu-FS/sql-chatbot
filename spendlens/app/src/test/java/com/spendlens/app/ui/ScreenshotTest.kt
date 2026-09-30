package com.spendlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.spendlens.app.data.AppSettings
import com.spendlens.app.data.ThemeMode
import com.spendlens.app.domain.Analytics
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.Txn
import com.spendlens.app.ocr.DraftFlag
import com.spendlens.app.ocr.DraftState
import com.spendlens.app.ocr.ImportDraft
import com.spendlens.app.ocr.ImportMode
import com.spendlens.app.ocr.ImportPhase
import com.spendlens.app.ocr.ImportState
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.screens.activity.ActivityContent
import com.spendlens.app.ui.screens.activity.ActivityUiState
import com.spendlens.app.ui.screens.activity.DayGroup
import com.spendlens.app.ui.screens.detail.DetailContent
import com.spendlens.app.ui.screens.edit.EditContent
import com.spendlens.app.ui.screens.edit.EditForm
import com.spendlens.app.ui.screens.home.DashboardContent
import com.spendlens.app.ui.screens.home.HomeActions
import com.spendlens.app.ui.screens.home.Welcome
import com.spendlens.app.ui.screens.review.ReviewActions
import com.spendlens.app.ui.screens.review.ReviewContent
import com.spendlens.app.ui.screens.settings.SettingsContent
import com.spendlens.app.ui.screens.settings.SettingsActions
import com.spendlens.app.ui.screens.settings.SettingsUi
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.SpendLensTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Renders every screen with realistic data and saves PNGs (build/outputs/roborazzi) so the UI can be
 * reviewed without a device. Animations are driven by a manual clock and run to completion first.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.now()
    private val txns: List<Txn> = sample()

    private fun sample(): List<Txn> {
        val rnd = Random(7)
        val places = listOf(
            Triple("Swiggy", Category.FOOD, 180..900), Triple("Blinkit", Category.GROCERIES, 150..1600),
            Triple("Uber", Category.TRANSPORT, 120..650), Triple("Blue Tokai Coffee", Category.FOOD, 220..480),
            Triple("Airtel Postpaid", Category.BILLS, 599..599), Triple("Myntra", Category.SHOPPING, 700..3200),
            Triple("Apollo Pharmacy", Category.HEALTH, 90..900), Triple("Priya Sharma", Category.TRANSFERS, 200..2500),
            Triple("PVR Cinemas", Category.ENTERTAINMENT, 350..900),
        )
        return (0 until 140).map { i ->
            val (name, cat, range) = places[rnd.nextInt(places.size)]
            val at = now.minusDays(rnd.nextLong(0, 200)).withHour(rnd.nextInt(8, 23)).withMinute(rnd.nextInt(60))
            Txn(i.toLong(), rnd.nextInt(range.first, range.last + 1) * 100L, name, cat, at.coerceAtMost(now), paymentApp = listOf("Google Pay", "PhonePe", "Paytm")[i % 3], reference = "4265${1000000 + i}")
        }.sortedByDescending { it.dateTime }
    }

    private fun LocalDateTime.coerceAtMost(max: LocalDateTime) = if (isAfter(max)) max else this

    private fun shoot(name: String, dark: Boolean = true, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SpendLensTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalCurrency provides CurrencyOption.INR) {
                    Box(Modifier.fillMaxSize().background(Spend.ink.canvas)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun dashboard(type: PeriodType) =
        Analytics.build(txns, Period(type, now.toLocalDate()), now, CurrencyOption.INR, monthlyBudget = 30_000_00)

    @Test fun welcome() = shoot("01_welcome") { Welcome({}, {}, {}) }

    @Test fun homeMonth() = shoot("02_home_month") { DashboardContent(dashboard(PeriodType.MONTH), HomeActions()) }

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun homeMonthFull() = shoot("03_home_month_full") { DashboardContent(dashboard(PeriodType.MONTH), HomeActions()) }

    @Config(qualifiers = "w400dp-h3000dp-xxhdpi")
    @Test fun homeYearFull() = shoot("04_home_year_full") { DashboardContent(dashboard(PeriodType.YEAR), HomeActions()) }

    @Test fun homeDay() = shoot("05_home_day") {
        val day = txns.first().dateTime.toLocalDate()
        DashboardContent(Analytics.build(txns, Period(PeriodType.DAY, day), now, CurrencyOption.INR, 30_000_00), HomeActions())
    }

    @Test fun homeLight() = shoot("06_home_light", dark = false) { DashboardContent(dashboard(PeriodType.MONTH), HomeActions()) }

    @Test fun activity() = shoot("07_activity") {
        val groups = txns.take(30).groupBy { it.dateTime.toLocalDate() }.map { (d, l) -> DayGroup(d, l.sumOf { it.amountMinor }, l) }
        ActivityContent(
            ActivityUiState(false, null, groups, Category.entries.take(8), txns.sumOf { it.amountMinor }, txns.size, true),
            "", {}, {}, {}, {},
        )
    }

    @Test fun review() = shoot("08_review") {
        val state = ImportState(
            mode = ImportMode.PICKED, phase = ImportPhase.SCANNING, total = 3, processed = 2,
            drafts = listOf(
                ImportDraft(sourceUri = "a", state = DraftState.READY, amountText = "450", merchant = "Swiggy", category = Category.FOOD, dateTime = now.minusHours(2), paymentApp = "Google Pay"),
                ImportDraft(sourceUri = "b", state = DraftState.READY, amountText = "1249", merchant = "Blinkit", category = Category.GROCERIES, dateTime = now.minusDays(1), paymentApp = "PhonePe", include = false, flags = setOf(DraftFlag.DUPLICATE)),
                ImportDraft(sourceUri = "c"),
            ),
        )
        ReviewContent(state, saving = false, actions = ReviewActions())
    }

    @Test fun detail() = shoot("09_detail") {
        DetailContent(txns.first().copy(rawText = "Paid to SWIGGY\n₹450\nCompleted\nUPI transaction ID 426512345678"), {}, {}, {})
    }

    @Test fun edit() = shoot("10_edit") {
        EditContent(EditForm(amountText = "320", merchant = "Chai Point", category = Category.FOOD), isNew = true, canSave = true, onUpdate = {}, onSave = {}, onClose = {})
    }

    @Config(qualifiers = "w400dp-h1500dp-xxhdpi")
    @Test fun settings() = shoot("11_settings") {
        SettingsContent(SettingsUi(AppSettings(monthlyBudgetMinor = 30_000_00, theme = ThemeMode.DARK), txns), SettingsActions())
    }

}
