
package com.geosigpac.cirserv.database

import android.content.Context
import androidx.room.*
import com.geosigpac.cirserv.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Database(entities = [NativeExpediente::class, NativeParcela::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geosigpac_db"
                ).fallbackToDestructiveMigration()
                 .build() // Eliminado allowMainThreadQueries()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String = gson.toJson(value)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String>? {
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value)

    @TypeConverter
    fun toStringList(value: String): List<String>? {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromSigpacData(value: SigpacData?): String = gson.toJson(value)

    @TypeConverter
    fun toSigpacData(value: String): SigpacData? = gson.fromJson(value, SigpacData::class.java)

    @TypeConverter
    fun fromCultivoData(value: CultivoData?): String = gson.toJson(value)

    @TypeConverter
    fun toCultivoData(value: String): CultivoData? = gson.fromJson(value, CultivoData::class.java)
}
