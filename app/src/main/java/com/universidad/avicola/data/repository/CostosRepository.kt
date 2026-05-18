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
 * Repositorio del módulo Estimación de Costos.
 * - Persistencia local con Room
 * - Integración con InventarioRepository (precios reales)
 * - Integración con FinanzasRepository (envío de costos proyectados)
 * - Integración con AvesRepository (lotes activos)
 *
 * Fix v2: expone forzarSincronizacionInventario() para que el formulario
 * pueda solicitar un refresh inmediato antes de mostrar los spinners.
 */
class CostosRepository(context: Context) {

    private val app = context.applicationContext as AvicolaApp
    private val dao = app.database.estimacionCostosDao()

    private val inventarioRepo = InventarioRepository(context)
    private val finanzasRepo = FinanzasRepository()
    private val avesRepo = AvesRepository(context)

    // ═══════════════════════════════════════════════════════════════════════
    //  CRUD Estimaciones
    // ═══════════════════════════════════════════════════════════════════════

    fun obtenerEstimaciones(): Flow<List<EstimacionCostos>> =
        dao.getAllFlow().map { it.map { e -> e.toDomain() } }

    fun obtenerPorLote(loteId: String): Flow<List<EstimacionCostos>> =
        dao.getPorLote(loteId).map { it.map { e -> e.toDomain() } }

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

