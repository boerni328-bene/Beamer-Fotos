package com.beamerfotos.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract

/**
 * Zugriff auf den USB-Stick über die Ordnerauswahl von Android (Storage Access Framework).
 * Google TV lässt Apps auf USB-Sticks oft nur lesen; über die Freigabe schreibt Android selbst.
 * Version 0.10.1: nur Test, ob Auswahl und Schreiben auf diesem Beamer klappen.
 */
object UsbAccess {
    /** Intent für die Ordnerauswahl, möglichst gleich auf dem Stick geöffnet. */
    fun pickIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val sm = context.getSystemService(StorageManager::class.java)
            val usb = runCatching {
                sm.storageVolumes.firstOrNull { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
            }.getOrNull()
            if (usb != null) return usb.createOpenDocumentTreeIntent()
        }
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    }

    /** Freigabe merken und testweise eine Datei anlegen, beschreiben und wieder löschen. Liefert das Ergebnis als Text. */
    fun test(context: Context, tree: Uri): String {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(tree, flags) }
        return try {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val file = DocumentsContract.createDocument(resolver, parent, "text/plain", "beamer-fotos-test.txt")
                ?: return "Freigabe erteilt, aber Anlegen einer Datei nicht möglich (Stick nur lesbar?)."
            resolver.openOutputStream(file)?.use { it.write("Beamer-Fotos".toByteArray()) }
                ?: return "Datei angelegt, aber Schreiben nicht möglich."
            DocumentsContract.deleteDocument(resolver, file)
            "✓ Schreiben auf den Stick klappt (Ordner: ${tree.lastPathSegment ?: tree})."
        } catch (e: Exception) {
            "Schreiben nicht möglich: ${e.javaClass.simpleName} ${e.message ?: ""}".trim()
        }
    }
}
