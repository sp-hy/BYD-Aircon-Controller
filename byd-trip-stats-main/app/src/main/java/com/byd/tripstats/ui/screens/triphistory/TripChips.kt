package com.byd.tripstats.ui.screens.triphistory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.ui.theme.*

/**
 * Score chip - displays trip score 0-100 with colour feedback.
 * Green >=80, Yellow 60-79, Orange 40-59, Red <40
 */
@Composable
fun ScoreChip(
    score: Int?,
    modifier: Modifier = Modifier
) {
    val scoreColor = when {
        score == null -> MaterialTheme.colorScheme.onSurfaceVariant
        score >= 80   -> RegenGreen
        score >= 60   -> BatteryBlue
        score >= 40   -> AccelerationOrange
        else          -> BydErrorRed
    }
    val grade = when {
        score == null -> "—"
        score >= 80   -> "A"
        score >= 60   -> "B"
        score >= 40   -> "C"
        else          -> "D"
    }

    val bgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f)
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = scoreColor)
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.filter_score), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            if (score != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Start) {
                    Text("$score", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = scoreColor)
                    Spacer(Modifier.width(3.dp))
                    Text("($grade)", style = MaterialTheme.typography.labelSmall, color = scoreColor)
                }
            } else {
                Text("—", style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Compact labelled metric cell used inside TripItem rows.
 */
@Composable
fun TripMetricChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                Icon(icon, null, Modifier.size(14.dp), tint = iconTint)
                Spacer(Modifier.width(3.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
