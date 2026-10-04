package com.urbanlens.core

import com.urbanlens.core.alerts.AlertKind
import com.urbanlens.core.alerts.AlertState
import com.urbanlens.core.alerts.AqiAlertPolicy
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.reports.CommunityReport
import com.urbanlens.core.reports.ReportPolicy
import com.urbanlens.core.reports.ReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliciesTest {
    private val hour = 3_600_000L
    private val t0 = 1_800_000_000_000L

    @Test
    fun reportsExpireAfterTheirTtl() {
        val report = CommunityReport(1, ReportType.BLOCKED_ROAD, LatLng(13.0, 80.0), "", null, t0)
        assertTrue(ReportPolicy.isActive(report, t0 + 23 * hour))
        assertFalse(ReportPolicy.isActive(report, t0 + 25 * hour))
    }

    @Test
    fun confirmationsRestartAndStretchTheClock() {
        val report = CommunityReport(1, ReportType.SMOKE, LatLng(13.0, 80.0), "", null, t0)
        val confirmed = ReportPolicy.confirmed(report, t0 + 5 * hour)
        assertEquals(1, confirmed.confirmations)
        // 6h TTL x 1.5 from the confirmation time.
        assertEquals(t0 + 5 * hour + 9 * hour, ReportPolicy.expiresAtMillis(confirmed))
        assertEquals("Confirmed once", ReportPolicy.confidenceLabel(confirmed))
    }

    @Test
    fun onlyRouteBlockingReportsBecomeObstacles() {
        val blocked = CommunityReport(7, ReportType.BLOCKED_ROAD, LatLng(13.0, 80.0), "", null, t0)
        val smoke = blocked.copy(type = ReportType.SMOKE)
        assertEquals("report:7", ReportPolicy.toObstacle(blocked)!!.id)
        assertNull(ReportPolicy.toObstacle(smoke))
        assertNotNull(ReportPolicy.toHotspot(smoke))
        assertNull(ReportPolicy.toHotspot(blocked))
    }

    @Test
    fun alertFiresOnceWhenCrossingThreshold() {
        val first = AqiAlertPolicy.evaluate(aqi = 160, threshold = 151, state = AlertState(), nowMillis = t0)
        assertEquals(AlertKind.WORSENED, first.notify)
        val second = AqiAlertPolicy.evaluate(aqi = 170, threshold = 151, state = first.state, nowMillis = t0 + hour)
        assertNull(second.notify)
    }

    @Test
    fun alertUsesHysteresisBeforeRecovering() {
        val alerting = AlertState(alerting = true, lastNotifiedAtMillis = t0)
        assertNull(AqiAlertPolicy.evaluate(145, 151, alerting, t0 + hour).notify)
        val recovered = AqiAlertPolicy.evaluate(130, 151, alerting, t0 + hour)
        assertEquals(AlertKind.RECOVERED, recovered.notify)
        assertFalse(recovered.state.alerting)
    }

    @Test
    fun alertCooldownSuppressesFlapping() {
        val justNotified = AlertState(alerting = false, lastNotifiedAtMillis = t0)
        val decision = AqiAlertPolicy.evaluate(160, 151, justNotified, t0 + hour)
        assertNull(decision.notify)
        assertTrue(decision.state.alerting)
    }
}
