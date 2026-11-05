// FILE: app/src/main/java/com/calnyx/app/moan/WhatsAppHelper.kt
// NEW FILE - "Open in WhatsApp" (Click to Chat).
//
// Normalizes a phone number and opens that number's WhatsApp
// chat via https://wa.me/<number>. Used in two places:
// 1. The Hardcore WhatsApp card: the user types or pastes a
//    number and taps the WhatsApp button.
// 2. WhatsAppOpenerActivity: a number tapped in ANY other app
//    (Messenger, Phone, browser, ...) is picked up and opened
//    in WhatsApp.
//
// Number rules (Bangladesh default): only digits are kept.
// A number starting with 0 (01XXXXXXXXX) becomes
// 8801XXXXXXXXX. A bare 10-digit mobile gets 880 prepended.
// A number that already starts with 880 is kept as-is.
// Anything else is used as-is (it already has a country
// code). Returns null when there are too few digits to be
// a real number.
package com.calnyx.app.moan

import android.content.Context
import android.content.Intent
import android.net.Uri

object WhatsAppHelper {

    fun normalizeNumber(raw: String?): String? {
        if (raw == null) return null
        var digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        if (digits.startsWith("0")) {
            digits = "880" + digits.drop(1)
        } else if (digits.length == 10) {
            digits = "880$digits"
        }
        if (digits.length < 10) return null
        return digits
    }

    // Open the WhatsApp chat for this number. Returns true if
    // the intent was started, false if the number was invalid
    // or nothing could handle it (e.g. WhatsApp not installed
    // and no browser). WhatsApp handles wa.me links itself when
    // installed; otherwise the browser opens the web chat.
    fun openChat(context: Context, raw: String?): Boolean {
        val number = normalizeNumber(raw) ?: return false
        return try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$number")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
