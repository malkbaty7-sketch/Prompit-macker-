package com.forgeprom.codestudio.data.local.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: ProjectEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(projects: List<ProjectEntity>)

    @Query("UPDATE projects SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE projects SET isPinned = :pinned, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setPinnedBatch(ids: List<String>, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    @Query("UPDATE projects SET isArchived = :archived, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setArchivedBatch(ids: List<String>, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    @Query("DELETE FROM projects WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Database(
    entities = [ProjectEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * ترحيل آمن من الإصدار 1 إلى 2 يضيف الفهارس دون فقدان أي مشروع محفوظ
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_projects_listing` ON `projects` (`isArchived`, `isPinned`, `updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_projects_platform` ON `projects` (`targetPlatform`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "forgeprom_local_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
