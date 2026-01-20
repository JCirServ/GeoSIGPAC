
package com.geosigpac.cirserv.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// --- ENTIDADES ---

@Entity(tableName = "expedientes")
data class Expediente(
    @PrimaryKey val id: String, // Referencia del KML o ID generado
    val titular: String,
    val campana: Int,
    val fechaImportacion: Long,
    val estadoGlobal: String // 'pendiente', 'en_curso', 'finalizado'
)

@Entity(
    tableName = "recintos",
    foreignKeys = [ForeignKey(
        entity = Expediente::class,
        parentColumns = ["id"],
        childColumns = ["expedienteId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RecintoEntity(
    @PrimaryKey val id: String, // UUID o Referencia SIGPAC única
    val expedienteId: String,
    
    // Identificación SIGPAC
    val provincia: String,
    val municipio: String,
    val poligono: String,
    val parcela: String,
    val recinto: String,
    
    // Datos Declarados (KML)
    val usoDeclarado: String,
    val superficieDeclarada: Double,
    val geomDeclaradaJson: String, // GeoJSON String para MapLibre
    
    // Datos Oficiales (SIGPAC API) - Se rellenan al inspeccionar
    val usoOficial: String? = null,
    val superficieOficial: Double? = null,
    
    // Estado Inspección
    val estadoInspeccion: String = "pendiente", // 'pendiente', 'conforme', 'discrepancia'
    val tipoDiscrepancia: String? = null // 'uso', 'geometria', 'ambos'
)

@Entity(
    tableName = "hallazgos",
    foreignKeys = [ForeignKey(
        entity = RecintoEntity::class,
        parentColumns = ["id"],
        childColumns = ["recintoId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Hallazgo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recintoId: String,
    val tipo: String, // 'foto', 'nota', 'geometria_real'
    val contenido: String, // URI de foto, texto o GeoJSON
    val timestamp: Long
)

// --- DAO ---

@Dao
interface InspectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpediente(expediente: Expediente)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecintos(recintos: List<RecintoEntity>)

    @Query("SELECT * FROM expedientes ORDER BY fechaImportacion DESC")
    fun getAllExpedientes(): Flow<List<Expediente>>

    @Query("SELECT * FROM recintos WHERE expedienteId = :expId")
    fun getRecintosByExpediente(expId: String): Flow<List<RecintoEntity>>

    @Query("SELECT * FROM recintos WHERE expedienteId = :expId")
    suspend fun getRecintosList(expId: String): List<RecintoEntity>

    @Query("UPDATE recintos SET estadoInspeccion = :estado, tipoDiscrepancia = :tipoDisc WHERE id = :recintoId")
    suspend fun updateRecintoStatus(recintoId: String, estado: String, tipoDisc: String?)

    @Insert
    suspend fun insertHallazgo(hallazgo: Hallazgo)
    
    @Transaction
    suspend fun createFullInspection(exp: Expediente, recs: List<RecintoEntity>) {
        insertExpediente(exp)
        insertRecintos(recs)
    }
}

// --- DATABASE ---

@Database(entities = [Expediente::class, RecintoEntity::class, Hallazgo::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inspectionDao(): InspectionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geosigpac_db"
                ).build().also { instance = it }
            }
        }
    }
}
