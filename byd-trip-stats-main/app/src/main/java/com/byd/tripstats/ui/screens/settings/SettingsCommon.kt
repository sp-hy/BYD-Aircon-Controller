package com.byd.tripstats.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SectionHeader(
    icon : ImageVector,
    title: String,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.padding(start = 4.dp, bottom = 2.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color)
    }
}

@Composable
internal fun SettingsGroupLabel(
    title: String
) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
    )
}

@Composable
internal fun SettingsDetailRow(
    label: String,
    value: String,
    url: String? = null,
    onClick: (() -> Unit)? = null,
    showClickIndicator: Boolean = false
) {
    val context = LocalContext.current
    val hiddenClickInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(
                when {
                    url != null -> Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                    onClick != null && showClickIndicator -> Modifier.clickable { onClick() }
                    onClick != null -> Modifier.clickable(
                        interactionSource = hiddenClickInteractionSource,
                        indication = null
                    ) { onClick() }
                    else -> Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (url != null || showClickIndicator) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                url != null -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open link",
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(14.dp)
                    )
                }
                showClickIndicator -> {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackupSummaryCard(
    modifier  : Modifier,
    icon      : ImageVector,
    title     : String,
    body      : String,
    statusLine: String,
    statusOk  : Boolean
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier            = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                statusLine,
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color      = if (statusOk) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
        }
    }
}

internal fun formatFriendlyTimestamp(epochMs: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMs))
}
