package com.spendlens.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.spendlens.app.data.AppSettings
import com.spendlens.app.data.ThemeMode
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.Comparer
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Goal
import com.spendlens.app.domain.GoalPlanner
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.SampleData
import com.spendlens.app.domain.Txn
import com.spendlens.app.domain.TxnSource
import com.spendlens.app.domain.WrapBuilder
import com.spendlens.app.domain.AnomalyDetector
import com.spendlens.app.ocr.DraftFlag
import com.spendlens.app.ocr.DraftState
import com.spendlens.app.ocr.ImportDraft
import com.spendlens.app.ocr.ImportMode
import com.spendlens.app.ocr.ImportPhase
import com.spendlens.app.ocr.ImportState
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.LocalGlass
import com.spendlens.app.ui.components.MotionSettings
import com.spendlens.app.ui.screens.activity.ActivityContent
import com.spendlens.app.ui.screens.activity.ActivityUiState
import com.spendlens.app.ui.screens.activity.DayGroup
import com.spendlens.app.ui.screens.compare.CompareActions
import com.spendlens.app.ui.screens.compare.CompareContent
import com.spendlens.app.ui.screens.detail.DetailContent
import com.spendlens.app.ui.screens.edit.EditContent
import com.spendlens.app.ui.screens.edit.EditForm
import com.spendlens.app.ui.screens.goals.GoalsActions
import com.spendlens.app.ui.screens.goals.GoalsContent
import com.spendlens.app.ui.screens.goals.GoalsUi
import com.spendlens.app.ui.screens.home.DashboardContent
import com.spendlens.app.ui.screens.home.HomeActions
import com.spendlens.app.ui.screens.home.Welcome
import com.spendlens.app.ui.screens.home.buildHome
import com.spendlens.app.ui.screens.review.ReviewActions
import com.spendlens.app.ui.screens.review.ReviewContent
import com.spendlens.app.ui.screens.settings.SettingsActions
import com.spendlens.app.ui.screens.settings.SettingsContent
import com.spendlens.app.ui.screens.settings.SettingsUi
import com.spendlens.app.ui.screens.wrap.WrapContent
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.theme.Style
import com.spendlens.app.ui.theme.lookFor
import androidx.compose.ui.graphics.Color
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Renders every screen from the built-in sample data and saves PNGs (build/outputs/roborazzi) so the
 * UI can be reviewed without a device. Animations run on a manual clock to completion first.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun noLoops() {
            MotionSettings.loops = false
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.now()
    private val today = now.toLocalDate()
    private val txns: List<Txn> = SampleData.generate(today, now = now).mapIndexed { i, s ->
        Txn(i + 1L, s.amountMinor, s.merchant, s.category, s.dateTime, s.app, reference = "SAMPLE$i", source = TxnSource.SAMPLE)
    }.sortedByDescending { it.dateTime }
    private val goals = listOf(
        Goal(1, "Goa trip", 60_000_00, 21_500_00, today.plusMonths(3)),
        Goal(2, "New phone", 45_000_00, 45_000_00, null),
    )

    private fun shoot(
        name: String,
        dark: Boolean = true,
        glass: Float = 0.55f,
        style: Style = Style.EDITORIAL,
        accent: Color? = null,
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SpendLensTheme(darkTheme = dark, style = style, accent = accent) {
                val g = if (lookFor(style).glassy) glass else 0f
                CompositionLocalProvider(LocalCurrency provides CurrencyOption.INR, LocalGlass provides g) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun home(type: PeriodType, anchor: LocalDate = today) =
        buildHome(txns, Period(type, anchor), now, CurrencyOption.INR, 45_000_00, 90_000_00, goals, emptySet())

    @Test fun welcome() = shoot("01_welcome") { Welcome({}, {}, {}) }

    @Test fun homeMonth() = shoot("02_home_month") { DashboardContent(home(PeriodType.MONTH), HomeActions()) }

    @Config(qualifiers = "w400dp-h6400dp-xxhdpi")
    @Test fun homeMonthFull() = shoot("03_home_month_full") { DashboardContent(home(PeriodType.MONTH), HomeActions()) }

    @Config(qualifiers = "w400dp-h6400dp-xxhdpi")
    @Test fun homeMonthFullFlat() = shoot("03b_home_month_flat", glass = 0f) { DashboardContent(home(PeriodType.MONTH), HomeActions()) }

    @Config(qualifiers = "w400dp-h2600dp-xxhdpi")
    @Test fun homeWeek() = shoot("04_home_week", glass = 1f) { DashboardContent(home(PeriodType.WEEK), HomeActions()) }

    @Config(qualifiers = "w400dp-h2600dp-xxhdpi")
    @Test fun homeYear() = shoot("05_home_year") { DashboardContent(home(PeriodType.YEAR), HomeActions()) }

    @Test fun homeDay() = shoot("06_home_day") { DashboardContent(home(PeriodType.DAY), HomeActions()) }

    @Config(qualifiers = "w400dp-h2600dp-xxhdpi")
    @Test fun homeLight() = shoot("07_home_light", dark = false, glass = 0.8f) { DashboardContent(home(PeriodType.MONTH), HomeActions()) }

    @Test fun activity() = shoot("08_activity") {
        val groups = txns.take(30).groupBy { it.dateTime.toLocalDate() }.map { (d, l) -> DayGroup(d, l.sumOf { it.amountMinor }, l) }
        ActivityContent(ActivityUiState(false, null, groups, Category.entries.take(8), txns.sumOf { it.amountMinor }, txns.size, true), "", {}, {}, {}, {})
    }

    @Test fun review() = shoot("09_review") {
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

    @Test fun reviewSms() = shoot("10_review_sms") {
        val body = "Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 30/09/26\nRef 426512345678\nNot You? Call 18002586161"
        val state = ImportState(
            mode = ImportMode.SMS, phase = ImportPhase.DONE, total = 42, processed = 42, skipped = 38,
            drafts = listOf(
                ImportDraft(sourceUri = "sms:1", state = DraftState.READY, amountText = "450", merchant = "Swiggy", category = Category.FOOD, dateTime = now.minusHours(3), paymentApp = "HDFC Bank · UPI", rawText = body, smsFrom = "VM-HDFCBK"),
                ImportDraft(sourceUri = "sms:2", state = DraftState.READY, amountText = "1249", merchant = "Blinkit", category = Category.GROCERIES, dateTime = now.minusDays(1), paymentApp = "HDFC Bank · Card", rawText = "Spent Rs.1249 On HDFC Bank Card 5678 At BLINKIT On 2026-09-29:19:45:12.", smsFrom = "AD-HDFCBK"),
            ),
        )
        ReviewContent(state, saving = false, actions = ReviewActions())
    }

    @Test fun detailWithAlert() = shoot("11_detail_alert") {
        val anomaly = AnomalyDetector.detect(txns, now, CurrencyOption.INR).first { it.txn.merchant == "Swiggy" }
        DetailContent(anomaly.txn.copy(rawText = "Paid to SWIGGY\n₹486\nCompleted"), {}, {}, {}, anomaly)
    }

    @Test fun edit() = shoot("12_edit") {
        EditContent(EditForm(amountText = "320", merchant = "Chai Point", category = Category.FOOD), isNew = true, canSave = true, onUpdate = {}, onSave = {}, onClose = {})
    }

    @Config(qualifiers = "w400dp-h3400dp-xxhdpi")
    @Test fun settings() = shoot("13_settings") {
        SettingsContent(SettingsUi(AppSettings(monthlyBudgetMinor = 45_000_00, monthlyIncomeMinor = 90_000_00, theme = ThemeMode.DARK, summaryNotification = true), txns, sampleCount = txns.size), SettingsActions())
    }

    @Test fun wrapIntro() = shoot("14_wrap_intro") { WrapContent(WrapBuilder.of(txns, Period(PeriodType.MONTH, today.minusMonths(1)), today), {}, autoAdvance = false) }

    @Test fun wrapPlaces() = shoot("15_wrap_places") { WrapContent(WrapBuilder.of(txns, Period(PeriodType.MONTH, today.minusMonths(1)), today), {}, autoAdvance = false, initialPage = 1) }

    @Test fun wrapHour() = shoot("16_wrap_hour") { WrapContent(WrapBuilder.of(txns, Period(PeriodType.MONTH, today.minusMonths(1)), today), {}, autoAdvance = false, initialPage = 3) }

    @Test fun wrapSummary() = shoot("17_wrap_summary") { WrapContent(WrapBuilder.of(txns, Period(PeriodType.YEAR, today), today), {}, autoAdvance = false, initialPage = 6) }

    @Config(qualifiers = "w400dp-h2200dp-xxhdpi")
    @Test fun compare() = shoot("18_compare_months") {
        val b = Period(PeriodType.MONTH, today)
        CompareContent(Comparer.compare(txns, b.shift(-1), b, today), CompareActions())
    }

    @Config(qualifiers = "w400dp-h1600dp-xxhdpi")
    @Test fun goals() = shoot("19_goals") {
        val month = Period(PeriodType.MONTH, today)
        val spent = txns.filter { it.dateTime in month }.sumOf { it.amountMinor }
        GoalsContent(GoalsUi(goals.map { GoalPlanner.plan(it, 90_000_00, spent, spent + 10_000_00, today) }, 90_000_00, spent, spent + 10_000_00), GoalsActions())
    }

    // ---- Every look on the home screen, then other screens in other looks.

    private fun homeIn(name: String, style: Style, dark: Boolean, accent: Color? = null) =
        shoot(name, dark = dark, style = style, accent = accent) { DashboardContent(home(PeriodType.MONTH), HomeActions()) }

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeReceipt() = homeIn("20_theme_receipt", Style.RECEIPT, dark = false)

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeBento() = homeIn("21_theme_bento", Style.BENTO, dark = true)

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeSwiss() = homeIn("22_theme_swiss", Style.SWISS, dark = false)

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeDot() = homeIn("23_theme_dot", Style.DOT, dark = true)

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeCalendar() = homeIn("24_theme_calendar", Style.CALENDAR, dark = false)

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeBrutal() = homeIn("25_theme_brutal", Style.BRUTAL, dark = false)

    @Config(qualifiers = "w400dp-h3600dp-xxhdpi")
    @Test fun themeWallet() = homeIn("26_theme_wallet", Style.WALLET, dark = true)

    @Config(qualifiers = "w400dp-h2600dp-xxhdpi")
    @Test fun themeEditorialAccent() = homeIn("27_theme_editorial_accent", Style.EDITORIAL, dark = true, accent = Color(0xFFFF7A1A))

    @Test fun themeBentoLight() = homeIn("28_theme_bento_light", Style.BENTO, dark = false, accent = Color(0xFF2B3BE8))

    @Config(qualifiers = "w400dp-h2000dp-xxhdpi")
    @Test fun settingsThemes() = shoot("29_settings_themes", style = Style.BENTO) {
        SettingsContent(SettingsUi(AppSettings(monthlyBudgetMinor = 45_000_00, style = "bento", theme = ThemeMode.DARK), txns, sampleCount = 0), SettingsActions())
    }

    @Test fun activityBrutal() = shoot("30_activity_brutal", dark = false, style = Style.BRUTAL) {
        val groups = txns.take(30).groupBy { it.dateTime.toLocalDate() }.map { (d, l) -> DayGroup(d, l.sumOf { it.amountMinor }, l) }
        ActivityContent(ActivityUiState(false, null, groups, Category.entries.take(8), txns.sumOf { it.amountMinor }, txns.size, true), "", {}, {}, {}, {})
    }

    @Test fun detailSwiss() = shoot("31_detail_swiss", dark = false, style = Style.SWISS) {
        val anomaly = AnomalyDetector.detect(txns, now, CurrencyOption.INR).first { it.txn.merchant == "Swiggy" }
        DetailContent(anomaly.txn, {}, {}, {}, anomaly)
    }

    @Config(qualifiers = "w400dp-h2200dp-xxhdpi")
    @Test fun compareWallet() = shoot("32_compare_wallet_view", style = Style.WALLET) {
        val b = Period(PeriodType.MONTH, today)
        CompareContent(Comparer.compare(txns, b.shift(-1), b, today), CompareActions())
    }

    @Config(qualifiers = "w400dp-h1600dp-xxhdpi")
    @Test fun goalsCalendar() = shoot("33_goals_calendar", dark = false, style = Style.CALENDAR) {
        val month = Period(PeriodType.MONTH, today)
        val spent = txns.filter { it.dateTime in month }.sumOf { it.amountMinor }
        GoalsContent(GoalsUi(goals.map { GoalPlanner.plan(it, 90_000_00, spent, spent + 10_000_00, today) }, 90_000_00, spent, spent + 10_000_00), GoalsActions())
    }

    @Test fun editReceipt() = shoot("34_edit_receipt", dark = false, style = Style.RECEIPT) {
        EditContent(EditForm(amountText = "320", merchant = "Chai Point", category = Category.FOOD), isNew = true, canSave = true, onUpdate = {}, onSave = {}, onClose = {})
    }

    @Test fun wrapDot() = shoot("35_wrap_dot", style = Style.DOT) {
        WrapContent(WrapBuilder.of(txns, Period(PeriodType.MONTH, today.minusMonths(1)), today), {}, autoAdvance = false)
    }

    @Test fun homeWeekWallet() = shoot("36_home_week_wallet_light", dark = false, style = Style.WALLET) { DashboardContent(home(PeriodType.WEEK), HomeActions()) }
}
