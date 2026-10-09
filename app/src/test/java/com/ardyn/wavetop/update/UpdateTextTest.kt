package com.ardyn.wavetop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTextTest {
    @Test
    fun `play protect verification failure is explained and offers the download`() {
        val f = UpdateText.installFailure(
            "INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///data/app/vmdl1588809635.tmp",
            blocked = false,
        )
        assertTrue(f.message.startsWith("Google Play Protect stopped the install"))
        assertFalse("no raw code shown", "INSTALL_FAILED" in f.message)
        assertTrue(f.offerDownload)
    }

    @Test
    fun `blocked status alone counts as play protect`() {
        assertTrue(UpdateText.installFailure(null, blocked = true).message.startsWith("Google Play Protect"))
    }

    @Test
    fun `signature mismatch says to uninstall first`() {
        val f = UpdateText.installFailure("INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match", false)
        assertTrue("uninstall" in f.message)
    }

    @Test
    fun `cancel and storage are plain`() {
        assertEquals("The update was cancelled, so nothing changed.", UpdateText.installFailure(null, false).message)
        assertFalse(UpdateText.installFailure("INSTALL_FAILED_INSUFFICIENT_STORAGE", false).offerDownload)
    }

    @Test
    fun `unknown failures drop the temp file path`() {
        val f = UpdateText.installFailure("INSTALL_FAILED_INVALID_APK: Bad thing for file:///data/app/x.tmp", false)
        assertEquals("The update didn't install: INSTALL_FAILED_INVALID_APK: Bad thing.", f.message)
    }

    @Test
    fun `github generated notes become plain text`() {
        val body = """
            ## What's Changed
            * WaveTop as its own Android app, styled like NetSeer by @ardyn-systems in https://github.com/ardyn-systems/WaveTop/pull/3
            * Clearer **update** errors by @someone-else in https://github.com/ardyn-systems/WaveTop/pull/4

            ## New Contributors
            * @someone-else made their first contribution in https://github.com/ardyn-systems/WaveTop/pull/4

            **Full Changelog**: https://github.com/ardyn-systems/WaveTop/compare/v1.0.0...v1.0.1
        """.trimIndent()
        assertEquals(
            "• WaveTop as its own Android app, styled like NetSeer\n• Clearer update errors",
            UpdateText.plainNotes(body),
        )
    }

    @Test
    fun `hand-written notes keep their words`() {
        assertEquals(
            "Highlights\n• See the user guide",
            UpdateText.plainNotes("### Highlights\n- See the [user guide](https://example.com/guide)"),
        )
    }
}
