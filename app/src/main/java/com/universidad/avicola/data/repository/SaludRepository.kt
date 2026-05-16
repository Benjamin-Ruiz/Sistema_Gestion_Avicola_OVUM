package com.universidad.avicola.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.local.entities.RegistroMedicoEntity
import com.universidad.avicola.data.local.entities.VacunacionEntity
import com.universidad.avicola.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
//  SaludRepository.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
// ─────────────────────────────────────────────────────────────────────────────

class SaludRepository(context: Context) {

    private val app = context.applicationContext as AvicolaApp
    private val registroDao = app.database.registroMedicoDao()
    private val vacunacionDao = app.database.vacunacionDao()

    private val avesRepo = AvesRepository(context)
    private val inventarioRepo = InventarioRepository(context)
    private val finanzasRepo = FinanzasRepository()

    private fun uid() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ══════════════════════════════════════════════════════════════════
    //  REGISTROS MÉDICOS
    // ══════════════════════════════════════════════════════════════════

    fun obtenerRegistros(): Flow<List<RegistroMedico>> =
        registroDao.getAllFlow().map { it.map { e -> e.toDomain() } }

    fun obtenerRegistrosPorLote(loteId: String): Flow<List<RegistroMedico>> =
        registroDao.getPorLote(loteId).map { it.map { e -> e.toDomain() } }

    fun obtenerRegistrosPendientes(): Flow<List<RegistroMedico>> =
        registroDao.getPendientes().map { it.map { e -> e.toDomain() } }

    fun obtenerRegistrosUrgentes(): Flow<List<RegistroMedico>> =
        registroDao.getUrgentes().map { it.map { e -> e.toDomain() } }

