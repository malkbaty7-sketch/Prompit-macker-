package com.forgeprom.codestudio.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * كيان بيانات المشروع المحلي (Room Database Entity)
 * مزوّد بفهارس SQLite مركّبة لتسريع الفرز والبحث والتصفية محلياً 100%.
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["isArchived", "isPinned", "updatedAt"], name = "idx_projects_listing"),
        Index(value = ["targetPlatform"], name = "idx_projects_platform")
    ]
)
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val type: String, // NEW_APP, NEW_FEATURE, REFACTOR, BUG_FIX
    val targetPlatform: String, // ANDROID_KOTLIN, FLUTTER, REACT_NATIVE, WEB, BACKEND
    val rawIdea: String,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val analysisJson: String? = null,
    val currentPromptText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
