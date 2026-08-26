package com.byd.tripstats.ui.screens.batterydegrad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BatteryBlue
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.RegenGreen

@Composable
internal fun DegradationStatCard(
    modifier : Modifier,
    label    : String,
    value    : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    color    : Color
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier            = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun HealthBand(range: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(range, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun sohColor(soh: Double): Color = when {
    soh >= 95 -> RegenGreen
    soh >= 90 -> BatteryBlue
    soh >= 80 -> AccelerationOrange
    else      -> MaterialTheme.colorScheme.error
}

// ProBadge is a private local copy — there is already one in settings.ProCards;
// this one stays private as it is only used within this file (BatteryDegradationScreen).
@Composable
internal fun ProBadge() {
    Box(
        modifier = Modifier
            .background(
                BydElectricAzure,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "PRO",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
