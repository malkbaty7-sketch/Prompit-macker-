package com.forgeprom.codestudio.data.local.repository

import com.forgeprom.codestudio.data.local.db.ProjectDao
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * طبقة المستودع المحلي (ProjectRepository)
 * تفصل طبقة الـ ViewModel عن DAO وتنفذ العمليات الجماعية والدمج الذكي بمعاملات Room ذرية.
 */
class ProjectRepository(private val projectDao: ProjectDao) {

    data class SmartMergeSummary(
        val addedCount: Int,
        val updatedCount: Int,
        val skippedOlderCount: Int
    )

    val activeProjects: Flow<List<ProjectEntity>> = projectDao.getActiveProjectsFlow()
    val archivedProjects: Flow<List<ProjectEntity>> = projectDao.getArchivedProjectsFlow()

    suspend fun getById(id: String): ProjectEntity? = projectDao.getProjectById(id)

    suspend fun getAllOnce(): List<ProjectEntity> = projectDao.getAllProjectsOnce()

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
     * دمج ذكي عند استعادة النسخ الاحتياطية: يضيف المشاريع الجديدة ويحدّث الأقدم فقط لحماية تعديلات المستخدم الحديثة
     */
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
