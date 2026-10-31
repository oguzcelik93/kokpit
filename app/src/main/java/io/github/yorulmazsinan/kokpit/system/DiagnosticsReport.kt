package io.github.yorulmazsinan.kokpit.system

data class DiagnosticsReport(
    val sdkInt: Int,
    val release: String,
    val model: String,
    val hardware: String,
    val display: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val wifi5GhzSupported: Boolean?,
    val bluetoothAdapterPresent: Boolean,
    val defaultHomePackage: String?,
    val launchablePackages: List<String>,
    val homePackages: List<String>,
    val systemPackages: List<String>,
)

/** Plain-text form meant to be pasted into a GitHub issue. */
fun DiagnosticsReport.toText(): String = buildString {
    appendLine("Kokpit diagnostics")
    appendLine("sdk=$sdkInt release=$release")
    appendLine("model=$model hardware=$hardware build=$display")
    appendLine("screen=${widthPx}x$heightPx dpi=$densityDpi")
    appendLine("wifi5GHz=${wifi5GhzSupported?.toString() ?: "unknown"} bt=$bluetoothAdapterPresent")
    appendLine("defaultHome=${defaultHomePackage ?: "none"}")
    section("home packages", homePackages)
    section("launchable packages", launchablePackages)
    section("system packages", systemPackages)
}

private fun StringBuilder.section(title: String, packages: List<String>) {
    appendLine()
    appendLine("[$title]")
    packages.forEach { appendLine(it) }
}
