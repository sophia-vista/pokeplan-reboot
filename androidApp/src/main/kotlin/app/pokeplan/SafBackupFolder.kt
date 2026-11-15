package app.pokeplan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.pokeplan.data.BackupFolder

/**
 * A folder the player picked once with the phone's folder picker (`OpenDocumentTree`; Android's Storage Access Framework), seen as a [BackupFolder]: its files by
 * name, no subfolders. Typically a folder a cloud app syncs. The app keeps the permission to it (`takePersistableUriPermission`) until the player stops or changes it.
 * Not yet exercised on a device.
 */
class SafBackupFolder(private val context: Context, private val tree: Uri) : BackupFolder {
    private val resolver = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /** (display name, document id) of every file in the folder. */
    private fun children(): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val out = mutableListOf<Pair<String, String>>()
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out += c.getString(1) to c.getString(0)
        }
        return out
    }

    override fun names(): List<String> = children().map { it.first }

    override fun write(name: String, text: String): Boolean {
        val created = DocumentsContract.createDocument(resolver, documentUri(rootId), "application/json", name) ?: return false
        val out = resolver.openOutputStream(created, "wt") ?: return false
        out.use { it.write(text.encodeToByteArray()) }
        return true
    }

    override fun read(name: String): String? {
        val id = children().firstOrNull { it.first == name }?.second ?: return null
        return resolver.openInputStream(documentUri(id))?.use { it.readBytes().decodeToString() }
    }

    override fun delete(name: String) {
        children().filter { it.first == name }.forEach { runCatching { DocumentsContract.deleteDocument(resolver, documentUri(it.second)) } }
    }

    companion object {
        /** The folder's name as the player knows it, for the Settings card; null if it cannot be read. */
        fun nameOf(context: Context, tree: Uri): String? = runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }
}
