package com.shilapi.xcertplay.network

/**
 * The Wi-Fi key the accessory advertises to the iPhone for a manual hotspot.
 *
 * Manual mode reads the running access point's name, security, band and channel from the platform,
 * and the name is validated: a mismatch aborts the bring-up. The key had no such check, so the
 * accessory could tell the iPhone a key that the access point does not use. iOS then holds an
 * association it made with its own saved key while the accessory hands it different credentials —
 * the phone keeps browsing the accessory's services but never opens the AirPlay connection.
 *
 * The platform only sometimes exposes the access point's key (vendor builds may mask it). When it is
 * usable it wins, because the access point is the source of truth; otherwise the app's own setting
 * is advertised as before. [matchesConfigured] reports whether the two agreed, which is the only
 * thing a diagnostic report needs and carries no secret.
 */
internal data class ManualHotspotCredential(
    val passphrase: String,
    val source: String,
    val matchesConfigured: Boolean?,
)

internal fun resolveManualHotspotCredential(
    configured: String,
    fromAccessPoint: String?,
): ManualHotspotCredential {
    val candidate = fromAccessPoint?.let(::usableAccessPointPassphrase)
        ?: return ManualHotspotCredential(configured, SOURCE_SETTING, null)
    return ManualHotspotCredential(candidate, SOURCE_ACCESS_POINT, candidate == configured)
}

/** A masked or truncated platform value is not a key and must never be advertised. */
private fun usableAccessPointPassphrase(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.all { it == '*' } || trimmed.all { it == '.' }) return null
    return trimmed.takeIf { it.length in MIN_WPA_KEY_CHARS..MAX_WPA_KEY_CHARS }
}

internal const val SOURCE_SETTING = "appSetting"
internal const val SOURCE_ACCESS_POINT = "accessPoint"
private const val MIN_WPA_KEY_CHARS = 8
private const val MAX_WPA_KEY_CHARS = 63
