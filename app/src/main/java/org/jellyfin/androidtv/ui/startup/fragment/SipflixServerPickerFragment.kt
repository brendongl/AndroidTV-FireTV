package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.ConnectingState
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.koin.androidx.viewmodel.ext.android.activityViewModel

data class SipflixServer(val name: String, val cdnUrl: String, val directUrl: String)

private val SIPFLIX_SERVERS = listOf(
    SipflixServer("iDuck", "https://34335.brr.savethecdn.com/", "http://95.216.4.149:42418/"),
    SipflixServer("Alpha", "https://emby.alphacdn.sipflix.net/", "https://emby.alpha.sipflix.net/"),
)

class SipflixServerPickerFragment : Fragment() {
    private val startupViewModel: StartupViewModel by activityViewModel()

    private val connectingState = mutableStateOf(false)
    private val selectedServer = mutableStateOf<SipflixServer?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setContent {
            SipflixServerPickerScreen(
                connecting = connectingState.value,
                selected = selectedServer.value,
                onServerSelected = { server -> connectToServer(server) },
            )
        }
    }

    private fun connectToServer(server: SipflixServer) {
        if (connectingState.value) return
        connectingState.value = true
        selectedServer.value = server

        startupViewModel.addServer(server.cdnUrl).onEach { state ->
            when (state) {
                is ConnectedState -> {
                    parentFragmentManager.commit {
                        replace<UserLoginFragment>(
                            R.id.content_view,
                            null,
                            bundleOf(
                                UserLoginFragment.ARG_SERVER_ID to state.id.toString(),
                                UserLoginFragment.ARG_SKIP_QUICKCONNECT to true,
                            )
                        )
                        replace<StartupToolbarFragment>(R.id.toolbar_view)
                        addToBackStack(null)
                    }
                }
                is ConnectingState -> Unit
                is UnableToConnectState -> {
                    connectingState.value = false
                    selectedServer.value = null
                    Toast.makeText(
                        requireContext(),
                        "Could not connect to ${server.name}. Try another server.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                else -> {
                    connectingState.value = false
                    selectedServer.value = null
                }
            }
        }.launchIn(lifecycleScope)
    }
}

@Composable
private fun SipflixServerPickerScreen(
    connecting: Boolean,
    selected: SipflixServer?,
    onServerSelected: (SipflixServer) -> Unit,
) {
    val firstButtonFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstButtonFocus.requestFocus()
    }

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
                text = "SIPFLIX",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B),
                letterSpacing = 4.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Select a server to continue",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(40.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SIPFLIX_SERVERS.forEachIndexed { index, server ->
                    ServerButton(
                        server = server,
                        isConnecting = connecting && selected == server,
                        enabled = !connecting,
                        onClick = { onServerSelected(server) },
                        focusRequester = if (index == 0) firstButtonFocus else null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun ServerButton(
    server: SipflixServer,
    isConnecting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isConnecting -> Color(0xFFFF6B6B).copy(alpha = 0.2f)
        isFocused -> Color(0xFFFF6B6B).copy(alpha = 0.12f)
        else -> Color(0xFF1A1A2E)
    }
    val borderColor = when {
        isConnecting || isFocused -> Color(0xFFFF6B6B)
        else -> Color.White.copy(alpha = 0.12f)
    }

    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }

    Box(
        modifier = focusModifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = server.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (isConnecting) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connecting…",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
