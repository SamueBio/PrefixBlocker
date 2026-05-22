package com.example.prefixblocker

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PrefixCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {

        Log.d("PREFIX_BLOCKER", "onScreenCall triggered")

        val rawNumber =
            callDetails.handle.schemeSpecificPart ?: return

        val incomingNumber = rawNumber
            .replace("+39", "")
            .replace(" ", "")
            .replace("-", "")

        Log.d("PREFIX_BLOCKER", "Numero: $incomingNumber")

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