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
    //  INTEGRACIÓN CON INVENTARIO
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

    /**
     * Verifica si el inventario tiene suficiente stock para cubrir
     * todos los insumos de la estimación ANTES de descontarlos.
     *
     * @return Lista de descripciones de los insumos con stock insuficiente.
     *         Si está vacía, el inventario es suficiente para activar la producción.
     */
    suspend fun verificarStockParaProduccion(
        estimacion: EstimacionCostos,
        productosActuales: List<ProductoInventario>
    ): List<String> {
        val insuficientes = mutableListOf<String>()

        // Verificar fases de alimentación
        estimacion.fases.forEach { fase ->
            if (fase.productoInventarioId.isEmpty()) return@forEach
            val producto = productosActuales.firstOrNull { it.id == fase.productoInventarioId }
                ?: return@forEach
            val consumoNecesario = fase.consumoTotalKg(estimacion.cantidadAves)
            if (producto.cantidad < consumoNecesario) {
                val falta = consumoNecesario - producto.cantidad
                insuficientes.add(
                    "• ${fase.nombre} (${fase.productoNombre.ifEmpty { producto.nombre }}): " +
                            "necesita ${String.format("%.1f", consumoNecesario)} ${producto.unitType}, " +
                            "disponible ${String.format("%.1f", producto.cantidad)} ${producto.unitType} " +
                            "(faltan ${String.format("%.1f", falta)} ${producto.unitType})"
                )
            }
        }

        // Verificar ítems sanitarios
        estimacion.itemsSanitarios.forEach { item ->
            if (item.productoInventarioId.isEmpty()) return@forEach
            val producto = productosActuales.firstOrNull { it.id == item.productoInventarioId }
                ?: return@forEach
            if (producto.cantidad < item.dosisParaLote) {
                val falta = item.dosisParaLote - producto.cantidad
                insuficientes.add(
                    "• ${item.nombre}: " +
                            "necesita ${String.format("%.1f", item.dosisParaLote)} ${producto.unitType}, " +
                            "disponible ${String.format("%.1f", producto.cantidad)} ${producto.unitType} " +
                            "(faltan ${String.format("%.1f", falta)} ${producto.unitType})"
                )
            }
        }

        return insuficientes
    }

    /**
     * Descuenta los insumos del inventario al activar la producción.
     * Solo descuenta los que tengan stock suficiente; los insuficientes
     * se omiten (deben haberse verificado antes con verificarStockParaProduccion).
     */
    suspend fun descontarInventarioParaProduccion(
        estimacion: EstimacionCostos,
        productosActuales: List<ProductoInventario>
    ): Result<Unit> {
        return try {
            estimacion.fases.forEach { fase ->
                if (fase.productoInventarioId.isEmpty()) return@forEach
                val producto = productosActuales.firstOrNull { it.id == fase.productoInventarioId }
                    ?: return@forEach
                val consumo    = fase.consumoTotalKg(estimacion.cantidadAves)
                val nuevoStock = producto.cantidad - consumo
                if (nuevoStock >= 0) {
                    inventarioRepo.actualizarProducto(
                        producto,
                        producto.copy(cantidad = nuevoStock),
                        "Activación de producción: ${estimacion.loteNombre}"
                    )
                }
            }
            estimacion.itemsSanitarios.forEach { item ->
                if (item.productoInventarioId.isEmpty()) return@forEach
                val producto = productosActuales.firstOrNull { it.id == item.productoInventarioId }
                    ?: return@forEach
                val nuevoStock = producto.cantidad - item.dosisParaLote
                if (nuevoStock >= 0) {
                    inventarioRepo.actualizarProducto(
                        producto,
                        producto.copy(cantidad = nuevoStock),
                        "Sanitario — Activación: ${estimacion.loteNombre}"
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  INTEGRACIÓN CON FINANZAS
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
        return guardarEstimacion(actualizada).fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    // ══════════════════════════════════════════════════════════════════
    //  INTEGRACIÓN CON AVES / LOTES
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