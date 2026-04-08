package com.universidad.avicola.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.InventoryLog
import com.universidad.avicola.data.model.ProductoInventario
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * InventarioRepository.kt — Versión Pro
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
 *
 * Firestore collections:
 *   inventario/         → productos
 *   inventory_logs/     → historial de movimientos
 */
class InventarioRepository {

    private val db = Firebase.firestore
    private val colProductos = db.collection("inventario")
    private val colLogs = db.collection("inventory_logs")

    // ════════════════════════════════════════════════
    //  PRODUCTOS — READ
    // ════════════════════════════════════════════════

    fun obtenerProductos(): Flow<List<ProductoInventario>> = callbackFlow {
        val listener = colProductos
            .orderBy("nombre", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ProductoInventario::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    fun obtenerProductosPorCategoria(categoria: Categoria): Flow<List<ProductoInventario>> = callbackFlow {
        val listener = colProductos
            .whereEqualTo("categoria", categoria.name)
            .orderBy("nombre", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ProductoInventario::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    // ════════════════════════════════════════════════
    //  PRODUCTOS — CRUD
    // ════════════════════════════════════════════════

    suspend fun agregarProducto(producto: ProductoInventario): Result<String> {
        return try {
            val datos = productoToMap(producto)
            val docRef = colProductos.add(datos).await()

            // Registrar log de entrada inicial
            registrarLog(
                productoId = docRef.id,
                productoNombre = producto.nombre,
                quantityChange = producto.cantidad,
                reason = "Ingreso inicial"
            )
            Result.success(docRef.id)
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
            val datos = productoToMap(productoNuevo).toMutableMap()
            datos["fechaActualizacion"] = System.currentTimeMillis()

            colProductos.document(productoNuevo.id).update(datos).await()

            // Registrar log solo si la cantidad cambió
            val diff = productoNuevo.cantidad - productoActual.cantidad
            if (diff != 0.0) {
                registrarLog(
                    productoId = productoNuevo.id,
                    productoNombre = productoNuevo.nombre,
                    quantityChange = diff,
                    reason = reason
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarProducto(id: String, nombre: String): Result<Unit> {
        return try {
            colProductos.document(id).delete().await()
            registrarLog(id, nombre, 0.0, "Producto eliminado del inventario")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
