
package com.geosigpac.cirserv.utils

import android.content.Context
import android.util.Log
import com.geosigpac.cirserv.database.AppDatabase
import com.geosigpac.cirserv.model.NativeExpediente
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object ProjectStorage {
    private const val TAG = "ProjectStorage"

    /**
     * Guarda la lista completa de expedientes de forma asíncrona.
     */
    suspend fun saveExpedientes(context: Context, expedientes: List<NativeExpediente>) {
        try {
            val dao = AppDatabase.getDatabase(context).projectDao()
            dao.clearAndSaveAll(expedientes)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando en Room: ${e.message}", e)
        }
    }

    /**
     * Devuelve un Flow reactivo que emite la lista de expedientes mapeada.
     */
    fun getExpedientesFlow(context: Context): Flow<List<NativeExpediente>> {
        val dao = AppDatabase.getDatabase(context).projectDao()
        return dao.getAllExpedientesFlow().map { list ->
            list.map { item ->
                NativeExpediente(
                    id = item.expediente.id,
                    titular = item.expediente.titular,
                    fechaImportacion = item.expediente.fechaImportacion,
                    parcelas = item.parcelas
                )
            }
        }
    }
}
