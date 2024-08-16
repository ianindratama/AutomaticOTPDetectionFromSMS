package com.ianindratama.automaticotpdetectionfromsms

import android.app.Activity
import android.app.PendingIntent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.ianindratama.automaticotpdetectionfromsms.ui.theme.AutomaticOTPDetectionFromSMSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AutomaticOTPDetectionFromSMSTheme {
                MainApp(modifier = Modifier)
            }
        }

//        val appSignatureHelper = AppSignatureHelper(this)
//        println("App hash-code: ${appSignatureHelper.appSignatures}")
    }
}

@Composable
fun MainApp(modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        StatefulPhoneNumber()
        StatefulSMSConsentAPI()
        StatefulSMSRetrieverAPI()
    }
}

@Composable
fun StatefulPhoneNumber(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val userPhoneNumber = viewModel.userPhoneNumber.collectAsState()

    val context = LocalContext.current

    val signInClient = remember {
        Identity.getSignInClient(context)
    }

    var isShowPhoneNumberHintRequest by remember {
        mutableStateOf(true)
    }

    val getPhoneNumberLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val chosenUserPhoneNumber =
                    Identity.getSignInClient(context).getPhoneNumberFromIntent(result.data)
                viewModel.updateUserPhoneNumber(chosenUserPhoneNumber)
            } else {
                Toast.makeText(context, "Dismissed", Toast.LENGTH_SHORT).show()
            }
        })

    PhoneNumberMenu(
        userPhoneNumber = userPhoneNumber.value,
        isShowPhoneNumberHintRequest = isShowPhoneNumberHintRequest,
        modifier = modifier,
        onUserPhoneNumberChange = { newValue -> viewModel.updateUserPhoneNumber(newValue) },
        onShowPhoneNumberHintRequest = {
            val request = GetPhoneNumberHintIntentRequest.builder().build()
            signInClient.getPhoneNumberHintIntent(request)
                .addOnSuccessListener { result: PendingIntent ->
                    try {
                        getPhoneNumberLauncher.launch(
                            IntentSenderRequest.Builder(result).build()
                        )
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "An error has occurred, please try again later",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        context,
                        "An error has occurred, please type in manually",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            isShowPhoneNumberHintRequest = false
        }
    )
}

@Composable
fun StatefulSMSConsentAPI(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val userPhoneNumber by rememberUpdatedState(viewModel.userPhoneNumber.collectAsState())

    val context = LocalContext.current

    var otp by remember { mutableStateOf("") }

    val smsConsentBroadcastReceiver = remember {
        SMSConsentBroadcastReceiver()
    }

    LaunchedEffect(key1 = smsConsentBroadcastReceiver) {
        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)

        ContextCompat.registerReceiver(
            context,
            smsConsentBroadcastReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    val consentResult by smsConsentBroadcastReceiver.consentResult.collectAsState(
        initial = null
    )

    val readOTPReceive = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                // Get SMS message content
                val message = result.data?.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
                // Extract one-time code using regex
                otp = if (message != null) {
                    val otpPattern = """\b(\d{6})\b""".toRegex()
                    val otpExtractedFromMessage = otpPattern.find(message)?.value

                    if (otpExtractedFromMessage == null) {
                        Toast.makeText(
                            context,
                            "Error occurred when retrieving OTP automatically",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    otpExtractedFromMessage ?: ""
                } else ""
            } else {
                // Consent denied. User can type OTC manually.
                Toast.makeText(context, "Please type the OTP manually", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    )

    LaunchedEffect(key1 = consentResult) {
        consentResult?.let { result ->
            when (result) {
                is SMSConsentBroadcastReceiver.ConsentResult.ConsentAccepted -> readOTPReceive.launch(
                    result.permissionToReadSMSIntent
                )

                is SMSConsentBroadcastReceiver.ConsentResult.ConsentDenied -> Toast.makeText(
                    context,
                    result.reason,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    AuthenticationMenu(
        label = "SMS User Consent API",
        otp = otp,
        modifier = modifier,
        onBtnClick = {
            SmsRetriever.getClient(context).startSmsUserConsent(null)
            Toast.makeText(context, "Sending OTP request to ${userPhoneNumber.value}", Toast.LENGTH_SHORT).show()
        },
        onOTPChange = { newValue -> otp = newValue }
    )
}

@Composable
fun StatefulSMSRetrieverAPI(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val userPhoneNumber by rememberUpdatedState(viewModel.userPhoneNumber.collectAsState())

    val context = LocalContext.current

    var otp by remember { mutableStateOf("") }

    val smsRetrieverBroadcastReceiver = remember {
        SMSRetrieverBroadcastReceiver()
    }

    LaunchedEffect(key1 = smsRetrieverBroadcastReceiver) {
        val intentFilter = IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)

        ContextCompat.registerReceiver(
            context,
            smsRetrieverBroadcastReceiver,
            intentFilter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    val otpResult by smsRetrieverBroadcastReceiver.otpResult.collectAsState(
        initial = null
    )

    LaunchedEffect(key1 = otpResult) {
        otpResult?.let { result ->
            when (result) {
                is SMSRetrieverBroadcastReceiver.OTPResult.OTPReceived -> otp = result.otp
                is SMSRetrieverBroadcastReceiver.OTPResult.OTPNotReceived -> Toast.makeText(
                    context,
                    result.error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    AuthenticationMenu(
        label = "SMS Retriever API",
        otp = otp,
        modifier = modifier,
        onBtnClick = {
            SmsRetriever.getClient(context).startSmsRetriever()
            Toast.makeText(context, "Sending OTP request to ${userPhoneNumber.value}", Toast.LENGTH_SHORT).show()
        },
        onOTPChange = { newValue -> otp = newValue }
    )
}

@Composable
fun PhoneNumberMenu(
    userPhoneNumber: String,
    isShowPhoneNumberHintRequest: Boolean,
    modifier: Modifier = Modifier,
    onUserPhoneNumberChange: (String) -> Unit,
    onShowPhoneNumberHintRequest: () -> Unit
) {
    OutlinedTextField(
        label = { Text(text = "Phone Number") },
        value = userPhoneNumber,
        modifier = modifier,
        onValueChange = { newValue ->
            onUserPhoneNumberChange(newValue)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        interactionSource = remember { MutableInteractionSource() }
            .also { interactionSource ->
                if (isShowPhoneNumberHintRequest) {
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect {
                            if (it is PressInteraction.Release) {
                                onShowPhoneNumberHintRequest()
                            }
                        }
                    }
                }
            }
    )
}

@Composable
fun AuthenticationMenu(
    label: String,
    otp: String,
    modifier: Modifier = Modifier,
    onBtnClick: () -> Unit,
    onOTPChange: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(40.dp, 20.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = label
        )
        Button(onClick = onBtnClick) {
            Text(text = "Send")
        }
    }
    OutlinedTextField(
        value = otp,
        onValueChange = { onOTPChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppPreview() {
    AutomaticOTPDetectionFromSMSTheme {
        MainApp()
    }
}