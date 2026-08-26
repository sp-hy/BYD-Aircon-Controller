package com.byd.tripstats.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.ui.theme.ToggleUncheckedTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WebCompanionSection(context: Context, scope: CoroutineScope) {
    val preferencesManager = remember { PreferencesManager(context) }
    // DataStore is the authoritative state — the switch just writes here and
    // the LaunchedEffect reacts, so the toggle is always responsive.
    val enabled by preferencesManager.webServerEnabled.collectAsState(initial = true)
    val port    by preferencesManager.webServerPort.collectAsState(
        initial = PreferencesManager.DEFAULT_WEB_SERVER_PORT
    )
    val pin     by preferencesManager.webServerPin.collectAsState(initial = "")
    var portInput   by remember(port) { mutableStateOf(port.toString()) }
    var pinInput    by remember(pin)  { mutableStateOf(pin) }
    var pinVisible   by remember { mutableStateOf(false) }
    var serverUrl    by remember { mutableStateOf<String?>(null) }
    var serverError  by remember { mutableStateOf<String?>(null) }
    val lockedCount  by com.byd.tripstats.server.WebServerManager.lockedOutCount.collectAsState()
    val clipManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Ensure a PIN exists as soon as the section is first composed
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { preferencesManager.getOrCreateWebServerPin() }
    }

    // Start/stop the server whenever enabled, port, or PIN changes.
    // Runs on IO so ServerSocket binding never touches the main thread.
    LaunchedEffect(enabled, port, pin) {
        com.byd.tripstats.server.WebServerManager.stop()
        serverUrl   = null
        serverError = null
        if (enabled && pin.isNotEmpty()) {
            val error = withContext(Dispatchers.IO) {
                com.byd.tripstats.server.WebServerManager.start(context, port, pin)
            }
            if (error == null) {
                serverUrl   = com.byd.tripstats.server.WebServerManager.getUrl(context)
                serverError = null
            } else {
                serverUrl   = null
                serverError = error
            }
        }
    }

    SectionHeader(icon = Icons.Filled.Language, title = stringResource(R.string.web_companion_label))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.web_companion_label),
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (enabled)
                            stringResource(R.string.web_companion_desc)
                        else
                            stringResource(R.string.web_companion_disabled_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked         = enabled,
                    onCheckedChange = { next ->
                        scope.launch { preferencesManager.saveWebServerEnabled(next) }
                    },
                    thumbContent = if (!enabled) {
                        { Box(Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                    } else null,
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor  = Color.White,
                        uncheckedTrackColor  = ToggleUncheckedTrack,
                        uncheckedBorderColor = ToggleUncheckedTrack
                    )
                )
            }

            if (enabled) {
                Text(
                    stringResource(R.string.web_companion_wifi_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Port row — always visible so users can change it even while disabled
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.port_field_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val p = portInput.toIntOrNull()?.coerceIn(1024, 65535) ?: return@Button
                        scope.launch { preferencesManager.saveWebServerPort(p) }
                    },
                    enabled = portInput.toIntOrNull()?.let { it in 1024..65535 } == true &&
                              portInput.toIntOrNull() != port
                ) { Text(stringResource(R.string.apply)) }
            }
            Text(
                stringResource(R.string.port_hint_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── PIN ──────────────────────────────────────────────────────────
            Text(
                stringResource(R.string.web_access_pin_label),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.web_pin_browser_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val strHidePin = stringResource(R.string.web_pin_hide_cd)
            val strShowPin = stringResource(R.string.web_pin_show_cd)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter(Char::isDigit).take(10) },
                    label = { Text(stringResource(R.string.web_pin_field_label)) },
                    singleLine = true,
                    visualTransformation = if (pinVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Icon(
                                if (pinVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (pinVisible) strHidePin else strShowPin
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                // Apply custom PIN
                Button(
                    onClick = {
                        if (pinInput.length >= 4) scope.launch {
                            preferencesManager.saveWebServerPin(pinInput)
                        }
                    },
                    enabled = pinInput.length >= 4 && pinInput != pin
                ) { Text(stringResource(R.string.web_pin_set)) }
                // Generate a new random PIN
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val newPin = (100_000..999_999).random().toString()
                            pinInput = newPin
                            preferencesManager.saveWebServerPin(newPin)
                        }
                    }
                ) { Text(stringResource(R.string.web_pin_regen)) }
            }
            Text(
                stringResource(R.string.web_pin_min_digits),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))

            // ── Lockout banner ───────────────────────────────────────────────
            if (lockedCount > 0) {
                val strLocked = if (lockedCount == 1)
                    stringResource(R.string.web_ip_locked_one)
                else
                    stringResource(R.string.web_ip_locked_many, lockedCount)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strLocked,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.web_too_many_attempts),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    OutlinedButton(
                        onClick = { com.byd.tripstats.server.WebServerManager.clearLockouts() },
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text(stringResource(R.string.clear_lockout_action)) }
                }
            }

            if (enabled) {
                if (serverError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            stringResource(R.string.web_server_failed, serverError ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    val strWaiting = stringResource(R.string.web_server_waiting)
                    val url = serverUrl ?: strWaiting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            url,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier   = Modifier.weight(1f)
                        )
                        if (serverUrl != null) {
                            IconButton(
                                onClick  = { clipManager.setText(AnnotatedString(url)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ContentCopy,
                                    contentDescription = stringResource(R.string.web_copy_url_cd),
                                    modifier = Modifier.size(18.dp),
                                    tint     = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.web_server_open_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
