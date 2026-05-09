package com.universidad.avicola.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.local.entities.ProductoEntity
import com.universidad.avicola.data.model.ProductoInventario
import kotlinx.coroutines.tasks.await

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val db = Firebase.firestore
    private val colProductos = db.collection("inventario")
    private val dao = (appContext.applicationContext as AvicolaApp).database.productoDao()

    override suspend fun doWork(): Result {
        return try {
            // 1. PUSH: Subir cambios locales no sincronizados
            pushLocalChanges()

            // 2. PULL: Descargar cambios remotos
            pullRemoteChanges()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun pushLocalChanges() {
        val unsynced = dao.getUnsynced()
        for (productoEntity in unsynced) {
            val map = productoToMap(productoEntity.toDomain())
            colProductos.document(productoEntity.id).set(map).await()
            dao.markAsSynced(productoEntity.id)
        }
    }

    private suspend fun pullRemoteChanges() {
        val snapshot = colProductos.get().await()
        val remoteProductos = snapshot.documents.mapNotNull { doc ->
            doc.toObject(ProductoInventario::class.java)?.copy(id = doc.id)
        }

        // Guardar todo en local (esto sobrescribe local con lo de la nube)
        // En una implementación más avanzada, compararíamos timestamps.
        val entities = remoteProductos.map { ProductoEntity.fromDomain(it, true) }
        dao.insertAll(entities)
    }

    private fun productoToMap(p: ProductoInventario): Map<String, Any> = mapOf(
        "nombre" to p.nombre,
        "cantidad" to p.cantidad,
        "precioUnitario" to p.precioUnitario,
        "minStock" to p.minStock,
        "categoria" to p.categoria,
        "unitType" to p.unitType,
        "numeroLote" to p.numeroLote,
        "fechaVencimientoMs" to p.fechaVencimientoMs,
        "fechaCreacion" to p.fechaCreacion,
        "fechaActualizacion" to System.currentTimeMillis()
    )
}
