package app.pokeplan

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// DocumentsContract's method and extra names are hidden API constants (present in AOSP but not in the public android.jar);
// a documents provider receives them as strings on the call.
private const val METHOD_CREATE = "android:createDocument"
private const val METHOD_DELETE = "android:deleteDocument"
private const val EXTRA_URI = "uri"
private const val EXTRA_RESULT = "result"

/**
 * The off-phone backup folder through the Storage Access Framework (REP-034). [SafBackupFolder] talks to a documents provider with
 * `DocumentsContract` and `ContentResolver`; this runs it against a fake provider with a tree URI, so the calls it makes (query the
 * children, create, open, delete) are exercised without a phone.
 */
@RunWith(RobolectricTestRunner::class)
class SafBackupFolderTest : AndroidTestBase() {
    private val authority = "app.pokeplan.test.documents"



    /** One flat folder of files behind the Storage Access Framework: the calls [SafBackupFolder] makes, nothing else. */
    class FakeDocumentsProvider : ContentProvider() {
        private lateinit var dir: File
        private val names = linkedMapOf<String, String>() // document id -> display name

        override fun onCreate(): Boolean {
            dir = File(context!!.cacheDir, "saf-documents").also { it.mkdirs() }
            return true
        }

        private fun documentId(uri: Uri): String? = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            // Honour the projection: a caller that asks only for a display name gets only that column.
            val columns = projection?.map { it }?.toTypedArray() ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
            val cursor = MatrixCursor(columns)
            fun add(id: String, name: String) {
                cursor.addRow(Array<Any?>(columns.size) { i ->
                    when (columns[i]) { Document.COLUMN_DOCUMENT_ID -> id; Document.COLUMN_DISPLAY_NAME -> name; else -> null }
                })
            }
            // The folder's own document answers a query for the folder; its children answer the child-listing URI.
            if (uri.pathSegments.size == 4 && documentId(uri) == "root") add("root", "Backups")
            names.forEach { (id, name) -> add(id, name) }
            return cursor
        }

        override fun getType(uri: Uri): String = "application/json"

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            val id = documentId(uri) ?: error("the document URI names no document")
            val flags = if (mode.startsWith("r")) ParcelFileDescriptor.MODE_READ_ONLY
            else ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            return ParcelFileDescriptor.open(File(dir, id), flags)
        }

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
            METHOD_CREATE -> {
                val parent = extras?.getParcelable(EXTRA_URI, Uri::class.java)
                val displayName = extras?.getString(Document.COLUMN_DISPLAY_NAME).orEmpty()
                val id = "doc-${displayName.hashCode()}"
                names[id] = displayName
                Bundle().apply { putParcelable(EXTRA_URI, DocumentsContract.buildDocumentUriUsingTree(parent!!, id)) }
            }
            METHOD_DELETE -> {
                extras?.getParcelable(EXTRA_URI, Uri::class.java)?.let { uri ->
                    documentId(uri)?.let { names.remove(it); File(dir, it).delete() }
                }
                Bundle().apply { putBoolean(EXTRA_RESULT, true) }
            }
            else -> null
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    @Test
    fun theFolderWritesListsReadsAndDeletesThroughTheSaf() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, authority)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
        val folder = SafBackupFolder(context, tree)

        assertTrue(folder.names().isEmpty(), "a fresh folder is empty")
        assertTrue(folder.write("backup.json", """{"v":1}"""))
        assertEquals(listOf("backup.json"), folder.names())
        assertEquals("""{"v":1}""", folder.read("backup.json"))
        assertNull(folder.read("missing.json"))

        // Writing the same name again replaces the copy rather than adding a second one.
        assertTrue(folder.write("backup.json", """{"v":2}"""))
        assertEquals(1, folder.names().size)
        assertEquals("""{"v":2}""", folder.read("backup.json"))

        folder.delete("backup.json")
        assertTrue(folder.names().isEmpty(), "the deleted copy is gone")
    }

    @Test
    fun theFoldersNameIsReadFromTheTreeDocument() {
        Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, authority)
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "root")
        assertEquals("Backups", SafBackupFolder.nameOf(context, tree))
    }
}
