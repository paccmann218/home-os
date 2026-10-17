package com.homeos.bridge.ui.dev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dev inspector reads `HealthSync.sync`'s human summary rather than a structured result,
 * because that file carries the menstruation reader from a previous session and is left untouched.
 * Parsing a prose format is fragile by nature, so the format is pinned here: if someone changes the
 * summary string, this test is what tells them the inspector went blank.
 *
 * The sample is the real shape, taken from the summary `HealthSync.sync` builds.
 */
class SyncSummaryTest {

    private val real =
        "Read 0 nights, 48 HR samples, 8 resting HR, 2 HRV, 35 step records, 0 period records, " +
            "0 flow logs over 2 d (from com.gabitrn) · 10 permissions\n" +
            "Server: {\"nights\":0,\"newNights\":0}"

    @Test
    fun `pulls per-type counts out of the summary`() {
        val report = parseSyncSummary(real)
        val counts = report.counts.toMap()
        assertEquals("0", counts["nights"])
        assertEquals("48", counts["HR samples"])
        assertEquals("8", counts["resting HR"])
        assertEquals("2", counts["HRV"])
        assertEquals("35", counts["step records"])
        assertEquals("0", counts["period records"])
        assertEquals("0", counts["flow logs"])
        assertEquals(7, report.counts.size)
    }

    @Test
    fun `pulls origins, window, permissions and the server reply`() {
        val report = parseSyncSummary(real)
        assertEquals("com.gabitrn", report.origins)
        assertEquals("2 d", report.window)
        assertEquals("10 permissions", report.permissions)
        assertEquals("{\"nights\":0,\"newNights\":0}", report.server)
        assertNull(report.errors)
    }

    @Test
    fun `surfaces per-record-type read errors`() {
        val withErrors = real + "\nErrors: menstruation: SecurityException: not granted"
        val report = parseSyncSummary(withErrors)
        assertTrue(report.errors!!.contains("menstruation"))
    }

    @Test
    fun `handles a sync with no origins, which is what no data looks like`() {
        val noOrigins = "Read 0 nights, 0 HR samples, 0 resting HR, 0 HRV, 0 step records, " +
            "0 period records, 0 flow logs over 2 d · 10 permissions"
        val report = parseSyncSummary(noOrigins)
        assertNull(report.origins)
        assertEquals("2 d", report.window)
        assertEquals("10 permissions", report.permissions)
        assertEquals(7, report.counts.size)
    }

    @Test
    fun `a failed sync still yields something to show`() {
        val failed = "Sync failed: java.net.ConnectException: Failed to connect"
        val report = parseSyncSummary(failed)
        assertTrue(report.counts.isEmpty())
        assertNull(report.server)
    }

    @Test
    fun `never synced is not an error`() {
        assertTrue(parseSyncSummary("never").counts.isEmpty())
        assertTrue(parseSyncSummary("").counts.isEmpty())
    }
}
