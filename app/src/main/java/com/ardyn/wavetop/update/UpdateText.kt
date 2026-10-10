package com.ardyn.wavetop.update

/** What to tell the user when Android's installer turns an update down. */
data class InstallFailure(
    val message: String,
    /** Offer "Download from GitHub": the user can still install it by hand from the browser. */
    val offerDownload: Boolean,
)

/**
 * Turns raw installer results and GitHub release bodies into text for the Updates screen.
 * Plain Kotlin so it can be unit tested.
 */
object UpdateText {

    /**
     * @param platformMessage PackageInstaller's EXTRA_STATUS_MESSAGE, e.g.
     *   "INSTALL_FAILED_VERIFICATION_FAILURE: Install not allowed for file:///…"
     * @param blocked the session ended with STATUS_FAILURE_BLOCKED
     */
    fun installFailure(platformMessage: String?, blocked: Boolean): InstallFailure {
        val m = platformMessage.orEmpty()
        return when {
            // Google Play Protect (developer verification) refused an app it doesn't know yet.
            blocked || "VERIFICATION_FAILURE" in m -> InstallFailure(
                "Google Play Protect stopped the install because it doesn't recognise WaveTop's developer yet. " +
                    "Download the APK from GitHub and open it instead; if Android warns you, tap More details → " +
                    "Install anyway. Your surveys are kept.",
                offerDownload = true,
            )
            "UPDATE_INCOMPATIBLE" in m || "signatures do not match" in m.lowercase() || "CONFLICT" in m -> InstallFailure(
                "The WaveTop on this phone was signed differently (a test or older build), so Android won't " +
                    "update it in place. Back up your surveys under Your data, uninstall WaveTop, then install this version.",
                offerDownload = true,
            )
            "INSUFFICIENT_STORAGE" in m || "STORAGE" in m -> InstallFailure(
                "There isn't enough free space on the phone to install the update. Free some up and try again.",
                offerDownload = false,
            )
            m.isBlank() || "ABORTED" in m || "cancel" in m.lowercase() -> InstallFailure(
                "The update was cancelled, so nothing changed.",
                offerDownload = false,
            )
            else -> InstallFailure("The update didn't install: ${m.substringBefore(" for file:")}.", offerDownload = true)
        }
    }

    private val byUserIn = Regex("""\s+by\s+@[\w.-]+\s+in\s+https?://\S+$""")
    private val link = Regex("""\[([^\]]+)]\((https?://[^)]+)\)""")
    private val emphasis = Regex("""(\*\*|__|\*|_|`)(.+?)\1""")

    /**
     * GitHub's release body, readable as plain text: headings lose their #, bullets become •,
     * links keep their words, and the generated boilerplate ("What's Changed", "by @user in
     * <url>", "Full Changelog", first-contribution lines) is dropped.
     */
    fun plainNotes(body: String): String =
        body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { line ->
                val l = line.lowercase()
                "full changelog" in l || "made their first contribution" in l ||
                    l.trimStart('#', ' ') in setOf("what's changed", "new contributors")
            }
            .map { line ->
                var s = line
                if (s.startsWith("#")) s = s.trimStart('#').trim()
                if (s.startsWith("* ") || s.startsWith("- ")) s = "• " + s.drop(2)
                s = s.replace(byUserIn, "")
                s = link.replace(s) { it.groupValues[1] }
                s = emphasis.replace(s) { it.groupValues[2] }
                s
            }
            .joinToString("\n")
}
