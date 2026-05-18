package com.universidad.avicola.ui.costos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.universidad.avicola.data.model.*
import com.universidad.avicola.data.repository.CostosCalculator
import com.universidad.avicola.data.repository.CostosRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * CostosViewModel.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/costos/
 *
 * Fix v2: nuevo método recargarInventario() para forzar refresh de productos
 * desde Firestore (con reconciliación de obsoletos) antes de mostrar spinners.
 */
class CostosViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = CostosRepository(app)

    // ── Listas observables ────────────────────────────────────────────────
    private val _estimaciones = MutableLiveData<List<EstimacionCostos>>(emptyList())
    val estimaciones: LiveData<List<EstimacionCostos>> = _estimaciones

    private val _lotes = MutableLiveData<List<Lote>>(emptyList())
    val lotes: LiveData<List<Lote>> = _lotes

    private val _productos = MutableLiveData<List<ProductoInventario>>(emptyList())
    val productos: LiveData<List<ProductoInventario>> = _productos

    // ── Estimación en edición ─────────────────────────────────────────────
    private val _estimacionActual = MutableLiveData<EstimacionCostos?>(null)
    val estimacionActual: LiveData<EstimacionCostos?> = _estimacionActual

    // ── Resultado de cálculo en tiempo real ───────────────────────────────
    private val _resultado = MutableLiveData<ResultadoCalculo?>(null)
    val resultado: LiveData<ResultadoCalculo?> = _resultado

    // ── Mensajes y estado ─────────────────────────────────────────────────
    private val _mensaje = MutableLiveData<String>("")
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData<Boolean>(false)
    val cargando: LiveData<Boolean> = _cargando

    private val _stockInsuficiente = MutableLiveData<List<String>?>(null)
    val stockInsuficiente: LiveData<List<String>?> = _stockInsuficiente

    // Estado específico del refresh de inventario (para mostrar progress en form)
    private val _sincronizandoInventario = MutableLiveData<Boolean>(false)
    val sincronizandoInventario: LiveData<Boolean> = _sincronizandoInventario

    // ── Filtros ───────────────────────────────────────────────────────────
    private val _estimacionesFiltradas = MutableLiveData<List<EstimacionCostos>>(emptyList())
    val estimacionesFiltradas: LiveData<List<EstimacionCostos>> = _estimacionesFiltradas

    private var filtroEstado: String? = null
    private var filtroLoteId: String? = null

    init {
        cargarEstimaciones()
        cargarLotes()
        cargarProductos()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CARGA DE DATOS
    // ═══════════════════════════════════════════════════════════════════════

    private fun cargarEstimaciones() {
        viewModelScope.launch {
            repo.obtenerEstimaciones().collectLatest { lista ->
                _estimaciones.value = lista
                aplicarFiltros(lista)
            }
        }
    }

    private fun cargarLotes() {
        viewModelScope.launch {
            repo.obtenerLotesActivos().collectLatest { _lotes.value = it }
        }
    }

    private fun cargarProductos() {
        viewModelScope.launch {
            repo.obtenerProductosInventario().collectLatest { _productos.value = it }
        }
    }

    /**
     * Fuerza un pull inmediato del inventario desde Firestore (con reconciliación
     * de borrados). El Flow ya colectándose se encargará de propagar los cambios
     * a la UI vía LiveData<List<ProductoInventario>>.
     *
     * Se debe llamar:
     *  - Al abrir CostosFormActivity (onCreate)
     *  - Al volver a primer plano (onResume), por si el usuario fue al módulo
     *    de Inventario a agregar/eliminar productos.
     */
    fun recargarInventario() {
        viewModelScope.launch {
            _sincronizandoInventario.value = true
            repo.forzarSincronizacionInventario()
                .onFailure {
                    // Sin red: los datos locales actuales seguirán mostrándose.
                    // No mostramos error intrusivo; es un refresh "best-effort".
                }
            _sincronizandoInventario.value = false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CÁLCULO EN TIEMPO REAL
    // ═══════════════════════════════════════════════════════════════════════

    fun recalcular(
        cantidadAves: Int,
        diasCrianza: Int,
        fases: List<FaseAlimentacion>,
        itemsSanitarios: List<ItemSanitario>,
        costosOperativos: List<CostoOperativo>,
        porcentajeMortalidad: Double,
        precioVentaUnitario: Double,
        costoAveInicial: Double = 0.0
    ) {
        if (cantidadAves <= 0) return
        val resultado = CostosCalculator.calcular(
            cantidadAves = cantidadAves,
            diasCrianza = diasCrianza,
            fases = fases,
            itemsSanitarios = itemsSanitarios,
            costosOperativos = costosOperativos,
            porcentajeMortalidad = porcentajeMortalidad,
            precioVentaUnitario = precioVentaUnitario,
            costoAveInicial = costoAveInicial
        )
        _resultado.value = resultado
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CRUD
    // ═══════════════════════════════════════════════════════════════════════

    fun guardarEstimacion(estimacion: EstimacionCostos) {
        viewModelScope.launch {
            _cargando.value = true
            // Enriquecer con precios reales antes de guardar
            val productos = _productos.value ?: emptyList()
            val fasesEnriquecidas = repo.enriquecerFasesConInventario(estimacion.fases, productos)
            val itemsEnriquecidos = repo.enriquecerItemsSanitariosConInventario(estimacion.itemsSanitarios, productos)

            // Recalcular resultado final con datos enriquecidos
            val resultado = CostosCalculator.calcular(
                cantidadAves = estimacion.cantidadAves,
                diasCrianza = estimacion.diasCrianza,
                fases = fasesEnriquecidas,
                itemsSanitarios = itemsEnriquecidos,
                costosOperativos = estimacion.costosOperativos,
                porcentajeMortalidad = estimacion.porcentajeMortalidad,
                precioVentaUnitario = estimacion.precioVentaUnitario
            )

            val estimacionFinal = estimacion.copy(
                fases = fasesEnriquecidas,
                itemsSanitarios = itemsEnriquecidos,
                costoAlimentacionTotal = resultado.costoAlimentacion,
                costoSanitarioTotal = resultado.costoSanitario,
                costoOperativoTotal = resultado.costoOperativo,
                perdidaMortalidad = resultado.perdidaMortalidad,
                costoTotal = resultado.costoTotal,
                costoPorAve = resultado.costoPorAve,
                ingresoEstimado = resultado.ingresoEstimado,
                gananciaNeta = resultado.gananciaNeta,
                roi = resultado.roi,
                puntoEquilibrioUnidades = resultado.puntoEquilibrioUnidades,
                alertas = resultado.alertas
            )

            repo.guardarEstimacion(estimacionFinal)
                .onSuccess { _mensaje.value = "Estimación guardada correctamente" }
                .onFailure { _mensaje.value = "Error al guardar: ${it.message}" }
            _cargando.value = false
        }
    }

    fun eliminarEstimacion(id: String) {
        viewModelScope.launch {
            repo.eliminarEstimacion(id)
                .onSuccess { _mensaje.value = "Estimación eliminada" }
                .onFailure { _mensaje.value = "Error al eliminar" }
        }
    }

    fun duplicarEstimacion(id: String) {
        viewModelScope.launch {
            repo.duplicarEstimacion(id)
                .onSuccess { _mensaje.value = "Estimación duplicada" }
                .onFailure { _mensaje.value = "Error al duplicar" }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTEGRACIÓN FINANZAS
    // ═══════════════════════════════════════════════════════════════════════

    fun enviarCostoAFinanzas(estimacion: EstimacionCostos) {
        viewModelScope.launch {
            repo.enviarCostoAFinanzas(estimacion)
                .onSuccess { _mensaje.value = "Costo enviado al módulo de Finanzas como gasto proyectado" }
                .onFailure { _mensaje.value = "Error al enviar a Finanzas: ${it.message}" }
        }
    }

    fun registrarCostoReal(estimacion: EstimacionCostos, costoReal: Double) {
        viewModelScope.launch {
            repo.actualizarCostoReal(estimacion, costoReal)
                .onSuccess { _mensaje.value = "Costo real registrado. Estimación completada." }
                .onFailure { _mensaje.value = "Error al registrar costo real" }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTEGRACIÓN INVENTARIO
    // ═══════════════════════════════════════════════════════════════════════

    fun limpiarVerificacionStock() {
        _stockInsuficiente.value = null
    }

    fun verificarStockParaActivar(estimacion: EstimacionCostos) {
        val insuficientes = mutableListOf<String>()
        val productos = _productos.value ?: emptyList()

        estimacion.fases.forEach { fase ->
            if (fase.productoInventarioId.isNotEmpty()) {
                val p = productos.firstOrNull { it.id == fase.productoInventarioId }
                val stock = p?.cantidad ?: 0.0
                val consumo = fase.consumoTotalKg(estimacion.cantidadAves)
                if (stock < consumo) {
                    insuficientes.add("${fase.productoNombre}: faltan ${String.format("%.2f", consumo - stock)} kg")
                }
            }
        }

        estimacion.itemsSanitarios.forEach { item ->
            if (item.productoInventarioId.isNotEmpty()) {
                val p = productos.firstOrNull { it.id == item.productoInventarioId }
                val stock = p?.cantidad ?: 0.0
                if (stock < item.dosisParaLote) {
                    insuficientes.add("${item.nombre}: faltan ${String.format("%.2f", item.dosisParaLote - stock)} unidades")
                }
            }
        }
        _stockInsuficiente.value = insuficientes
    }

    fun activarProduccion(estimacion: EstimacionCostos, forzar: Boolean = false) {
        viewModelScope.launch {
            _cargando.value = true
            val productos = _productos.value ?: emptyList()
            repo.descontarInventarioParaProduccion(estimacion, productos)
                .onSuccess {
                    guardarEstimacion(estimacion.copy(estado = EstadoEstimacion.ACTIVA.name))
                    _mensaje.value = "Producción activada. Inventario actualizado."
                }
                .onFailure { _mensaje.value = "Error al activar producción: ${it.message}" }
            _cargando.value = false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FILTROS
    // ═══════════════════════════════════════════════════════════════════════

    fun setFiltroEstado(estado: String?) {
        filtroEstado = estado
        aplicarFiltros(_estimaciones.value ?: emptyList())
    }

    fun setFiltroLote(loteId: String?) {
        filtroLoteId = loteId
        aplicarFiltros(_estimaciones.value ?: emptyList())
    }

    fun aplicarFiltros(lista: List<EstimacionCostos>) {
        var filtrada = lista
        filtroEstado?.let { e -> filtrada = filtrada.filter { it.estado == e } }
        filtroLoteId?.let { l -> filtrada = filtrada.filter { it.loteId == l } }
        _estimacionesFiltradas.value = filtrada
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HELPERS UI
    // ═══════════════════════════════════════════════════════════════════════

    fun iniciarNuevaEstimacion(): EstimacionCostos {
        val primeraFases = CostosCalculator.fasesDefault(TipoAveEstimacion.ENGORDE)
        val costosOp = CostosCalculator.costosOperativosDefault()
        return EstimacionCostos(
            fases = primeraFases,
            costosOperativos = costosOp
        )
    }

    fun fasesDefaultParaTipo(tipo: TipoAveEstimacion): List<FaseAlimentacion> =
        CostosCalculator.fasesDefault(tipo)

    /** Métricas resumen para la pantalla principal */
    data class MetricasResumen(
        val totalEstimaciones: Int,
        val roiPromedio: Double,
        val costoTotalAcumulado: Double,
        val estimacionesRentables: Int
    )

    fun calcularMetricas(lista: List<EstimacionCostos>): MetricasResumen {
        val activas = lista.filter { it.estado != EstadoEstimacion.ARCHIVADA.name }
        return MetricasResumen(
            totalEstimaciones = activas.size,
            roiPromedio = if (activas.isEmpty()) 0.0 else activas.map { it.roi }.average(),
            costoTotalAcumulado = activas.sumOf { it.costoTotal },
            estimacionesRentables = activas.count { it.isRentable() }
        )
    }
}