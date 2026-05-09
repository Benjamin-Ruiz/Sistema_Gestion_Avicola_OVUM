package com.universidad.avicola.data.repository

import android.content.Context
import androidx.work.*
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.local.entities.ProductoEntity
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.InventoryLog
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.data.sync.SyncWorker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * InventarioRepository.kt — Versión Offline-First (Room + Sync)
 */
class InventarioRepository(context: Context) {

    private val db = Firebase.firestore
    private val colProductos = db.collection("inventario")
    private val colLogs = db.collection("inventory_logs")
    
    private val app = context.applicationContext as AvicolaApp
    private val dao = app.database.productoDao()
    private val workManager = WorkManager.getInstance(context)

    // ════════════════════════════════════════════════
    //  PRODUCTOS — READ (Desde Room)
    // ════════════════════════════════════════════════

    fun obtenerProductos(): Flow<List<ProductoInventario>> {
        // Disparar una sincronización rápida al abrir para tener datos frescos
        triggerSync()
        return dao.getAllFlow().map { entities -> 
            entities.map { it.toDomain() } 
        }
    }

    // ════════════════════════════════════════════════
    //  PRODUCTOS — CRUD (Afecta Room y dispara Sync)
    // ════════════════════════════════════════════════

    suspend fun agregarProducto(producto: ProductoInventario): Result<String> {
        return try {
            // 1. Generar ID si no tiene (usamos uno temporal o UUID)
            val id = if (producto.id.isEmpty()) colProductos.document().id else producto.id
            val pConId = producto.copy(id = id)

            // 2. Guardar en Room inmediatamente (isSynced = false)
            dao.insert(ProductoEntity.fromDomain(pConId, isSynced = false))

            // 3. Registrar log localmente (Opcional, podrías tener una tabla de logs)
            registrarLog(id, pConId.nombre, pConId.cantidad, "Ingreso inicial")

            // 4. Disparar WorkManager para subir a la nube en cuanto haya internet
            triggerSync()

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun actualizarProducto(
        productoActual: ProductoInventario,
        productoNuevo: ProductoInventario,
        reason: String = "Ajuste manual"
    ): Result<Unit> {
        return try {
            // 1. Guardar en Room con flag isSynced = false
            dao.insert(ProductoEntity.fromDomain(productoNuevo, isSynced = false))

            // 2. Registrar log
            val diff = productoNuevo.cantidad - productoActual.cantidad
            if (diff != 0.0) {
                registrarLog(productoNuevo.id, productoNuevo.nombre, diff, reason)
            }

            // 3. Disparar Sync
            triggerSync()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarProducto(id: String, nombre: String): Result<Unit> {
        return try {
            // 1. Eliminar de Room
            dao.deleteById(id)

            // 2. En una app pro, marcaríamos como "borrado" para sincronizar el borrado.
            // Por ahora, intentamos borrar de Firebase directamente si hay red.
            colProductos.document(id).delete().await()
            
            registrarLog(id, nombre, 0.0, "Producto eliminado")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "OneTimeInventorySync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // ════════════════════════════════════════════════
    //  HISTORIAL DE MOVIMIENTOS
    // ════════════════════════════════════════════════

    fun obtenerLogsPorProducto(productoId: String): Flow<List<InventoryLog>> = callbackFlow {
        val listener = colLogs
            .whereEqualTo("productoId", productoId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(InventoryLog::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun registrarLog(
        productoId: String,
        productoNombre: String,
        quantityChange: Double,
        reason: String
    ) {
        try {
            val log = hashMapOf(
                "productoId" to productoId,
                "productoNombre" to productoNombre,
                "quantityChange" to quantityChange,
                "reason" to reason,
                "timestamp" to System.currentTimeMillis()
            )
            colLogs.add(log).await()
        } catch (e: Exception) {
            // El log no debe romper el flujo principal
        }
    }

    // ════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════

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
