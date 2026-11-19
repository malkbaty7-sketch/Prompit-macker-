package com.forgeprom.codestudio.data.local.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * كيان سجل إصدارات البرومبت في قاعدة بيانات Room (PromptVersionEntity)
 * مرتبط بكيان المشروع مع حذف تلقائي متسلسل (CASCADE) لمنع بقاء أي سجلات يتيمة.
 */
@Entity(
    tableName = "prompt_versions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "timestampMs"], name = "idx_prompt_versions_project_time")
    ]
)
data class PromptVersionEntity(
    @PrimaryKey
    val id: String,
    val projectId: String,
    val promptText: String,
    val reason: String,
    val qualityScore: Int,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * كيان القوالب الهندسية المخصصة للمستخدم (CustomBlueprintEntity)
 * يتيح حفظ أي مشروع هندسي كقالب قابل لإعادة الاستخدام محلياً.
 */
@Entity(
    tableName = "custom_blueprints",
    indices = [
        Index(value = ["category", "createdAt"], name = "idx_custom_blueprints_cat_time")
    ]
)
data class CustomBlueprintEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val category: String,
    val targetPlatform: String,
    val description: String,
    val rawIdea: String,
    val prebuiltPrompt: String,
    val createdAt: Long = System.currentTimeMillis()
)
