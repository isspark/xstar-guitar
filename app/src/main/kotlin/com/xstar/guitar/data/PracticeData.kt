package com.xstar.guitar.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "lesson_progress", primaryKeys = ["lessonId"])
data class LessonProgressEntity(val lessonId: String, val status: String = "NOT_STARTED", val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "practice_sessions")
data class PracticeSessionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val practiceId: String,
    val durationSeconds: Int,
    val bpm: Int,
    val rating: String,
    val completedAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "'COMPLETED'") val status: String = "COMPLETED",
    @androidx.room.ColumnInfo(defaultValue = "0") val completedUnits: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val correctAnswers: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "''") val problems: String = "",
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM lesson_progress ORDER BY updatedAt DESC")
    fun observeLessons(): Flow<List<LessonProgressEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun saveLesson(progress: LessonProgressEntity)

    @Insert
    suspend fun savePractice(session: PracticeSessionEntity)

    @Query("SELECT * FROM practice_sessions ORDER BY completedAt DESC, id DESC")
    fun observePractice(): Flow<List<PracticeSessionEntity>>
}

@Database(entities = [LessonProgressEntity::class, PracticeSessionEntity::class], version = 2, exportSchema = false)
abstract class XStarDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile private var instance: XStarDatabase? = null
        fun create(context: Context): XStarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, XStarDatabase::class.java, "xstar.db")
                .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
                        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN completedUnits INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN correctAnswers INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE practice_sessions ADD COLUMN problems TEXT NOT NULL DEFAULT ''")
                    }
                }).build().also { instance = it }
        }
    }
}
