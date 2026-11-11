package com.forgeprom.codestudio.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * كيان بيانات المشروع المحلي (Room Database Entity)
 * تخزين محلي كامل 100% بدون أي اعتمادية على شبكة الإنترنت.
 */
@Entity(tableName = "projects")
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
