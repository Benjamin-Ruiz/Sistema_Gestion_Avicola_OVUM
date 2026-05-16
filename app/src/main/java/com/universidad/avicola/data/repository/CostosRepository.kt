package com.universidad.avicola.data.repository

import android.content.Context
import com.universidad.avicola.AvicolaApp
import com.universidad.avicola.data.local.entities.EstimacionCostosEntity
import com.universidad.avicola.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * CostosRepository.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
 *
 * CORRECCIÓN aplicada:
 * - actualizarCostoReal usaba .map { } (Result<String>.map no existe en Kotlin stdlib
 *   para Result<T>) — reemplazado con fold() explícito que devuelve Result<Unit>.
 */
class CostosRepository(context: Context) {

    private val app = context.applicationContext as AvicolaApp
    private val dao = app.database.estimacionCostosDao()

    private val inventarioRepo = InventarioRepository(context)
    private val finanzasRepo   = FinanzasRepository()
    private val avesRepo       = AvesRepository(context)

    // ══════════════════════════════════════════════════════════════════
    //  CRUD Estimaciones
    // ══════════════════════════════════════════════════════════════════

    fun obtenerEstimaciones(): Flow<List<EstimacionCostos>> =
        dao.getAllFlow().map { lista -> lista.map { it.toDomain() } }

    fun obtenerPorLote(loteId: String): Flow<List<EstimacionCostos>> =
        dao.getPorLote(loteId).map { lista -> lista.map { it.toDomain() } }

    suspend fun guardarEstimacion(estimacion: EstimacionCostos): Result<String> {
        return try {
            val id = if (estimacion.id.isEmpty()) UUID.randomUUID().toString() else estimacion.id
            dao.insert(EstimacionCostosEntity.fromDomain(estimacion.copy(id = id)))
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun eliminarEstimacion(id: String): Result<Unit> {
        return try {
            dao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun duplicarEstimacion(id: String): Result<String> {
        val original = dao.getById(id)?.toDomain()
            ?: return Result.failure(Exception("No encontrada"))
        val copia = original.copy(
            id                  = UUID.randomUUID().toString(),
            estado              = EstadoEstimacion.BORRADOR.name,
            loteNombre          = "${original.loteNombre} (Copia)",
            fechaCreacion       = System.currentTimeMillis(),
            costoRealRegistrado = 0.0,
            variacionPorcentaje = 0.0
        )
        return guardarEstimacion(copia)
    }

    // ══════════════════════════════════════════════════════════════════
    //  Integración con INVENTARIO
    // ══════════════════════════════════════════════════════════════════

    fun obtenerProductosInventario(): Flow<List<ProductoInventario>> =
        inventarioRepo.obtenerProductos()

    suspend fun enriquecerFasesConInventario(
        fases: List<FaseAlimentacion>,
        productos: List<ProductoInventario>
    ): List<FaseAlimentacion> = fases.map { fase ->
        if (fase.productoInventarioId.isEmpty()) return@map fase
        val producto = productos.firstOrNull { it.id == fase.productoInventarioId }
            ?: return@map fase
        fase.copy(
            precioKg        = producto.precioUnitario,
            productoNombre  = producto.nombre,
            stockDisponible = producto.cantidad
        )
    }

    suspend fun enriquecerItemsSanitariosConInventario(
        items: List<ItemSanitario>,
        productos: List<ProductoInventario>
    ): List<ItemSanitario> = items.map { item ->
        if (item.productoInventarioId.isEmpty()) return@map item
        val producto = productos.firstOrNull { it.id == item.productoInventarioId }
            ?: return@map item
        item.copy(
            precioUnitario  = producto.precioUnitario,
            nombre          = producto.nombre,
            stockDisponible = producto.cantidad
        )
    }

    suspend fun descontarInventarioParaProduccion(
        estimacion: EstimacionCostos,
        productosActuales: List<ProductoInventario>
    ): Result<Unit> {
        return try {
            estimacion.fases.forEach { fase ->
                if (fase.productoInventarioId.isEmpty()) return@forEach
                val producto = productosActuales.firstOrNull { it.id == fase.productoInventarioId }
                    ?: return@forEach
                val nuevoStock = producto.cantidad - fase.consumoTotalKg(estimacion.cantidadAves)
                if (nuevoStock >= 0.0) {
                    inventarioRepo.actualizarProducto(
                        producto,
                        producto.copy(cantidad = nuevoStock),
                        "Descuento por estimación: ${estimacion.loteNombre}"
                    )
                }
            }
            estimacion.itemsSanitarios.forEach { item ->
                if (item.productoInventarioId.isEmpty()) return@forEach
                val producto = productosActuales.firstOrNull { it.id == item.productoInventarioId }
                    ?: return@forEach
                val nuevoStock = producto.cantidad - item.dosisParaLote
                if (nuevoStock >= 0.0) {
                    inventarioRepo.actualizarProducto(
                        producto,
                        producto.copy(cantidad = nuevoStock),
                        "Sanitario por estimación: ${estimacion.loteNombre}"
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Integración con FINANZAS
    // ══════════════════════════════════════════════════════════════════

    suspend fun enviarCostoAFinanzas(estimacion: EstimacionCostos): Result<String> {
        val transaccion = Transaccion(
            tipo        = TipoTransaccion.GASTO.name,
            categoria   = CategoriaGasto.OTRO_GASTO.name,
            descripcion = "Estimación de costos: ${estimacion.loteNombre} " +
                          "(${estimacion.cantidadAves} aves / ${estimacion.diasCrianza} días)",
            monto       = estimacion.costoTotal,
            estado      = EstadoPago.PENDIENTE.name,
            fechaMs     = System.currentTimeMillis(),
            loteId      = estimacion.loteId,
            notas       = "ROI estimado: ${String.format("%.1f", estimacion.roi)}% | " +
                          "Ganancia estimada: Q${String.format("%.2f", estimacion.gananciaNeta)}"
        )
        return finanzasRepo.agregarTransaccion(transaccion)
    }

    suspend fun actualizarCostoReal(
        estimacion: EstimacionCostos,
        costoReal: Double
    ): Result<Unit> {
        val variacion = if (estimacion.costoTotal > 0.0)
            ((costoReal - estimacion.costoTotal) / estimacion.costoTotal) * 100.0
        else 0.0
        val actualizada = estimacion.copy(
            costoRealRegistrado = costoReal,
            variacionPorcentaje = variacion,
            estado              = EstadoEstimacion.COMPLETADA.name
        )
        // CORRECCIÓN: Result<T> de Kotlin stdlib no tiene .map{}.
        // Usamos fold() para convertir Result<String> → Result<Unit>.
        return guardarEstimacion(actualizada).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  Integración con AVES / LOTES
    // ══════════════════════════════════════════════════════════════════

    fun obtenerLotesActivos(): Flow<List<Lote>> = avesRepo.getLotesActivos()

    suspend fun recalcularPorMortalidad(
        loteId: String,
        nuevaMortalidadRegistrada: Int,
        costoPorAve: Double
    ): Result<Unit> {
        return try {
            finanzasRepo.registrarPerdidaMortalidad(
                galponId         = loteId,
                cantidadBajas    = nuevaMortalidadRegistrada,
                costoPromedioAve = costoPorAve,
                motivo           = "Mortalidad registrada en lote"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