    suspend fun guardarRegistro(registro: RegistroMedico): Result<String> {
        return try {
            val id = if (registro.id.isEmpty()) UUID.randomUUID().toString() else registro.id
            val final = registro.copy(id = id, userId = uid())
            registroDao.insert(RegistroMedicoEntity.fromDomain(final))

            // Si es mortalidad, registrar en Finanzas automáticamente
            if (registro.tipo == TipoRegistroMedico.MORTALIDAD.name && registro.costo > 0) {
                finanzasRepo.registrarPerdidaMortalidad(
                    galponId = registro.galponId,
                    cantidadBajas = registro.avesAfectadas,
                    costoPromedioAve = if (registro.avesAfectadas > 0) registro.costo / registro.avesAfectadas else 0.0,
                    motivo = "Mortalidad registrada: ${registro.descripcion}"
                )
            }

            // Si es tratamiento con medicamento, descontar del inventario
            if (registro.medicamentoId.isNotEmpty() && registro.costo > 0) {
                registrarGastoSanitarioEnFinanzas(registro)
            }

            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun marcarResuelta(id: String): Result<Unit> {
        return try {
            registroDao.marcarResuelta(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarRegistro(id: String): Result<Unit> {
        return try {
            registroDao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  VACUNACIONES
    // ══════════════════════════════════════════════════════════════════

    fun obtenerVacunaciones(): Flow<List<Vacunacion>> =
        vacunacionDao.getAllFlow().map { it.map { e -> e.toDomain() } }

    fun obtenerVacunacionesPorLote(loteId: String): Flow<List<Vacunacion>> =
        vacunacionDao.getPorLote(loteId).map { it.map { e -> e.toDomain() } }

    fun obtenerVacunacionesPendientes(): Flow<List<Vacunacion>> =
        vacunacionDao.getPendientes().map { it.map { e -> e.toDomain() } }

    suspend fun guardarVacunacion(vacunacion: Vacunacion): Result<String> {
        return try {
            val id = if (vacunacion.id.isEmpty()) UUID.randomUUID().toString() else vacunacion.id
            val final = vacunacion.copy(id = id, userId = uid())
            vacunacionDao.insert(VacunacionEntity.fromDomain(final))
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun marcarVacunacionAplicada(
        vacunacion: Vacunacion,
        productos: List<ProductoInventario>
    ): Result<Unit> {
        return try {
            val ahora = System.currentTimeMillis()
            vacunacionDao.marcarAplicada(vacunacion.id, ahora)

            // Descontar del inventario si tiene medicamento vinculado
            if (vacunacion.medicamentoId.isNotEmpty()) {
                val producto = productos.firstOrNull { it.id == vacunacion.medicamentoId }
                if (producto != null) {
                    val dosisNum = vacunacion.dosis.toDoubleOrNull() ?: 0.0
                    val nuevoStock = producto.cantidad - dosisNum
                    if (nuevoStock >= 0) {
                        inventarioRepo.actualizarProducto(
                            productoActual = producto,
                            productoNuevo  = producto.copy(cantidad = nuevoStock),
                            reason         = "Vacunación: ${vacunacion.nombreVacuna} — Lote: ${vacunacion.loteNombre}"
                        )
                    }
                }
            }

            // Registrar gasto en Finanzas
            if (vacunacion.costo > 0) {
                finanzasRepo.agregarTransaccion(
                    Transaccion(
                        tipo        = TipoTransaccion.GASTO.name,
                        categoria   = CategoriaGasto.MEDICINAS.name,
                        descripcion = "Vacunación: ${vacunacion.nombreVacuna} — ${vacunacion.loteNombre}",
                        monto       = vacunacion.costo,
                        estado      = EstadoPago.PAGADO.name,
                        fechaMs     = ahora,
                        loteId      = vacunacion.loteId,
                        notas       = "Aves vacunadas: ${vacunacion.avesVacunadas}"
                    )
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarVacunacion(id: String): Result<Unit> {
        return try {
            vacunacionDao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ESTADO SANITARIO POR LOTE
    // ══════════════════════════════════════════════════════════════════

    suspend fun calcularEstadoSanitario(lote: Lote): EstadoSanitarioLote {
        val registrosPendientes = registroDao.countPendientesPorLote(lote.id)
        val vacunasPendientes   = vacunacionDao.countPendientesPorLote(lote.id)
        val costoRegistros      = registroDao.costoTotalPorLote(lote.id) ?: 0.0
        val costoVacunas        = vacunacionDao.costoTotalPorLote(lote.id) ?: 0.0

        val mortalidadTotal = lote.cantidadInicial - lote.cantidadActual
        val pctMortalidad   = if (lote.cantidadInicial > 0)
            (mortalidadTotal.toDouble() / lote.cantidadInicial) * 100.0 else 0.0

        val alertas = mutableListOf<String>()
        if (pctMortalidad > 8.0) alertas.add("⚠ Mortalidad elevada (${String.format("%.1f", pctMortalidad)}%)")
        if (vacunasPendientes > 0) alertas.add("⚠ $vacunasPendientes vacuna(s) pendiente(s)")
        if (registrosPendientes > 0) alertas.add("⚠ $registrosPendientes caso(s) sin resolver")

        val estado = when {
            pctMortalidad > 15.0 || (registrosPendientes > 0 && pctMortalidad > 8.0) -> EstadoSanidad.CRITICO.name
            pctMortalidad > 8.0 || registrosPendientes > 2 -> EstadoSanidad.EN_RIESGO.name
            vacunasPendientes > 0 || registrosPendientes > 0 -> EstadoSanidad.VIGILANCIA.name
            else -> EstadoSanidad.NORMAL.name
        }

        return EstadoSanitarioLote(
            loteId = lote.id,
            loteNombre = lote.lineaGenetica,
            galponId = lote.galponId,
            cantidadAves = lote.cantidadActual,
            mortalidadTotal = mortalidadTotal,
            porcentajeMortalidad = pctMortalidad,
            estadoGeneral = estado,
            vacunasPendientes = vacunasPendientes,
            tratamientosActivos = registrosPendientes,
            alertas = alertas,
            costoSanitarioTotal = costoRegistros + costoVacunas
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  INTEGRACIÓN FUENTES
    // ══════════════════════════════════════════════════════════════════

    fun obtenerLotesActivos(): Flow<List<Lote>> = avesRepo.getLotesActivos()

    fun obtenerProductosMedicos(): Flow<List<ProductoInventario>> =
        inventarioRepo.obtenerProductos().map { productos ->
            productos.filter { it.categoria == Categoria.MEDICINAS.name }
        }

    private suspend fun registrarGastoSanitarioEnFinanzas(registro: RegistroMedico) {
        finanzasRepo.agregarTransaccion(
            Transaccion(
                tipo        = TipoTransaccion.GASTO.name,
                categoria   = CategoriaGasto.MEDICINAS.name,
                descripcion = "Tratamiento: ${registro.medicamentoNombre} — ${registro.loteNombre}",
                monto       = registro.costo,
                estado      = EstadoPago.PAGADO.name,
                fechaMs     = registro.fechaMs,
                loteId      = registro.loteId,
                galponId    = registro.galponId,
                notas       = "Aves afectadas: ${registro.avesAfectadas} | ${registro.descripcion}"
            )
        )
    }
}
