
package com.geosigpac.cirserv.database

import androidx.room.*
import com.geosigpac.cirserv.model.*
import kotlinx.coroutines.flow.Flow

data class ExpedienteWithParcelas(
    @Embedded val expediente: NativeExpediente,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentExpedienteId"
    )
    val parcelas: List<NativeParcela>
)

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM expedientes ORDER BY fechaImportacion DESC")
    fun getAllExpedientesFlow(): Flow<List<ExpedienteWithParcelas>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpediente(expediente: NativeExpediente)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParcelas(parcelas: List<NativeParcela>)

    @Query("DELETE FROM expedientes WHERE id = :expId")
    suspend fun deleteExpediente(expId: String)

    @Query("DELETE FROM expedientes")
    suspend fun deleteAllExpedientes()

    @Transaction
    suspend fun saveFullExpediente(expWithParcelas: NativeExpediente) {
        insertExpediente(expWithParcelas)
        insertParcelas(expWithParcelas.parcelas)
    }
    
    @Transaction
    suspend fun clearAndSaveAll(list: List<NativeExpediente>) {
        deleteAllExpedientes()
        list.forEach { saveFullExpediente(it) }
    }
}
