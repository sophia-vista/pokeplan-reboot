package app.pokeplan.platform

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.pokeplan.link.ContactCard
import app.pokeplan.link.ContactImport
import app.pokeplan.ui.ContactSource

class AndroidContacts(private val activity: ComponentActivity) : ContactSource {
    private var pending: ((Boolean) -> Unit)? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.invoke(granted)
        pending = null
    }

    override fun permitted(): Boolean =
        activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun requestPermission(done: (Boolean) -> Unit) {
        if (permitted()) return done(true)
        pending = done
        launcher.launch(Manifest.permission.READ_CONTACTS)
    }

    private class Draft(val key: String, val name: String) {
        val phones = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
        var organization = ""
        var birthday: String? = null
    }

    override fun contacts(): List<ContactCard> {
        if (!permitted()) return emptyList()
        val phone = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
        val email = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
        val org = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
        val event = ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
        val name = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
        val columns = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2,
        )
        val drafts = LinkedHashMap<String, Draft>()
        try {
            activity.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, columns, "${ContactsContract.Data.MIMETYPE} IN (?, ?, ?, ?, ?)", arrayOf(phone, email, org, event, name),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY}, ${ContactsContract.Contacts.LOOKUP_KEY}",
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = c.getString(0)?.trim().orEmpty()
                    val displayName = c.getString(1)?.trim().orEmpty()
                    if (key.isEmpty() || displayName.isEmpty()) continue
                    val d = drafts.getOrPut(key) { Draft(key, displayName) }
                    val value = c.getString(3)?.trim().orEmpty()
                    if (value.isEmpty()) continue
                    when (c.getString(2)) {
                        phone -> if (d.phones.size < 5) d.phones += value
                        email -> if (d.emails.size < 5) d.emails += value
                        org -> if (d.organization.isEmpty()) d.organization = value
                        event -> if (d.birthday == null && c.getInt(4) == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) d.birthday = value
                    }
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return drafts.values.map {
            ContactCard(it.key, it.name, it.phones.toList(), it.emails.toList(), it.organization, it.birthday?.let(ContactImport::parseBirthday))
        }
    }
}
