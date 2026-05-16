package com.universidad.avicola.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.data.model.ProductoInventario
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class InventarioFirestoreRepository {
    private val db = Firebase.firestore

    private val col = db.collection("inventario")

    private fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Obtener productos
    fun obtenerProductos(): Flow<List<ProductoInventario>> = callbackFlow {
        val listener = col
            .whereEqualTo("userId", currentUserId())
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

    // Agregar producto (ASIGNANDO userId)
    suspend fun agregarProducto(producto: ProductoInventario): Result<String> {
        return try {
            val uid = currentUserId()
            // Forzamos que el producto tenga el ID del usuario actual
            val productoConUser = producto.copy(userId = uid)

            // Usamos el mapa que ahora SÍ incluye el userId
            val ref = col.add(productoToMap(productoConUser)).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Actualizar producto
    // Modifica este método en InventarioFirestoreRepository.kt
    suspend fun actualizarProducto(producto: ProductoInventario): Result<Unit> {
        return try {
            val uid = currentUserId()
            val productoConUserId = producto.copy(userId = uid)
            // ── CAMBIO: set con merge en lugar de update ────────
            // update() lanza excepción si el doc no existe; set() lo crea si falta
            col.document(producto.id)
                .set(productoToMap(productoConUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Eliminar producto
    suspend fun eliminarProducto(id: String): Result<Unit> {
        return try {
            col.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Convertir a mapa (INCLUYENDO userId)
    private fun productoToMap(p: ProductoInventario): Map<String, Any> = mapOf(
        "userId" to p.userId, // <--- CRITICO: Esto debe coincidir con la regla de Firebase
        "nombre" to p.nombre,
        "cantidad" to p.cantidad,
        "precioUnitario" to p.precioUnitario,
        "minStock" to p.minStock,
        "categoria" to p.categoria,
        "unitType" to p.unitType,
        "numeroLote" to p.numeroLote,
        "fechaVencimientoMs" to p.fechaVencimientoMs,
        "fechaCreacion" to p.fechaCreacion,
        "fechaActualizacion" to p.fechaActualizacion
    )
}