package org.jellyfin.androidtv.ui.startup.fragment

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.auth.model.AuthenticatedState
import org.jellyfin.androidtv.auth.model.AuthenticatingState
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.sipflix.SipflixLoginServer
import org.jellyfin.androidtv.ui.startup.UserLoginViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber

private const val LOGIN_PORT = 8888

class SipflixQrLoginFragment : Fragment() {
    private val userLoginViewModel: UserLoginViewModel by activityViewModel()

    private val loginServer by lazy { SipflixLoginServer(LOGIN_PORT) }
    private val qrBitmap = mutableStateOf<Bitmap?>(null)
    private val loginUrl = mutableStateOf<String?>(null)
    private val statusMessage = mutableStateOf<String?>(null)
    private val isAuthenticating = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            SipflixQrLoginScreen(
                qrBitmap = qrBitmap.value,
                loginUrl = loginUrl.value,
                statusMessage = statusMessage.value,
                isAuthenticating = isAuthenticating.value,
                onManualLogin = { username, password ->
                    isAuthenticating.value = true
                    statusMessage.value = "Signing in…"
                    userLoginViewModel.login(username, password)
                },
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startLoginServer()
        observeLoginState()
    }

    private fun startLoginServer() {
        val ip = getWifiIpAddress(requireContext())
        if (ip == null) {
            statusMessage.value = "Not connected to WiFi. Please connect and try again."
            return
        }

        val url = "http://$ip:$LOGIN_PORT"
        loginUrl.value = url

        loginServer.onCredentials = { username, password ->
            lifecycleScope.launch(Dispatchers.Main) {
                isAuthenticating.value = true
                statusMessage.value = "Signing in…"
                userLoginViewModel.login(username, password)
            }
        }

        try {
            loginServer.start()
            Timber.d("SipflixLoginServer started on $url")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start SipflixLoginServer")
            statusMessage.value = "Failed to start login server: ${e.message}"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = generateQrCode(url, 400)
            withContext(Dispatchers.Main) { qrBitmap.value = bitmap }
        }
    }

    private fun observeLoginState() {
        userLoginViewModel.loginState.onEach { state ->
            when (state) {
                is AuthenticatingState -> {
                    isAuthenticating.value = true
                    statusMessage.value = "Signing in…"
                }
                is AuthenticatedState -> {
                    isAuthenticating.value = false
                    statusMessage.value = null
                }
                else -> {
                    if (isAuthenticating.value) {
                        isAuthenticating.value = false
                        statusMessage.value = "Sign in failed. Please try again."
                    }
                }
            }
        }.launchIn(lifecycleScope)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            loginServer.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping SipflixLoginServer")
        }
    }

    private fun getWifiIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo?.ipAddress ?: 0
        if (ipInt == 0) return null
        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff,
        )
    }

    private fun generateQrCode(content: String, size: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }
}

@Composable
private fun SipflixQrLoginScreen(
    qrBitmap: Bitmap?,
    loginUrl: String?,
    statusMessage: String?,
    isAuthenticating: Boolean,
    onManualLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val passwordFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Left: QR code
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(2.dp, Color(0xFFFF6B6B), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Login QR code",
                                modifier = Modifier
                                    .size(160.dp)
                                    .padding(4.dp),
                            )
                        } else if (statusMessage == null) {
                            Text(
                                text = "Generating…",
                                color = Color.Gray,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    if (loginUrl != null && !isAuthenticating) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = loginUrl,
                            fontSize = 11.sp,
                            color = Color(0xFF4ECDC4),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(180.dp)
                        .background(Color.White.copy(alpha = 0.1f))
                )

                // Right: Manual login
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Sign in manually",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                    )

                    LoginInputField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "Username",
                        enabled = !isAuthenticating,
                        imeAction = ImeAction.Next,
                        onNext = { passwordFocus.requestFocus() },
                    )

                    LoginInputField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password",
                        isPassword = true,
                        enabled = !isAuthenticating,
                        focusRequester = passwordFocus,
                        imeAction = ImeAction.Done,
                        onDone = { if (username.isNotBlank()) onManualLogin(username, password) },
                    )

                    LoginSubmitButton(
                        enabled = !isAuthenticating && username.isNotBlank(),
                        focusRequester = submitFocus,
                        label = if (isAuthenticating) "Signing in…" else "Sign In",
                        onClick = { onManualLogin(username, password) },
                    )
                }
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = statusMessage,
                    fontSize = 13.sp,
                    color = if (isAuthenticating) Color(0xFF4ECDC4) else Color(0xFFFF6B6B),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LoginInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    isPassword: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.15f)

    var fieldModifier = Modifier
        .fillMaxWidth()
        .onFocusChanged { isFocused = it.isFocused }

    if (focusRequester != null) {
        fieldModifier = fieldModifier.focusRequester(focusRequester)
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = fieldModifier,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        cursorBrush = SolidColor(Color(0xFFFF6B6B)),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() },
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A2E))
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.3f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun LoginSubmitButton(
    enabled: Boolean,
    focusRequester: FocusRequester,
    label: String,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        !enabled -> Color(0xFFFF6B6B).copy(alpha = 0.3f)
        isFocused -> Color(0xFFFF6B6B)
        else -> Color(0xFFFF6B6B).copy(alpha = 0.8f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
