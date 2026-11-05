// FILE: app/src/main/java/com/calnyx/app/moan/WhatsAppOpenerActivity.kt
// NEW FILE - picks up a phone number tapped in ANY other app
// (Messenger, Phone, browser, ...) and opens that number's
// WhatsApp chat.
//
// Declared in the manifest with intent filters for tel:
// (VIEW + DIAL), so Android offers CalNyx in the chooser when
// a number is tapped anywhere. This activity has no UI: it
// extracts the number, opens WhatsApp through WhatsAppHelper,
// and finishes. If the number is missing or invalid, it just
// finishes silently.
//
// Note: tapping a number will now show a chooser (Phone vs
// CalNyx). Pick CalNyx when you want WhatsApp, Phone when you
// want to call. That is how the reference "Open in WhatsApp"
// app works too.
package com.calnyx.app.moan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity

class WhatsAppOpenerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val number = extractNumber(intent)
        if (number != null) {
            WhatsAppHelper.openChat(this, number)
        }
        finish()
    }

    // tel:01712345678 -> "01712345678". A wa.me link, if one
    // ever reaches here, gives its last path segment.
    private fun extractNumber(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        return when (data.scheme) {
            "tel" -> data.schemeSpecificPart
            "http", "https" -> data.lastPathSegment
            else -> null
        }
    }
}
