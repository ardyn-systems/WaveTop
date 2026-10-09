package com.ardyn.wavetop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String, apk: Boolean = true) = Release(
        tag = tag,
        version = Releases.normalize(tag),
        name = "WaveTop $tag",
        publishedAt = "2026-10-09T00:00:00Z",
        notes = "",
        htmlUrl = "",
        prerelease = false,
        apk = if (apk) ReleaseAsset("WaveTop-${Releases.normalize(tag)}.apk", "u", 1) else null,
        sums = null,
    )

    @Test
    fun `versions compare numerically, not as text`() {
        assertTrue(Releases.compare("1.10.0", "1.9.9") > 0)
        assertTrue(Releases.compare("v2.0.0", "1.99.99") > 0)
        assertEquals(0, Releases.compare("v1.2", "1.2.0"))
        assertTrue(Releases.compare("1.0.0", "1.0.1") < 0)
        assertEquals(0, Releases.compare("1.0.0-debug", "1.0.0"))
    }

    @Test
    fun `releases are classified against the installed version`() {
        assertEquals(ReleaseKind.Newer, Releases.kind(release("v1.1.0"), "1.0.0"))
        assertEquals(ReleaseKind.Current, Releases.kind(release("v1.0.0"), "1.0.0"))
        assertEquals(ReleaseKind.Older, Releases.kind(release("v0.9.0"), "1.0.0"))
    }

    @Test
    fun `sorted puts the newest first`() {
        val sorted = Releases.sorted(listOf(release("v1.2.0"), release("v1.10.0"), release("v1.9.0")))
        assertEquals(listOf("1.10.0", "1.9.0", "1.2.0"), sorted.map { it.version })
    }

    @Test
    fun `picks the WaveTop apk and the checksum file`() {
        val assets = listOf(
            ReleaseAsset("SHA256SUMS.txt", "s", 1),
            ReleaseAsset("WaveTop-1.2.0.apk", "a", 1),
            ReleaseAsset("mapping.txt", "m", 1),
        )
        assertEquals("WaveTop-1.2.0.apk", Releases.pickApk(assets)?.name)
        assertEquals("SHA256SUMS.txt", Releases.pickSums(assets)?.name)
        assertNull(Releases.pickApk(listOf(ReleaseAsset("a.apk", "", 1), ReleaseAsset("b.apk", "", 1))))
    }

    @Test
    fun `reads a sha256sum listing, either mode`() {
        val hash = "a".repeat(64)
        val other = "b".repeat(64)
        val sums = "$other  WaveTop-1.1.0.apk\n$hash *WaveTop-1.2.0.apk\n"
        assertEquals(hash, Releases.expectedHash(sums, "WaveTop-1.2.0.apk"))
        assertEquals(other, Releases.expectedHash(sums, "WaveTop-1.1.0.apk"))
        assertNull(Releases.expectedHash(sums, "WaveTop-9.9.9.apk"))
        assertNull(Releases.expectedHash("nothex  WaveTop-1.2.0.apk", "WaveTop-1.2.0.apk"))
    }
}
