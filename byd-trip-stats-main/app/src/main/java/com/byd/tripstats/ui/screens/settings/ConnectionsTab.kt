package com.byd.tripstats.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.byd.tripstats.connections.AbrpConnectionManager
import com.byd.tripstats.connections.AbrpConnectionStore
import com.byd.tripstats.connections.MqttConnectionManager
import com.byd.tripstats.connections.MqttConnectionStore
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.RegenGreen
import com.byd.tripstats.ui.theme.ToggleUncheckedTrack
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ConnectionsTab() {
    val context = LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<DashboardViewModel>()
    val selectedCarConfig by viewModel.selectedCarConfig.collectAsState()
    val liveSnapshot by viewModel.vehicleSnapshot.collectAsState()
    val currentTelemetry by viewModel.currentTelemetry.collectAsState()
    val liveTelemetry = remember(liveSnapshot, currentTelemetry, selectedCarConfig) {
        liveSnapshot?.toTelemetry(selectedCarConfig) ?: currentTelemetry
    }
    val abrpManager = remember { AbrpConnectionManager(context) }
    val mqttManager = remember { MqttConnectionManager(context) }
    val screenScope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(AbrpConnectionStore.load(context)) }
    var tokenInput by rememberSaveable { mutableStateOf(settings.userToken) }
    var enabled by rememberSaveable { mutableStateOf(settings.enabled) }
    var intervalInput by rememberSaveable { mutableStateOf(settings.uploadIntervalSeconds.toString()) }
    var lastSavedAt by remember { mutableStateOf(settings.lastUploadAtMs) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var mqttSettings by remember { mutableStateOf(MqttConnectionStore.load(context)) }
    var mqttBrokerInput by rememberSaveable { mutableStateOf(mqttSettings.brokerUrl) }
    var mqttPortInput by rememberSaveable { mutableStateOf(mqttSettings.brokerPort.toString()) }
    var mqttUsernameInput by rememberSaveable { mutableStateOf(mqttSettings.username) }
    var mqttPasswordInput by rememberSaveable { mutableStateOf(mqttSettings.password) }
    var mqttFriendlyNameInput by rememberSaveable { mutableStateOf(mqttSettings.friendlyName) }
    var mqttEnabled by rememberSaveable { mutableStateOf(mqttSettings.enabled) }
    var mqttUseTls by rememberSaveable { mutableStateOf(mqttSettings.useTls) }
    var mqttUseWebSocket by rememberSaveable { mutableStateOf(mqttSettings.useWebSocket) }
    var mqttWsPathInput by rememberSaveable { mutableStateOf(mqttSettings.webSocketPath) }
    var mqttIntervalInput by rememberSaveable { mutableStateOf(mqttSettings.publishIntervalSeconds.toString()) }
    var mqttResult by remember { mutableStateOf<String?>(null) }
    var mqttOk by remember { mutableStateOf(false) }
    var mqttTesting by remember { mutableStateOf(false) }
    var showAbrpToken by rememberSaveable { mutableStateOf(false) }
    var showConnectionDetails by rememberSaveable { mutableStateOf(false) }

    fun reloadAbrpDraft() {
        settings = AbrpConnectionStore.load(context)
        tokenInput = settings.userToken
        enabled = settings.enabled
        intervalInput = settings.uploadIntervalSeconds.toString()
        lastSavedAt = settings.lastUploadAtMs
    }

    fun reloadMqttDraft() {
        mqttSettings = MqttConnectionStore.load(context)
        mqttBrokerInput = mqttSettings.brokerUrl
        mqttPortInput = mqttSettings.brokerPort.toString()
        mqttUsernameInput = mqttSettings.username
        mqttPasswordInput = mqttSettings.password
        mqttFriendlyNameInput = mqttSettings.friendlyName
        mqttEnabled = mqttSettings.enabled
        mqttUseTls = mqttSettings.useTls
        mqttUseWebSocket = mqttSettings.useWebSocket
        mqttWsPathInput = mqttSettings.webSocketPath
        mqttIntervalInput = mqttSettings.publishIntervalSeconds.toString()
    }

    fun reloadConnectionDrafts() {
        reloadAbrpDraft()
        reloadMqttDraft()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(icon = Icons.Filled.Link, title = stringResource(R.string.settings_tab_connections))

        if (!showConnectionDetails) {
            Text(
                stringResource(R.string.connections_overview_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConnectionSummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Route,
                    title = stringResource(R.string.abrp_title_label),
                    body = stringResource(R.string.abrp_card_desc),
                    statusLine = "${if (settings.enabled) stringResource(R.string.status_enabled_label) else stringResource(R.string.status_disabled_label)} • ${settings.lastStatus}"
                )
                ConnectionSummaryCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Cloud,
                    title = stringResource(R.string.mqtt_title_label),
                    body = stringResource(R.string.mqtt_card_desc),
                    statusLine = "${if (mqttSettings.enabled) stringResource(R.string.status_enabled_label) else stringResource(R.string.status_disabled_label)} • ${mqttSettings.lastStatus}"
                )
            }
            Button(
                onClick = {
                    reloadConnectionDrafts()
                    showConnectionDetails = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
            ) {
                Text(stringResource(R.string.open_connections_action))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    reloadConnectionDrafts()
                    showConnectionDetails = false
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.back_to_overview_action))
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.abrp_title_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.abrp_link_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.abrp_instructions_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.enable_abrp_label),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (enabled) stringResource(R.string.abrp_uploads_every, intervalInput)
                                else stringResource(R.string.abrp_disabled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { next ->
                                enabled = next
                                if (!next) {
                                    val interval = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                    settings = settings.copy(
                                        enabled = false,
                                        userToken = tokenInput,
                                        apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                        uploadIntervalSeconds = interval
                                    )
                                    AbrpConnectionStore.save(context, settings)
                                    lastSavedAt = System.currentTimeMillis()
                                }
                            },
                            thumbContent = if (!enabled) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(ToggleUncheckedTrack, CircleShape)
                                    )
                                }
                            } else null,
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = ToggleUncheckedTrack,
                                uncheckedBorderColor = ToggleUncheckedTrack
                            )
                        )
                    }
                    AnimatedVisibility(visible = enabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsGroupLabel(stringResource(R.string.connection_group_label))
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = { tokenInput = it.trim() },
                                label = { Text(stringResource(R.string.abrp_token_label)) },
                                placeholder = { Text(stringResource(R.string.paste_token_hint)) },
                                singleLine = true,
                                visualTransformation = if (showAbrpToken) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showAbrpToken = !showAbrpToken }) {
                                        Icon(
                                            imageVector = if (showAbrpToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = if (showAbrpToken) stringResource(R.string.hide_token_cd) else stringResource(R.string.show_token_cd)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = intervalInput,
                                onValueChange = { intervalInput = it.filter(Char::isDigit).take(3) },
                                label = { Text(stringResource(R.string.upload_interval_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val interval = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                        settings = settings.copy(
                                            enabled = enabled,
                                            userToken = tokenInput,
                                            apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                            uploadIntervalSeconds = interval
                                        )
                                        AbrpConnectionStore.save(context, settings)
                                        lastSavedAt = System.currentTimeMillis()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                                Button(
                                    onClick = {
                                        val telemetry = liveTelemetry
                                        if (telemetry == null) {
                                            testResult = if (viewModel.serviceConnected.value) {
                                                context.getString(R.string.telemetry_not_ready_msg)
                                            } else {
                                                context.getString(R.string.service_not_connected_msg)
                                            }
                                            testOk = false
                                            return@Button
                                        }
                                        val current = AbrpConnectionStore.load(context).copy(
                                            enabled = enabled,
                                            userToken = tokenInput,
                                            apiKey = settings.apiKey.ifBlank { AbrpConnectionStore.DEFAULT_PUBLIC_API_KEY },
                                            uploadIntervalSeconds = intervalInput.toIntOrNull()?.coerceIn(5, 120) ?: 5
                                        )
                                        AbrpConnectionStore.save(context, current)
                                        screenScope.launch(Dispatchers.IO) {
                                            val (ok, status) = abrpManager.testUpload(telemetry, selectedCarConfig, current)
                                            launch(Dispatchers.Main) {
                                                testResult = if (ok) context.getString(R.string.test_upload_success) else status
                                                testOk = ok
                                                settings = AbrpConnectionStore.load(context)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text(stringResource(R.string.test_and_save_action))
                                }
                                OutlinedButton(onClick = {
                                    reloadAbrpDraft()
                                }) {
                                    Text(stringResource(R.string.reload_action))
                                }
                            }
                        }
                    }
                }
                ConnectionStatusCard(
                    title = stringResource(R.string.abrp_status_label),
                    content = {
                        SettingsDetailRow(stringResource(R.string.label_configured), if (settings.userToken.isBlank()) stringResource(R.string.value_no) else stringResource(R.string.value_yes))
                        SettingsDetailRow(stringResource(R.string.label_enabled), if (settings.enabled) stringResource(R.string.value_yes) else stringResource(R.string.value_no))
                        SettingsDetailRow(stringResource(R.string.label_last_upload), if (settings.lastUploadAtMs > 0L) {
                            formatFriendlyTimestamp(settings.lastUploadAtMs)
                        } else {
                            stringResource(R.string.value_na)
                        })
                        SettingsDetailRow(stringResource(R.string.label_last_status), settings.lastStatus)
                        if (!testResult.isNullOrBlank()) {
                            Text(
                                testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testOk) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.mqtt_title_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.mqtt_card_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.enable_mqtt_label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (mqttEnabled) stringResource(R.string.publishes_every, mqttIntervalInput)
                                else stringResource(R.string.mqtt_disabled_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = mqttEnabled,
                            onCheckedChange = { next ->
                                mqttEnabled = next
                                if (!next) {
                                    val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                    val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                    mqttSettings = mqttSettings.copy(
                                        enabled = false,
                                        brokerUrl = mqttBrokerInput,
                                        brokerPort = port,
                                        username = mqttUsernameInput,
                                        password = mqttPasswordInput,
                                        friendlyName = mqttFriendlyNameInput,
                                        publishIntervalSeconds = interval
                                    )
                                    MqttConnectionStore.save(context, mqttSettings)
                                    mqttResult = context.getString(R.string.mqtt_disabled_saved_msg)
                                    mqttOk = true
                                }
                            },
                            thumbContent = if (!mqttEnabled) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(ToggleUncheckedTrack, CircleShape)
                                    )
                                }
                            } else null,
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = ToggleUncheckedTrack,
                                uncheckedBorderColor = ToggleUncheckedTrack
                            )
                        )
                    }
                    AnimatedVisibility(visible = mqttEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SettingsGroupLabel(stringResource(R.string.connection_group_label))
                            OutlinedTextField(
                                value = mqttBrokerInput,
                                onValueChange = { mqttBrokerInput = it.trim() },
                                label = { Text(stringResource(R.string.broker_url_label)) },
                                placeholder = { Text("example.hivemq.cloud") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPortInput,
                                onValueChange = { mqttPortInput = it.filter(Char::isDigit).take(5) },
                                label = { Text(stringResource(R.string.port_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.use_tls_label),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stringResource(R.string.tls_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (mqttPortInput == "8883" && !mqttUseTls) {
                                        Text(
                                            stringResource(R.string.port_8883_warning),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                Switch(
                                    checked = mqttUseTls,
                                    onCheckedChange = { mqttUseTls = it },
                                    thumbContent = if (!mqttUseTls) {
                                        { Box(modifier = Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                                    } else null,
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = ToggleUncheckedTrack,
                                        uncheckedBorderColor = ToggleUncheckedTrack
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.use_websocket_label),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        stringResource(R.string.websocket_desc_text),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = mqttUseWebSocket,
                                    onCheckedChange = { mqttUseWebSocket = it },
                                    thumbContent = if (!mqttUseWebSocket) {
                                        { Box(modifier = Modifier.size(12.dp).background(ToggleUncheckedTrack, CircleShape)) }
                                    } else null,
                                    colors = SwitchDefaults.colors(
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = ToggleUncheckedTrack,
                                        uncheckedBorderColor = ToggleUncheckedTrack
                                    )
                                )
                            }
                            AnimatedVisibility(visible = mqttUseWebSocket, enter = expandVertically(), exit = shrinkVertically()) {
                                OutlinedTextField(
                                    value = mqttWsPathInput,
                                    onValueChange = { mqttWsPathInput = it.trim() },
                                    label = { Text(stringResource(R.string.websocket_path_label)) },
                                    placeholder = { Text("/mqtt") },
                                    singleLine = true,
                                    supportingText = {
                                        val scheme = if (mqttUseTls) "wss" else "ws"
                                        val host = mqttBrokerInput.ifBlank { "broker" }
                                        val portTxt = mqttPortInput.ifBlank { "<port>" }
                                        val path = mqttWsPathInput.ifBlank { "/mqtt" }.let { if (it.startsWith("/")) it else "/$it" }
                                        Text("$scheme://$host:$portTxt$path", style = MaterialTheme.typography.bodySmall)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            OutlinedTextField(
                                value = mqttFriendlyNameInput,
                                onValueChange = { mqttFriendlyNameInput = it.trim() },
                                label = { Text(stringResource(R.string.mqtt_device_name_label)) },
                                placeholder = { Text("my-byd-seal") },
                                singleLine = true,
                                supportingText = {
                                    val preview = mqttFriendlyNameInput.replace("[^a-zA-Z0-9_-]".toRegex(), "_").ifBlank { "<android-id>" }
                                    Text("Topic: byd-trip-stats/$preview/state", style = MaterialTheme.typography.bodySmall)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttUsernameInput,
                                onValueChange = { mqttUsernameInput = it },
                                label = { Text(stringResource(R.string.username_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttPasswordInput,
                                onValueChange = { mqttPasswordInput = it },
                                label = { Text(stringResource(R.string.password_label)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = mqttIntervalInput,
                                onValueChange = { mqttIntervalInput = it.filter(Char::isDigit).take(3) },
                                label = { Text(stringResource(R.string.publish_interval_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text(stringResource(R.string.publish_interval_desc, mqttIntervalInput.ifBlank { "1" }), style = MaterialTheme.typography.bodySmall) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                stringResource(R.string.publish_parked_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                        val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                        mqttSettings = mqttSettings.copy(
                                            enabled = mqttEnabled,
                                            brokerUrl = mqttBrokerInput,
                                            brokerPort = port,
                                            username = mqttUsernameInput,
                                            password = mqttPasswordInput,
                                            friendlyName = mqttFriendlyNameInput,
                                            publishIntervalSeconds = interval,
                                            useTls = mqttUseTls,
                                            useWebSocket = mqttUseWebSocket,
                                            webSocketPath = mqttWsPathInput
                                        )
                                        MqttConnectionStore.save(context, mqttSettings)
                                        mqttResult = context.getString(R.string.mqtt_settings_saved_msg)
                                        mqttOk = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text(stringResource(R.string.save))
                                }
                                Button(
                                    enabled = !mqttTesting,
                                    onClick = {
                                        val telemetry = liveTelemetry
                                        if (telemetry == null) {
                                            mqttResult = context.getString(R.string.no_live_telemetry_yet)
                                            mqttOk = false
                                            return@Button
                                        }
                                        val interval = mqttIntervalInput.toIntOrNull()?.coerceIn(1, 120) ?: 1
                                        val port = mqttPortInput.toIntOrNull()?.coerceIn(1, 65535) ?: 1883
                                        val current = MqttConnectionStore.load(context).copy(
                                            enabled = mqttEnabled,
                                            brokerUrl = mqttBrokerInput,
                                            brokerPort = port,
                                            username = mqttUsernameInput,
                                            password = mqttPasswordInput,
                                            friendlyName = mqttFriendlyNameInput,
                                            publishIntervalSeconds = interval,
                                            useTls = mqttUseTls,
                                            useWebSocket = mqttUseWebSocket,
                                            webSocketPath = mqttWsPathInput
                                        )
                                        MqttConnectionStore.save(context, current)
                                        mqttTesting = true
                                        mqttResult = null
                                        mqttOk = false
                                        screenScope.launch {
                                            try {
                                                val (ok, status) = withContext(Dispatchers.IO) {
                                                    mqttManager.testPublish(telemetry)
                                                }
                                                mqttResult = if (ok) context.getString(R.string.mqtt_test_success) else status
                                                mqttOk = ok
                                                mqttSettings = MqttConnectionStore.load(context)
                                            } catch (e: Exception) {
                                                mqttResult = context.getString(R.string.mqtt_test_failed_msg, e.message)
                                                mqttOk = false
                                            } finally {
                                                mqttTesting = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BydElectricAzure)
                                ) {
                                    Text(if (mqttTesting) stringResource(R.string.running) else stringResource(R.string.test_and_save_action))
                                }
                                OutlinedButton(onClick = {
                                    reloadMqttDraft()
                                }) {
                                    Text(stringResource(R.string.reload_action))
                                }
                            }
                        }
                    }
                }
                ConnectionStatusCard(
                    title = stringResource(R.string.mqtt_status_label),
                    content = {
                        SettingsDetailRow(stringResource(R.string.label_configured), if (mqttSettings.brokerUrl.isBlank()) stringResource(R.string.value_no) else stringResource(R.string.value_yes))
                        SettingsDetailRow(stringResource(R.string.label_enabled), if (mqttSettings.enabled) stringResource(R.string.value_yes) else stringResource(R.string.value_no))
                        SettingsDetailRow(stringResource(R.string.label_last_publish), if (mqttSettings.lastPublishAtMs > 0L) {
                            formatFriendlyTimestamp(mqttSettings.lastPublishAtMs)
                        } else {
                            stringResource(R.string.value_na)
                        })
                        SettingsDetailRow(stringResource(R.string.label_last_status), mqttSettings.lastStatus)
                        if (!mqttResult.isNullOrBlank()) {
                            Text(
                                mqttResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mqttOk) {
                                    RegenGreen
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectionSummaryCard(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    body: String,
    statusLine: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                statusLine,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}
