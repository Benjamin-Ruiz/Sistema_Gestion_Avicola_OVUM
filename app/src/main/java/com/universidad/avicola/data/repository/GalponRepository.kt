package com.universidad.avicola.data.repository

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.data.model.EstadoGalpon
import com.universidad.avicola.data.model.Galpon
import com.universidad.avicola.data.model.MovimientoAves
import com.universidad.avicola.data.model.TipoMovimiento
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * GalponRepository.kt — Módulo de Aves
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
 *
 * Firestore collections:
 *   galpones/            → datos de cada galpón
 *   movimientos_aves/    → bajas y traslados
 */
class GalponRepository {

    private val db = Firebase.firestore
    private val colGalpones = db.collection("galpones")
    private val colMovimientos = db.collection("movimientos_aves")

    // ════════════════════════════════════════════════
    //  GALPONES — READ
    // ════════════════════════════════════════════════

    fun obtenerGalpones(): Flow<List<Galpon>> = callbackFlow {
        val listener = colGalpones
            .orderBy("numero", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Galpon::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    // ════════════════════════════════════════════════
    //  GALPONES — CRUD
    // ════════════════════════════════════════════════

    suspend fun agregarGalpon(galpon: Galpon): Result<String> {
        return try {
            val datos = galponToMap(galpon)
            val ref = colGalpones.add(datos).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun actualizarGalpon(galpon: Galpon): Result<Unit> {
        return try {
            colGalpones.document(galpon.id).update(galponToMap(galpon)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarGalpon(id: String): Result<Unit> {
        return try {
            colGalpones.document(id).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ════════════════════════════════════════════════
    //  BAJAS — Mortalidad
    // ════════════════════════════════════════════════

    /**
     * Registra una baja por mortalidad.
     * Descuenta las aves del galpón y crea un movimiento.
     */
    suspend fun registrarBaja(
        galpon: Galpon,
        cantidadBaja: Int,
        motivo: String
    ): Result<Unit> {
        return try {
            if (cantidadBaja <= 0) return Result.failure(Exception("La cantidad debe ser mayor a 0"))
            if (cantidadBaja > galpon.cantidadAves) return Result.failure(Exception("No hay suficientes aves"))

            val nuevaCantidad = galpon.cantidadAves - cantidadBaja
            val nuevoEstado = if (nuevaCantidad == 0) EstadoGalpon.VACIO.name else galpon.estado

            // Actualizar galpón
            colGalpones.document(galpon.id).update(
                mapOf(
                    "cantidadAves" to nuevaCantidad,
                    "estado" to nuevoEstado
                )
            ).await()

            // Registrar movimiento
            val movimiento = hashMapOf(
                "galponOrigenId" to galpon.id,
                "galponDestinoId" to "",
                "cantidadAves" to cantidadBaja,
                "tipoMovimiento" to TipoMovimiento.BAJA.name,
                "motivo" to motivo,
                "timestamp" to System.currentTimeMillis()
            )
            colMovimientos.add(movimiento).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ════════════════════════════════════════════════
    //  TRASLADOS
    // ════════════════════════════════════════════════

    /**
     * Traslada aves de un galpón a otro.
     * Descuenta del origen y suma al destino.
     */
    suspend fun registrarTraslado(
        galponOrigen: Galpon,
        galponDestino: Galpon,
        cantidad: Int,
        motivo: String
    ): Result<Unit> {
        return try {
            if (cantidad <= 0) return Result.failure(Exception("La cantidad debe ser mayor a 0"))
            if (cantidad > galponOrigen.cantidadAves) return Result.failure(Exception("No hay suficientes aves en el galpón origen"))

            val batch = db.batch()

            // Actualizar origen
            val origenRef = colGalpones.document(galponOrigen.id)
            val nuevaCantidadOrigen = galponOrigen.cantidadAves - cantidad
            batch.update(origenRef, mapOf(
                "cantidadAves" to nuevaCantidadOrigen,
                "estado" to if (nuevaCantidadOrigen == 0) EstadoGalpon.VACIO.name else galponOrigen.estado
            ))

            // Actualizar destino
            val destinoRef = colGalpones.document(galponDestino.id)
            batch.update(destinoRef, mapOf(
                "cantidadAves" to galponDestino.cantidadAves + cantidad,
                "estado" to EstadoGalpon.ACTIVO.name
            ))

            batch.commit().await()

            // Registrar movimiento
            val movimiento = hashMapOf(
                "galponOrigenId" to galponOrigen.id,
                "galponDestinoId" to galponDestino.id,
                "cantidadAves" to cantidad,
                "tipoMovimiento" to TipoMovimiento.TRASLADO.name,
                "motivo" to motivo,
                "timestamp" to System.currentTimeMillis()
            )
            colMovimientos.add(movimiento).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ════════════════════════════════════════════════
    //  MOVIMIENTOS — READ
    // ════════════════════════════════════════════════

    fun obtenerMovimientos(): Flow<List<MovimientoAves>> = callbackFlow {
        val listener = colMovimientos
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val lista = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(MovimientoAves::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(lista)
            }
        awaitClose { listener.remove() }
    }

    // ════════════════════════════════════════════════
    //  HELPER
    // ════════════════════════════════════════════════

    private fun galponToMap(g: Galpon): Map<String, Any> = mapOf(
        "numero" to g.numero,
        "cantidadAves" to g.cantidadAves,
        "edadSemanas" to g.edadSemanas,
        "tipoAve" to g.tipoAve,
        "estado" to g.estado,
        "fechaIngreso" to g.fechaIngreso,
        "observaciones" to g.observaciones
    )
}
