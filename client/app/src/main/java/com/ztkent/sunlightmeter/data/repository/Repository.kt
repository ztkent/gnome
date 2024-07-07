package com.ztkent.sunlightmeter.data.repository

import android.app.Application
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// Data Model
@Entity(tableName = "light_readings")
data class LightReading(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val luxValue: Float
)

// DAO (Data Access Object)
@androidx.room.Dao
interface LightReadingDao {
    @androidx.room.Query("SELECT * FROM light_readings")
    fun getAllReadings(): Flow<List<LightReading>>

    @androidx.room.Insert
    suspend fun insert(reading: LightReading)
}

// Database
@Database(entities = [LightReading::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lightReadingDao(): LightReadingDao
}

// Repository
class Repository(application: Application) {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "light-readings")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
    private val lightReadingDao: LightReadingDao by lazy { database.lightReadingDao() }

    // Get all readings in the db
    fun getReadings(): Flow<List<LightReading>> {
        return lightReadingDao.getAllReadings()
    }

    // Set a new reading to the DB
    suspend fun insertReading(reading: LightReading) {
        lightReadingDao.insert(reading)
    }

    companion object {
        // Store any migrations here
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE light_readings ADD COLUMN new_column INTEGER")
            }
        }
    }
}