package com.example.prefixblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.provider.ContactsContract
import android.net.Uri

class PrefixCallScreeningService : CallScreeningService() {

    private fun isNumberInContacts(number: String): Boolean {

        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(Uri.encode(number))
            .build()

        val cursor = contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )

        cursor?.use {
            return it.moveToFirst()
        }

        return false
    }

    override fun onScreenCall(callDetails: Call.Details) {

        Log.d("PREFIX_BLOCKER", "onScreenCall triggered")

        val rawNumber =
            callDetails.handle.schemeSpecificPart ?: return

        val cleanRaw = rawNumber
            .replace(" ", "")
            .replace("-", "")

        // true se numero internazionale NON italiano
        val isForeignInternational =

            (
                    cleanRaw.startsWith("+") &&
                            !cleanRaw.startsWith("+39")
                    )

                    ||

                    (
                            cleanRaw.startsWith("00") &&
                                    !cleanRaw.startsWith("0039")
                            )

        // legge modalità globale
        val prefs =
            getSharedPreferences("settings", MODE_PRIVATE)

        val blockInternational =
            prefs.getBoolean("block_international", false)

        // BLOCCO TOTALE ESTERO
        if (blockInternational && isForeignInternational) {

            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()

            respondToCall(callDetails, response)

            Log.d("PREFIX_BLOCKER", "INTERNATIONAL CALL BLOCKED")

            return
        }

        // normalizzazione Italia
        val incomingNumber = cleanRaw
            .replace("+39", "")
            .replace("0039", "")

        Log.d("PREFIX_BLOCKER", "Numero: $incomingNumber")

        // BYPASS CONTATTI SALVATI
        val isSavedContact =
            isNumberInContacts(rawNumber) ||
                    isNumberInContacts(incomingNumber)

        Log.d("PREFIX_BLOCKER", "Saved contact: $isSavedContact")

        if (isSavedContact) {

            Log.d("PREFIX_BLOCKER", "CALL ALLOWED (CONTACT)")

            return
        }

        val storage = PrefixStorage(this)

        val prefixes = runBlocking {
            storage.prefixesFlow.first()
        }

        Log.d("PREFIX_BLOCKER", "Prefissi: $prefixes")

        val shouldBlock = prefixes.any {
            incomingNumber.startsWith(it)
        }

        Log.d("PREFIX_BLOCKER", "Should block: $shouldBlock")

        if (shouldBlock) {

            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()

            respondToCall(callDetails, response)

            Log.d("PREFIX_BLOCKER", "CALL BLOCKED")
        }
    }
}