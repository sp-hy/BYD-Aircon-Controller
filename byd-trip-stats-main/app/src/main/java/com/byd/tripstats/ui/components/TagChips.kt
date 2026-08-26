package com.byd.tripstats.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TagEntity
import com.byd.tripstats.ui.theme.tagColor

/** A small coloured pill for a tag, optionally with a trailing remove (×) button. */
@Composable
fun TagChip(
    tag: TagEntity,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val color = tagColor(tag.colorIndex)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (selected) 0.30f else 0.16f))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = color.copy(alpha = if (selected) 0.9f else 0.4f),
                shape = RoundedCornerShape(50)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(tag.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        if (selected && onClick != null) {
            Icon(Icons.Filled.Check, null, tint = color, modifier = Modifier.size(14.dp))
        }
        if (onRemove != null) {
            Icon(
                Icons.Filled.Close, stringResource(R.string.remove_tag_action),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp).clickable(onClick = onRemove)
            )
        }
    }
}

/**
 * Dialog for managing the tags on a single trip: toggle any existing tag on/off
 * and create a new one by typing. [selectedIds] are the tags currently applied.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageTripTagsDialog(
    allTags: List<TagEntity>,
    selectedIds: Set<Long>,
    onToggle: (TagEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.tags_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (allTags.isEmpty()) {
                    Text(stringResource(R.string.no_tags_create_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { tag ->
                            TagChip(
                                tag = tag,
                                selected = tag.id in selectedIds,
                                onClick = { onToggle(tag) }
                            )
                        }
                    }
                }
                NewTagField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    onSubmit = {
                        val name = newTag.trim()
                        if (name.isNotEmpty()) { onCreate(name); newTag = "" }
                    }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } }
    )
}

/**
 * Dialog for the History bulk action: pick one existing tag to apply to the
 * selected trips, or create a new one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApplyTagDialog(
    allTags: List<TagEntity>,
    onPick: (TagEntity) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text(stringResource(R.string.apply_tag_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (allTags.isNotEmpty()) {
                    Text(stringResource(R.string.apply_tag_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { tag ->
                            TagChip(tag = tag, onClick = { onPick(tag) })
                        }
                    }
                }
                NewTagField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    onSubmit = {
                        val name = newTag.trim()
                        if (name.isNotEmpty()) { onCreate(name); newTag = "" }
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun NewTagField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.new_tag_label)) },
        placeholder = { Text(stringResource(R.string.new_tag_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            if (value.isNotBlank()) {
                TextButton(onClick = onSubmit) { Text(stringResource(R.string.add)) }
            }
        }
    )
}
