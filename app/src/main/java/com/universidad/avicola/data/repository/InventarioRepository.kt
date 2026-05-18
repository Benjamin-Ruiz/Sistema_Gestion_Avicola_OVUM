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
 *
 * Fix v2:
 *  - eliminarProducto() ahora es offline-safe (usa tombstones en SharedPreferences).
 *  - Nuevo método forzarSincronizacionRemota() para pull inmediato bajo demanda
 *    (lo usa el módulo de Costos al abrir el formulario).
 */
class InventarioRepository(context: Context) {

    private val ctx = context.applicationContext
    private val db = Firebase.firestore
    private val colProductos = db.collection("inventario")
    private val colLogs = db.collection("inventory_logs")

    private val app = ctx as AvicolaApp
    private val dao = app.database.productoDao()
    private val workManager = WorkManager.getInstance(context)

    // ═══════════════════════════════════════════════════════════════════════
    //  PRODUCTOS — READ (Desde Room)
    // ═══════════════════════════════════════════════════════════════════════

    fun obtenerProductos(): Flow<List<ProductoInventario>> {
        // Disparar una sincronización rápida al abrir para tener datos frescos
        triggerSync()
        return dao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Fuerza una sincronización inmediata (push pendientes + pull remoto con
     * reconciliación de borrados). Se llama bajo demanda, p. ej. al abrir el
     * formulario de Costos para garantizar que los spinners reflejen el
     * estado real del inventario.
     *
     * Retorna éxito incluso si no hay red: en ese caso el caller seguirá viendo
     * los datos locales actuales y la sincronización ocurrirá automáticamente
     * cuando vuelva la conexión.
     */
    suspend fun forzarSincronizacionRemota(): Result<Unit> {
        return try {
            // 1. PUSH cambios locales pendientes
            val unsynced = dao.getUnsynced()
            for (productoEntity in unsynced) {
                try {
                    colProductos.document(productoEntity.id)
                        .set(productoToMap(productoEntity.toDomain()))
                        .await()
                    dao.markAsSynced(productoEntity.id)
                } catch (_: Exception) {
                    // si falla, el WorkManager lo reintentará
                }
            }

            // 2. PUSH borrados pendientes (tombstones)
            procesarBorradosPendientes()

            // 3. PULL remoto y reconciliar con local
            val snapshot = colProductos.get().await()
            val remoteProductos = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ProductoInventario::class.java)?.copy(id = doc.id)
            }
            val entities = remoteProductos.map { ProductoEntity.fromDomain(it, true) }
            dao.reconciliarConRemoto(entities)

            Result.success(Unit)
        } catch (e: Exception) {
            // Si no hay red, los datos locales seguirán mostrándose. No es un error fatal.
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PRODUCTOS — CRUD (Afecta Room y dispara Sync)
    // ═══════════════════════════════════════════════════════════════════════

    suspend fun agregarProducto(producto: ProductoInventario): Result<String> {
        return try {
            // 1. Generar ID si no tiene (usamos uno temporal o UUID)
            val id = if (producto.id.isEmpty()) colProductos.document().id else producto.id
            val pConId = producto.copy(id = id)

            // 2. Guardar en Room inmediatamente (isSynced = false)
            dao.insert(ProductoEntity.fromDomain(pConId, isSynced = false))

            // 3. Registrar log localmente
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

    /**
     * Eliminación offline-safe:
     *  - Siempre borra de Room inmediatamente (la UI reacciona al instante).
     *  - Intenta borrar de Firestore. Si falla (sin red), guarda el ID en
     *    una lista de "borrados pendientes" para procesarlos en el próximo sync.
     *  - Así el pull no puede "resucitar" el producto en el próximo ciclo.
     */
    suspend fun eliminarProducto(id: String, nombre: String): Result<Unit> {
        return try {
            // 1. Borrar de Room siempre
            dao.deleteById(id)

            // 2. Intentar borrar de Firestore
            var firestoreOk = false
            try {
                colProductos.document(id).delete().await()
                firestoreOk = true
            } catch (_: Exception) {
                // Sin red u otro error — encolar tombstone
                agregarBorradoPendiente(id)
                triggerSync()
            }

            // 3. Log
            registrarLog(id, nombre, 0.0, if (firestoreOk) "Producto eliminado" else "Eliminado (pendiente de sincronizar)")

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ───────────────────── Tombstones de borrados pendientes ──────────────

    private fun agregarBorradoPendiente(id: String) {
        val prefs = ctx.getSharedPreferences("pending_deletes", Context.MODE_PRIVATE)
        val actuales = prefs.getStringSet("inventario", emptySet())?.toMutableSet() ?: mutableSetOf()
        actuales.add(id)
        prefs.edit().putStringSet("inventario", actuales).apply()
    }

    private suspend fun procesarBorradosPendientes() {
        val prefs = ctx.getSharedPreferences("pending_deletes", Context.MODE_PRIVATE)
        val pending = prefs.getStringSet("inventario", emptySet())?.toMutableSet() ?: return
        if (pending.isEmpty()) return

        val confirmados = mutableSetOf<String>()
        for (id in pending) {
            try {
                colProductos.document(id).delete().await()
                confirmados.add(id)
            } catch (_: Exception) {
                // se reintentará luego
            }
        }
        if (confirmados.isNotEmpty()) {
            pending.removeAll(confirmados)
            prefs.edit().putStringSet("inventario", pending).apply()
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

    // ═══════════════════════════════════════════════════════════════════════
    //  HISTORIAL DE MOVIMIENTOS
    // ═══════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════

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