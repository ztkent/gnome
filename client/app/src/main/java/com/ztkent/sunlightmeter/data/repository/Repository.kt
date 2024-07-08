package com.ztkent.sunlightmeter.data.repository

import android.app.Application
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// Data Model
@Entity(
    tableName = "connected_devices",
    indices = [androidx.room.Index(value = ["ssid"], unique = true)]
)
data class ConnectedDevice(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val ssid: String,
)

// DAO (Data Access Object)
@androidx.room.Dao
interface SunlightMeterDAO {
    @androidx.room.Query("SELECT * FROM connected_devices")
    fun getAllConnectedDevices(): Flow<List<ConnectedDevice>>

    @androidx.room.Upsert
    suspend fun upsert(reading: ConnectedDevice)
}

// Database
@Database(entities = [ConnectedDevice::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sunlightMeterDAO(): SunlightMeterDAO
}

// Repository
class Repository(application: Application) {
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(application, AppDatabase::class.java, "sunlight-meter")
//            .addMigrations(MIGRATION_1_2)
            .build()
    }
    private val sunlightMeterDAO: SunlightMeterDAO by lazy { database.sunlightMeterDAO() }

    // Get all readings in the db
    fun getDevices(): Flow<List<ConnectedDevice>> {
        return sunlightMeterDAO.getAllConnectedDevices()
    }

    // Set a new reading to the DB
    suspend fun insertDevice(reading: ConnectedDevice) {
        sunlightMeterDAO.upsert(reading)
    }


    companion object {
        // Store any migrations here
//        val MIGRATION_1_2 = object : Migration(1, 2) {
//            override fun migrate(db: SupportSQLiteDatabase) {
//                // db.execSQL("ALTER TABLE connected_devices ADD COLUMN new_column INTEGER")
//            }
//        }
    }
}