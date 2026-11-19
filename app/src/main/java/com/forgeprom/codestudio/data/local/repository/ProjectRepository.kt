package com.forgeprom.codestudio.data.local.repository

import android.content.Context
import com.forgeprom.codestudio.core.ux.PromptSnapshot
import com.forgeprom.codestudio.core.ux.PromptVersionHistoryStore
import com.forgeprom.codestudio.data.local.db.CustomBlueprintDao
import com.forgeprom.codestudio.data.local.db.ProjectDao
import com.forgeprom.codestudio.data.local.db.PromptVersionDao
import com.forgeprom.codestudio.data.local.model.CustomBlueprintEntity
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.local.model.PromptVersionEntity
import com.forgeprom.codestudio.domain.pipeline.PromptEngineeringEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * طبقة المستودع المحلي الشاملة (ProjectRepository)
 * تدير المشاريع، سجل الإصدارات المرتبط بـ CASCADE، والقوالب المخصصة عبر Room.
 */
class ProjectRepository(
    private val projectDao: ProjectDao,
    private val promptVersionDao: PromptVersionDao,
    private val customBlueprintDao: CustomBlueprintDao
) {

    data class SmartMergeSummary(
        val addedCount: Int,
        val updatedCount: Int,
        val skippedOlderCount: Int
    )

    val activeProjects: Flow<List<ProjectEntity>> = projectDao.getActiveProjectsFlow()
    val archivedProjects: Flow<List<ProjectEntity>> = projectDao.getArchivedProjectsFlow()
    val customBlueprints: Flow<List<CustomBlueprintEntity>> = customBlueprintDao.observeCustomBlueprints()

    val promptVersionsByProject: Flow<Map<String, List<PromptSnapshot>>> =
        promptVersionDao.observeAllVersions().map { entities ->
            entities.groupBy { it.projectId }.mapValues { (_, list) ->
                list.map { e ->
                    PromptSnapshot(
                        id = e.id,
                        projectId = e.projectId,
                        promptText = e.promptText,
                        reason = e.reason,
                        qualityScore = e.qualityScore,
                        timestampMs = e.timestampMs
                    )
                }
            }
        }

    suspend fun getById(id: String): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun getAllOnce(): List<ProjectEntity> = projectDao.getAllProjectsOnce()

    suspend fun getAllVersionsOnce(): List<PromptVersionEntity> = promptVersionDao.getAllVersionsOnce()

    suspend fun getAllCustomBlueprintsOnce(): List<CustomBlueprintEntity> = customBlueprintDao.getAllBlueprintsOnce()

    suspend fun getProjectCount(): Int = projectDao.getProjectCount()

    suspend fun getVersionCount(): Int = promptVersionDao.getVersionCount()

    suspend fun getCustomBlueprintCount(): Int = customBlueprintDao.getBlueprintCount()

    suspend fun saveProject(project: ProjectEntity) {
        projectDao.insertOrUpdate(project)
    }

    suspend fun saveAll(projects: List<ProjectEntity>) {
        if (projects.isNotEmpty()) {
            projectDao.insertOrUpdateAll(projects)
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        projectDao.setArchived(id, archived)
    }

    suspend fun setPinnedBatch(ids: List<String>, pinned: Boolean) {
        if (ids.isNotEmpty()) {
            projectDao.setPinnedBatch(ids, pinned)
        }
    }

    suspend fun setArchivedBatch(ids: List<String>, archived: Boolean) {
        if (ids.isNotEmpty()) {
            projectDao.setArchivedBatch(ids, archived)
        }
    }

    suspend fun deleteById(id: String) {
        projectDao.deleteById(id)
    }

    suspend fun deleteBatch(ids: List<String>) {
        if (ids.isNotEmpty()) {
            projectDao.deleteByIds(ids)
        }
    }

    /**
     * تسجيل لقطة إصدار برومبت جديدة في جدول Room مع منع التكرار وتقليم السجلات الأقدم من 20
     */
    suspend fun recordPromptVersion(
        projectId: String,
        previousText: String?,
        reason: String
    ) {
        val cleaned = previousText?.trim().orEmpty()
        if (cleaned.isBlank()) return
        val existing = promptVersionDao.getVersionsForProject(projectId)
        if (existing.firstOrNull()?.promptText == cleaned) return

        val score = PromptEngineeringEngine.evaluatePromptQuality(cleaned).totalScore
        promptVersionDao.insertVersion(
            PromptVersionEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                promptText = cleaned,
                reason = reason,
                qualityScore = score,
                timestampMs = System.currentTimeMillis()
            )
        )
        promptVersionDao.pruneOldVersions(projectId, keepLimit = 20)
    }

    /**
     * ترحيل تلقائي لمرة واحدة لأي إصدارات قديمة محفوظة في SharedPreferences إلى جدول Room
     */
    suspend fun migrateLegacyHistoryIfNeeded(context: Context) {
        val allProjects = projectDao.getAllProjectsOnce()
        for (proj in allProjects) {
            val legacy = PromptVersionHistoryStore.getSnapshots(context, proj.id)
            if (legacy.isNotEmpty()) {
                val entities = legacy.map { snap ->
                    PromptVersionEntity(
                        id = snap.id,
                        projectId = proj.id,
                        promptText = snap.promptText,
                        reason = snap.reason,
                        qualityScore = snap.qualityScore,
                        timestampMs = snap.timestampMs
                    )
                }
                promptVersionDao.insertVersions(entities)
                PromptVersionHistoryStore.clearHistory(context, proj.id)
            }
        }
    }

    suspend fun saveCustomBlueprint(blueprint: CustomBlueprintEntity) {
        customBlueprintDao.insertBlueprint(blueprint)
    }

    suspend fun deleteCustomBlueprint(id: String) {
        customBlueprintDao.deleteById(id)
    }

    suspend fun smartMergeRestoredProjects(restored: List<ProjectEntity>): SmartMergeSummary {
        val existingById = projectDao.getAllProjectsOnce().associateBy { it.id }
        val toPersist = mutableListOf<ProjectEntity>()
        var added = 0
        var updated = 0
        var skipped = 0

        for (candidate in restored) {
            val current = existingById[candidate.id]
            when {
                current == null -> {
                    toPersist.add(candidate)
                    added++
                }
                candidate.updatedAt >= current.updatedAt ||
                    (current.currentPromptText.isNullOrBlank() && !candidate.currentPromptText.isNullOrBlank()) -> {
                    toPersist.add(candidate)
                    updated++
                }
                else -> {
                    skipped++
                }
            }
        }

        if (toPersist.isNotEmpty()) {
            projectDao.insertOrUpdateAll(toPersist)
        }

        return SmartMergeSummary(
            addedCount = added,
            updatedCount = updated,
            skippedOlderCount = skipped
        )
    }

    data class EcosystemMergeSummary(
        val projectMerge: SmartMergeSummary,
        val restoredVersionsCount: Int,
        val restoredBlueprintsCount: Int
    )

    suspend fun smartMergeRestoredEcosystem(
        restoredProjects: List<ProjectEntity>,
        restoredVersions: List<PromptVersionEntity>,
        restoredBlueprints: List<CustomBlueprintEntity>
    ): EcosystemMergeSummary {
        val projMerge = smartMergeRestoredProjects(restoredProjects)
        val validProjectIds = projectDao.getAllProjectsOnce().map { it.id }.toSet()
        val validVersions = restoredVersions.filter { it.projectId in validProjectIds }
        if (validVersions.isNotEmpty()) {
            promptVersionDao.insertVersions(validVersions)
        }
        if (restoredBlueprints.isNotEmpty()) {
            customBlueprintDao.insertBlueprints(restoredBlueprints)
        }
        return EcosystemMergeSummary(
            projectMerge = projMerge,
            restoredVersionsCount = validVersions.size,
            restoredBlueprintsCount = restoredBlueprints.size
        )
    }

    suspend fun seedInitialProjectsIfEmpty() {
        val existing = projectDao.getAllProjectsOnce()
        if (existing.isEmpty()) {
            projectDao.insertOrUpdateAll(
                listOf(
                    ProjectEntity(
                        id = "forgeprom-seed-1",
                        name = "نظام نقاط البيع المحلي المتكامل (ForgePOS)",
                        description = "تطبيق POS مخصص لإدارة المبيعات والمخزون وطباعة الفواتير عبر البلوتوث محلياً 100% دون إنترنت مع مزامنة سحابية اختيارية.",
                        type = "NEW_APP",
                        targetPlatform = "ANDROID_KOTLIN",
                        rawIdea = "تطبيق أندرويد متكامل لنقاط البيع السريعة بدون إنترنت",
                        currentPromptText = "# البرومبت الهندسي المعتمد لـ ForgePOS\n- المعمارية: Clean Architecture + MVVM + Room DB\n- الواجهة: Jetpack Compose RTL مع دعم الوضع الداكن\n- الأمان: تشفير الفواتير والمفاتيح عبر Android KeyStore AES-256-GCM وفق معايير OWASP\n- الحالات الحدية: العمل دون إنترنت 100% ومنع استنزاف الذاكرة RAM (OOM)\n- معايير القبول: اختبار العمليات الحسابية وتصدير التقارير PDF.",
                        isPinned = true
                    ),
                    ProjectEntity(
                        id = "forgeprom-seed-2",
                        name = "نظام إدارة العيادات الطبية الخاصة (ForgeClinic)",
                        description = "منصة إدارة ملفات المرضى والمواعيد والوصفات الطبية مع تشفير محلي صارم وحماية بيانات الخصوصية وفق HIPAA.",
                        type = "NEW_APP",
                        targetPlatform = "FLUTTER",
                        rawIdea = "إدارة ملفات المرضى وتاريخ الزيارات الطبية محلياً على التابلت",
                        currentPromptText = "# البرومبت الهندسي المعتمد لـ ForgeClinic\n- المعمارية: Clean Architecture + SQLite Room\n- الأمان: تشفير AES-256-GCM محلي وفق OWASP MASVS\n- الميزات: سجل زيارات مرضى، عمل دون إنترنت، وتصدير تقارير طبية PDF باللغة العربية (RTL).",
                        isPinned = false
                    )
                )
            )
        }
    }
}
