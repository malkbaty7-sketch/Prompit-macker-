package com.forgeprom.codestudio.data.local.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.forgeprom.codestudio.data.local.model.CustomBlueprintEntity
import com.forgeprom.codestudio.data.local.model.ProjectEntity
import com.forgeprom.codestudio.data.local.model.PromptVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedProjectsFlow(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAllProjectsOnce(): List<ProjectEntity>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

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

@Dao
interface PromptVersionDao {

    @Query("SELECT * FROM prompt_versions ORDER BY timestampMs DESC")
    fun observeAllVersions(): Flow<List<PromptVersionEntity>>

    @Query("SELECT * FROM prompt_versions ORDER BY timestampMs DESC")
    suspend fun getAllVersionsOnce(): List<PromptVersionEntity>

    @Query("SELECT * FROM prompt_versions WHERE projectId = :projectId ORDER BY timestampMs DESC LIMIT 20")
    suspend fun getVersionsForProject(projectId: String): List<PromptVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: PromptVersionEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersions(versions: List<PromptVersionEntity>)

    @Query("DELETE FROM prompt_versions WHERE projectId = :projectId AND id NOT IN (SELECT id FROM prompt_versions WHERE projectId = :projectId ORDER BY timestampMs DESC LIMIT :keepLimit)")
    suspend fun pruneOldVersions(projectId: String, keepLimit: Int = 20)

    @Query("SELECT COUNT(*) FROM prompt_versions")
    suspend fun getVersionCount(): Int
}

@Dao
interface CustomBlueprintDao {

    @Query("SELECT * FROM custom_blueprints ORDER BY createdAt DESC")
    fun observeCustomBlueprints(): Flow<List<CustomBlueprintEntity>>

    @Query("SELECT * FROM custom_blueprints ORDER BY createdAt DESC")
    suspend fun getAllBlueprintsOnce(): List<CustomBlueprintEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlueprint(blueprint: CustomBlueprintEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlueprints(blueprints: List<CustomBlueprintEntity>)

    @Query("DELETE FROM custom_blueprints WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM custom_blueprints")
    suspend fun getBlueprintCount(): Int
}

@Database(
    entities = [
        ProjectEntity::class,
        PromptVersionEntity::class,
        CustomBlueprintEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun promptVersionDao(): PromptVersionDao
    abstract fun customBlueprintDao(): CustomBlueprintDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `prompt_versions` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `promptText` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `qualityScore` INTEGER NOT NULL,
                        `timestampMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_prompt_versions_project_time` ON `prompt_versions` (`projectId`, `timestampMs`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_blueprints` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `targetPlatform` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `rawIdea` TEXT NOT NULL,
                        `prebuiltPrompt` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_custom_blueprints_cat_time` ON `custom_blueprints` (`category`, `createdAt`)"
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
