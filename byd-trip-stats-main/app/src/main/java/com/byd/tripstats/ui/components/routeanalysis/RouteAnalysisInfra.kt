package com.byd.tripstats.ui.components.routeanalysis

import androidx.compose.foundation.border
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val cardBorder: Modifier
    @Composable get() = Modifier.border(
        width = 1.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.medium
    )

internal val cardColors: CardColors
    @Composable get() = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

internal val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
internal fun fmt(ts: Long): String = timeFormat.format(Date(ts))

internal const val MODE_SUMMARY_MIN_DURATION_MINUTES = 0.2
