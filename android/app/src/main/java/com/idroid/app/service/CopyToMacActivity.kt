package com.idroid.app.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Trampoline Activity for Android's system text selection context menu.
 * When text is highlighted in ANY app on Android, "Copy to Mac" appears.
 * Tapping it sends the text directly to the Mac and finishes instantly.
 */
class CopyToMacActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

        if (!selectedText.isNullOrBlank()) {
            IdroidService.instance?.sendClipboard(selectedText)
            Toast.makeText(this, "Copied to Mac", Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
