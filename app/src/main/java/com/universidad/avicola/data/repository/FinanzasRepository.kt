package com.universidad.avicola.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.model.CategoriaGasto
import com.universidad.avicola.data.model.EstadoPago
import com.universidad.avicola.data.model.Transaccion
import com.universidad.avicola.data.model.TipoTransaccion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

class FinanzasRepository {

    private val db = Firebase.firestore
    private val col = db.collection("transacciones")
    private val storage = Firebase.storage

    // Contexto para ContentResolver
    private val context: Context
        get() = AvicolaApp.instance.applicationContext

    private fun currentUserId(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── Lectura ──────────────────────────────────
    fun obtenerTransacciones(): Flow<List<Transaccion>> = callbackFlow {
        val listener = col
            .whereEqualTo("userId", currentUserId())
            .orderBy("fechaMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
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
            .whereEqualTo("userId", currentUserId())
            .orderBy("fechaMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaccion::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    fun obtenerCuentasPendientes(): Flow<List<Transaccion>> = callbackFlow {
        val listener = col
            .whereEqualTo("userId", currentUserId())
            .whereIn("estado", listOf(EstadoPago.PENDIENTE.name, EstadoPago.PARCIAL.name))
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Transaccion::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                // Ordenar manualmente por fecha descendente (sin índice)
                trySend(lista.sortedByDescending { it.fechaMs })
            }
        awaitClose { listener.remove() }
    }

    // ── CRUD ─────────────────────────────────────
    suspend fun agregarTransaccion(t: Transaccion): Result<String> {
        return try {
            val transaccionConUserId = t.copy(userId = currentUserId())
            val datos = transaccionToMap(transaccionConUserId)
            Log.d("FinanzasRepo", "Guardando transacción con userId: ${currentUserId()}")
            val ref = col.add(datos).await()
            Log.d("FinanzasRepo", "Transacción guardada con id: ${ref.id}")
            Result.success(ref.id)
        } catch (e: Exception) {
            Log.e("FinanzasRepo", "Error al guardar transacción", e)
            Result.failure(e)
        }
    }

    suspend fun actualizarTransaccion(t: Transaccion): Result<Unit> {
        return try {
            col.document(t.id).update(transaccionToMap(t.copy(userId = currentUserId()))).await()
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

    // ── Subida de imagen a Storage ───────────────
    suspend fun subirFotoRecibo(uri: Uri): Result<String> {
        return try {
            val storageRef = storage.reference
                .child("recibos/${currentUserId()}/${UUID.randomUUID()}.jpg")

            // Abrir InputStream desde el content URI
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("No se pudo abrir la imagen"))

            val uploadTask = storageRef.putStream(inputStream).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await()
            Log.d("FinanzasRepo", "Imagen subida: $downloadUrl")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e("FinanzasRepo", "Error subiendo imagen", e)
            Result.failure(e)
        }
    }

    // ── Integraciones ────────────────────────────
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
            productoInventarioId = productoId,
            userId = currentUserId()
        )
        return agregarTransaccion(transaccion)
    }

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
            galponId = galponId,
            userId = currentUserId()
        )
        return agregarTransaccion(transaccion)
    }

    // ── Helper ───────────────────────────────────
    private fun transaccionToMap(t: Transaccion): Map<String, Any> = mapOf(
        "userId" to t.userId,
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