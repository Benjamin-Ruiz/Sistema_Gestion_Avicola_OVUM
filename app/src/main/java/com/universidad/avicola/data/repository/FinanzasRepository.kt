package com.universidad.avicola.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.data.model.CategoriaGasto
import com.universidad.avicola.data.model.EstadoPago
import com.universidad.avicola.data.model.Transaccion
import com.universidad.avicola.data.model.TipoTransaccion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * FinanzasRepository.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
 *
 * Colección Firestore: "transacciones"
 */
class FinanzasRepository {

    private val db = Firebase.firestore
    private val col = db.collection("transacciones")

    // ════════════════════════════════════════════════
    //  READ — Tiempo real
    // ════════════════════════════════════════════════

    fun obtenerTransacciones(): Flow<List<Transaccion>> = callbackFlow {
        val listener = col
            .orderBy("fechaMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaccion::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    fun obtenerPorMes(anio: Int, mes: Int): Flow<List<Transaccion>> = callbackFlow {
        val cal = Calendar.getInstance()
        cal.set(anio, mes, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis
        cal.set(anio, mes + 1, 1, 0, 0, 0)
        val fin = cal.timeInMillis

        val listener = col
            .whereGreaterThanOrEqualTo("fechaMs", inicio)
            .whereLessThan("fechaMs", fin)
            .orderBy("fechaMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaccion::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    fun obtenerCuentasPendientes(): Flow<List<Transaccion>> = callbackFlow {
        val listener = col
            .whereIn("estado", listOf(EstadoPago.PENDIENTE.name, EstadoPago.PARCIAL.name))
            .orderBy("fechaMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaccion::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    // ════════════════════════════════════════════════
    //  CRUD
    // ════════════════════════════════════════════════

    suspend fun agregarTransaccion(t: Transaccion): Result<String> {
        return try {
            val datos = transaccionToMap(t)
            val ref = col.add(datos).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun actualizarTransaccion(t: Transaccion): Result<Unit> {
        return try {
            col.document(t.id).update(transaccionToMap(t)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarTransaccion(id: String): Result<Unit> {
        return try {
            col.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Integración con Inventario: cuando se agrega una compra de alimento
     * al inventario, se sugiere automáticamente un gasto en Finanzas.
     */
    suspend fun sugerirGastoDesdeInventario(
        nombreProducto: String,
        monto: Double,
        productoId: String,
        categoria: CategoriaGasto = CategoriaGasto.ALIMENTO
    ): Result<String> {
        val transaccion = Transaccion(
            tipo = TipoTransaccion.GASTO.name,
            categoria = categoria.name,
            descripcion = "Compra: $nombreProducto (desde Inventario)",
            monto = monto,
            estado = EstadoPago.PAGADO.name,
            fechaMs = System.currentTimeMillis(),
            productoInventarioId = productoId
        )
        return agregarTransaccion(transaccion)
    }

    /**
     * Integración con Galpones: calcula pérdida económica por mortalidad
     */
    suspend fun registrarPerdidaMortalidad(
        galponId: String,
        cantidadBajas: Int,
        costoPromedioAve: Double,
        motivo: String
    ): Result<String> {
        val perdida = cantidadBajas * costoPromedioAve
        val transaccion = Transaccion(
            tipo = TipoTransaccion.GASTO.name,
            categoria = CategoriaGasto.OTRO_GASTO.name,
            descripcion = "Pérdida por mortalidad: $cantidadBajas aves — $motivo",
            monto = perdida,
            estado = EstadoPago.PAGADO.name,
            fechaMs = System.currentTimeMillis(),
            galponId = galponId
        )
        return agregarTransaccion(transaccion)
    }

    // ════════════════════════════════════════════════
    //  HELPER (público, o puede ser privado si se usa solo aquí)
    // ════════════════════════════════════════════════

    private fun transaccionToMap(t: Transaccion): Map<String, Any> = mapOf(
        "tipo" to t.tipo,
        "categoria" to t.categoria,
        "descripcion" to t.descripcion,
        "monto" to t.monto,
        "estado" to t.estado,
        "fechaMs" to t.fechaMs,
        "fechaVencimientoMs" to t.fechaVencimientoMs,
        "montoPagado" to t.montoPagado,
        "contacto" to t.contacto,
        "loteId" to t.loteId,
        "fotoUrl" to t.fotoUrl,
        "notas" to t.notas,
        "productoInventarioId" to t.productoInventarioId,
        "galponId" to t.galponId
    )
}