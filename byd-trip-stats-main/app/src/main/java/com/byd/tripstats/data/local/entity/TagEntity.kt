package com.byd.tripstats.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reusable, user-defined trip label (e.g. "commute", "motorway", "errand").
 *
 * [colorIndex] is an index into the UI's fixed tag palette, auto-assigned at
 * creation time so the user doesn't have to pick a colour. The data layer only
 * stores the index; the actual Color lives in the theme (see TagColors) and is
 * looked up modulo the palette size, so the two never get out of sync.
 *
 * Names are unique case-insensitively (enforced at the repository layer via a
 * NOCASE lookup before insert, plus a unique index here as a backstop).
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorIndex: Int
)

/**
 * Many-to-many link between a trip and a tag. No Room ForeignKey is declared:
 * the repository cleans up cross-refs explicitly on trip/tag delete (matching
 * how data points / stats / segments are already handled), which keeps the Room
 * schema simple and avoids FK identity-hash mismatches in migrations.
 */
@Entity(
    tableName = "trip_tags",
    primaryKeys = ["tripId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class TripTagCrossRef(
    val tripId: Long,
    val tagId: Long
)

/** Number of colours in the tag palette; the UI palette must have at least this many. */
const val TAG_PALETTE_SIZE = 8
