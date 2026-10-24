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
            val item = clip.getItemAt(0)
            val uri = item.uri
            if (uri != null) {
                val mimeType = contentResolver.getType(uri) ?: clip.description?.let {
                    if (it.hasMimeType("image/*")) "image/png" else null
                }
                if (mimeType != null && mimeType.startsWith("image/")) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            if (bytes.isNotEmpty()) {
                                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                IdroidService.instance?.sendImageClipboard(b64)
                                Toast.makeText(this, "Copied Image to Mac", Toast.LENGTH_SHORT).show()
                                return
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val text = item.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) {
                IdroidService.instance?.sendClipboard(text)
                Toast.makeText(this, "Copied to Mac", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    }
}
