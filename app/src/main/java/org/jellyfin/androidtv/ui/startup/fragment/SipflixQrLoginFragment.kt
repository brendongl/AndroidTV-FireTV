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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
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
                    // Activity observes and opens MainActivity automatically
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
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Scan to Sign In",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Open the QR code with your phone",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(3.dp, Color(0xFFFF6B6B), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Login QR code",
                        modifier = Modifier
                            .size(200.dp)
                            .padding(8.dp),
                    )
                } else if (statusMessage == null) {
                    Text(
                        text = "Generating…",
                        color = Color.Gray,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (loginUrl != null && !isAuthenticating) {
                Text(
                    text = loginUrl,
                    fontSize = 13.sp,
                    color = Color(0xFF4ECDC4),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Or open this URL on your phone",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                )
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusMessage,
                    fontSize = 14.sp,
                    color = if (isAuthenticating) Color(0xFF4ECDC4) else Color(0xFFFF6B6B),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
