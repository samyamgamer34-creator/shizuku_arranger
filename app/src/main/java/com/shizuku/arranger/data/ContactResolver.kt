package com.shizuku.arranger.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Maps phone numbers to saved contact names using Android's own number matching. */
class ContactResolver(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Looks up each distinct number once. PhoneLookup matches "+15551234567" against a
     * contact saved as "555-1234567", so no manual normalising is needed. Call off the main thread.
     */
    fun resolve(numbers: Collection<String>): Map<String, String> {
        if (!hasPermission()) return emptyMap()
        val result = HashMap<String, String>()
        for (number in numbers) {
            if (number.isBlank()) continue
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            try {
                context.contentResolver.query(
                    uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { result[number] = it }
                }
            } catch (_: Exception) {
                // A malformed number shouldn't break the whole list.
            }
        }
        return result
    }
}
