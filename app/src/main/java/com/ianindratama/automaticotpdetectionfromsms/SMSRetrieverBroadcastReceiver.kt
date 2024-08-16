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

class SMSRetrieverBroadcastReceiver : BroadcastReceiver() {

    private val resultChannel = Channel<OTPResult>()
    val otpResult = resultChannel.receiveAsFlow().distinctUntilChanged()

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

                        if (permissionToReadSMSIntent == null){
                            // Get SMS message contents
                            val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)

                            // Define the regular expression pattern to match exactly six digits
                            val otpPattern = Regex("\\b\\d{6}\\b")

                            // Find the first match in the text
                            val otp = otpPattern.find(message.toString())?.value

                            if (otp != null) {
                                resultChannel.trySend(OTPResult.OTPReceived(otp))
                            } else {
                                resultChannel.trySend(OTPResult.OTPNotReceived("An error has occurred when extracting OTP from message"))
                            }
                        }

                    }

                    CommonStatusCodes.TIMEOUT -> {
                        resultChannel.trySend(OTPResult.OTPNotReceived("An error has occurred, Timeout"))
                    }
                }
            } else {
                resultChannel.trySend(OTPResult.OTPNotReceived("An error has occurred when reading the message"))
            }
        }
    }

    sealed interface OTPResult {
        data class OTPReceived(val otp: String) : OTPResult
        data class OTPNotReceived(val error: String) : OTPResult
    }
}