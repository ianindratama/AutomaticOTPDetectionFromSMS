package com.ianindratama.automaticotpdetectionfromsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow

class SMSConsentBroadcastReceiver : BroadcastReceiver() {

    private val resultChannel = Channel<ConsentResult>()
    val consentResult = resultChannel.receiveAsFlow().distinctUntilChanged()

    override fun onReceive(context: Context, intent: Intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
            val extras = intent.extras
            val status = IntentCompat.getParcelableExtra(
                intent,
                SmsRetriever.EXTRA_STATUS,
                Status::class.java
            )

            if (status != null && extras != null) {
                when (status.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        val permissionToReadSMSIntent = IntentCompat.getParcelableExtra(
                            intent,
                            SmsRetriever.EXTRA_CONSENT_INTENT,
                            Intent::class.java
                        )

                        if (permissionToReadSMSIntent != null) {
                            resultChannel.trySend(
                                ConsentResult.ConsentAccepted(
                                    permissionToReadSMSIntent
                                )
                            )
                        }
                    }

                    CommonStatusCodes.TIMEOUT -> {
                        resultChannel.trySend(ConsentResult.ConsentDenied("Timeout"))
                    }
                }
            } else {
                resultChannel.trySend(ConsentResult.ConsentDenied("An error has occurred when reading the message"))
            }
        }
    }

    sealed interface ConsentResult {
        data class ConsentAccepted(val permissionToReadSMSIntent: Intent) : ConsentResult
        data class ConsentDenied(val reason: String) : ConsentResult
    }
}