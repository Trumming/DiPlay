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
 * [fromAccessPoint] says the platform exposed the access point's own key and it won: the access
 * point is the source of truth. [matchesSetting] is the only thing a diagnostic report needs and
 * carries no secret.
 */
internal data class ManualHotspotCredential(
    val passphrase: String,
    val fromAccessPoint: Boolean,
    val matchesSetting: Boolean?,
)

internal fun resolveManualHotspotCredential(
    configured: String,
    fromAccessPoint: String?,
): ManualHotspotCredential {
    val candidate = fromAccessPoint?.let(::usableAccessPointPassphrase)
        ?: return ManualHotspotCredential(configured, fromAccessPoint = false, matchesSetting = null)
    return ManualHotspotCredential(candidate, fromAccessPoint = true, matchesSetting = candidate == configured)
}

/** A masked or impossible platform value is not a key and must never be advertised. */
private fun usableAccessPointPassphrase(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.all { it == '*' } || trimmed.all { it == '.' }) return null
    return trimmed.takeIf { it.length in MIN_WPA_KEY_CHARS..MAX_WPA_KEY_CHARS }
}

private const val MIN_WPA_KEY_CHARS = 8
private const val MAX_WPA_KEY_CHARS = 63
