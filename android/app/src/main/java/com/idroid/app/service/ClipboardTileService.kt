package com.idroid.app.service

import android.content.ClipboardManager
import android.content.Context
import android.service.quicksettings.TileService
import android.widget.Toast

class ClipboardTileService : TileService() {
    override fun onClick() {
        super.onClick()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip

        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) {
                IdroidService.instance?.sendClipboard(text)
                Toast.makeText(this, "Copied to Mac", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    }
}
