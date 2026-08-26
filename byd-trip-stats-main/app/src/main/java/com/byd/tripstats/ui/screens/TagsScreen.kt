package com.byd.tripstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.ui.components.BrandNavigationBar
import com.byd.tripstats.ui.theme.tagColor
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.ui.viewmodel.DashboardViewModel.TagStat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: DashboardViewModel,
    onOpenInHistory: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val tagStats   by viewModel.tagStats.collectAsState()
    val unitSystem by viewModel.unitSystem.collectAsState()

    var renameTarget by remember { mutableStateOf<TagStat?>(null) }
    var deleteTarget by remember { mutableStateOf<TagStat?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.tags_title), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateBack() }
                        )
                        VerticalDivider(
                            modifier = Modifier.height(14.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(R.string.per_tag_efficiency),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                  BrandNavigationBar {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back),
                            modifier = Modifier.size(32.dp))
                    }
                  }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (tagStats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text("🏷️", fontSize = 52.sp)
                    Text(stringResource(R.string.no_tags_yet),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.tag_usage_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tagStats, key = { it.tag.id }) { stat ->
                TagStatCard(
                    stat = stat,
                    unitSystem = unitSystem,
                    onOpenInHistory = { onOpenInHistory(stat.tag.id) },
                    onRename = { renameTarget = stat },
                    onDelete = { deleteTarget = stat }
                )
            }
        }
    }

    renameTarget?.let { target ->
        var name by remember(target.tag.id) { mutableStateOf(target.tag.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.rename_tag_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) viewModel.renameTag(target.tag.id, name.trim())
                        renameTarget = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = { Text(stringResource(R.string.delete_tag_title, target.tag.name)) },
            text = {
                Text(stringResource(R.string.delete_tag_msg, target.tripCount))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTag(target.tag.id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun TagStatCard(
    stat: TagStat,
    unitSystem: UnitSystem,
    onOpenInHistory: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val color = tagColor(stat.tag.colorIndex)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                    Column {
                        Text(stat.tag.name,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${stat.tripCount} trip${if (stat.tripCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.tags_label))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.view_trips_action)) },
                            onClick = { menuOpen = false; onOpenInHistory() },
                            leadingIcon = { Icon(Icons.Filled.FilterList, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = { menuOpen = false; onRename() },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = { menuOpen = false; onDelete() },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TagMetric("🛣️",
                    "${"%.0f".format(unitSystem.convertDistance(stat.totalDistanceKm))} ${unitSystem.distanceUnit}")
                TagMetric("⚡", "${"%.1f".format(stat.totalKwh)} kWh")
                if (stat.avgConsumption > 0.0) {
                    TagMetric("📊",
                        "${"%.1f".format(unitSystem.convertEfficiency(stat.avgConsumption))} ${unitSystem.consumptionUnit}")
                }
            }
        }
    }
}

@Composable
private fun TagMetric(icon: String, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 12.sp)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