    /** Duplica una estimación existente con nuevo ID y estado BORRADOR */
    suspend fun duplicarEstimacion(id: String): Result<String> {
        val original = dao.getById(id)?.toDomain() ?: return Result.failure(Exception("No encontrada"))
        val copia = original.copy(
            id = UUID.randomUUID().toString(),
            estado = EstadoEstimacion.BORRADOR.name,
            loteNombre = "${original.loteNombre} (Copia)",
            fechaCreacion = System.currentTimeMillis(),
            costoRealRegistrado = 0.0,
            variacionPorcentaje = 0.0
        )
        return guardarEstimacion(copia)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Integración con INVENTARIO
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Obtiene productos del inventario filtrando por categoría.
     * Se usa para autocompletar fases y artículos sanitarios.
     */
    fun obtenerProductosInventario(): Flow<List<ProductoInventario>> =
        inventarioRepo.obtenerProductos()

    /**
     * Fuerza una sincronización inmediata del inventario con Firestore.
     * Útil para llamar al abrir el formulario de Costos y garantizar que los
     * spinners reflejen el estado actual (sin productos fantasma de syncs antiguos).
     */
    suspend fun forzarSincronizacionInventario(): Result<Unit> =
        inventarioRepo.forzarSincronizacionRemota()

    /**
     * Carga el precio y stock real de cada fase desde el inventario.
     * Llamar antes de mostrar el formulario de edición.
     */
    suspend fun enriquecerFasesConInventario(
        fases: List<FaseAlimentacion>,
        productos: List<ProductoInventario>
    ): List<FaseAlimentacion> {
        return fases.map { fase ->
            if (fase.productoInventarioId.isNotEmpty()) {
                val producto = productos.firstOrNull { it.id == fase.productoInventarioId }
                if (producto != null) {
                    fase.copy(
                        precioKg = producto.precioUnitario,
                        productoNombre = producto.nombre,
                        stockDisponible = producto.cantidad
                    )
                } else fase
            } else fase
        }
    }

    suspend fun enriquecerItemsSanitariosConInventario(
        items: List<ItemSanitario>,
        productos: List<ProductoInventario>
    ): List<ItemSanitario> {
        return items.map { item ->
            if (item.productoInventarioId.isNotEmpty()) {
                val producto = productos.firstOrNull { it.id == item.productoInventarioId }
                if (producto != null) {
                    item.copy(
                        precioUnitario = producto.precioUnitario,
                        nombre = producto.nombre,
                        stockDisponible = producto.cantidad
                    )
                } else item
            } else item
        }
    }

    /**
     * Descuenta productos del inventario cuando una estimación
     * se convierte en producción real.
     */
    suspend fun descontarInventarioParaProduccion(
        estimacion: EstimacionCostos,
        productosActuales: List<ProductoInventario>
    ): Result<Unit> {
        return try {
            estimacion.fases.forEach { fase ->
                if (fase.productoInventarioId.isNotEmpty()) {
                    val producto = productosActuales.firstOrNull { it.id == fase.productoInventarioId }
                    if (producto != null) {
                        val consumo = fase.consumoTotalKg(estimacion.cantidadAves)
                        val nuevoStock = producto.cantidad - consumo
                        if (nuevoStock >= 0) {
                            inventarioRepo.actualizarProducto(
                                producto,
                                producto.copy(cantidad = nuevoStock),
                                "Descuento por estimación: ${estimacion.loteNombre}"
                            )
                        }
                    }
                }
            }
            estimacion.itemsSanitarios.forEach { item ->
                if (item.productoInventarioId.isNotEmpty()) {
                    val producto = productosActuales.firstOrNull { it.id == item.productoInventarioId }
                    if (producto != null) {
                        val nuevoStock = producto.cantidad - item.dosisParaLote
                        if (nuevoStock >= 0) {
                            inventarioRepo.actualizarProducto(
                                producto,
                                producto.copy(cantidad = nuevoStock),
                                "Sanitario por estimación: ${estimacion.loteNombre}"
                            )
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Integración con FINANZAS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Envía el costo total estimado a Finanzas como transacción proyectada.
     * Crea un gasto con estado PENDIENTE para comparar con el real después.
     */
    suspend fun enviarCostoAFinanzas(estimacion: EstimacionCostos): Result<String> {
        val transaccion = Transaccion(
            tipo = TipoTransaccion.GASTO.name,
            categoria = CategoriaGasto.OTRO_GASTO.name,
            descripcion = "Estimación de costos: ${estimacion.loteNombre} (${estimacion.cantidadAves} aves / ${estimacion.diasCrianza} días)",
            monto = estimacion.costoTotal,
            estado = EstadoPago.PENDIENTE.name,
            fechaMs = System.currentTimeMillis(),
            loteId = estimacion.loteId,
            notas = "ROI estimado: ${String.format("%.1f", estimacion.roi)}% | Ganancia estimada: Q${String.format("%.2f", estimacion.gananciaNeta)}"
        )
        return finanzasRepo.agregarTransaccion(transaccion)
    }

    /**
     * Compara el costo estimado con las transacciones reales de ese lote en Finanzas.
     * Retorna la variación porcentual.
     */
    suspend fun actualizarCostoReal(estimacion: EstimacionCostos, costoReal: Double): Result<Unit> {
        val variacion = if (estimacion.costoTotal > 0)
            ((costoReal - estimacion.costoTotal) / estimacion.costoTotal) * 100
        else 0.0
        val actualizada = estimacion.copy(
            costoRealRegistrado = costoReal,
            variacionPorcentaje = variacion,
            estado = EstadoEstimacion.COMPLETADA.name
        )
        return guardarEstimacion(actualizada).map { }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Integración con AVES / LOTES
    // ═══════════════════════════════════════════════════════════════════════

    fun obtenerLotesActivos(): Flow<List<Lote>> = avesRepo.getLotesActivos()

    /**
     * Recalcula la pérdida económica cuando se registra mortalidad en un lote
     * que tiene una estimación activa vinculada.
     */
    suspend fun recalcularPorMortalidad(
        loteId: String,
        nuevaMortalidadRegistrada: Int,
        costoPorAve: Double
    ): Result<Unit> {
        return try {
            val estimaciones = dao.getPorLote(loteId)
            // Se resuelve en el ViewModel; aquí solo registramos la pérdida en Finanzas
            finanzasRepo.registrarPerdidaMortalidad(
                galponId = loteId,
                cantidadBajas = nuevaMortalidadRegistrada,
                costoPromedioAve = costoPorAve,
                motivo = "Mortalidad registrada en lote"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}