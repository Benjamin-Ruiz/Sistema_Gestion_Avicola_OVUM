package com.universidad.avicola.ui.finanzas

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.universidad.avicola.data.model.CategoriaGasto
import com.universidad.avicola.data.model.EstadoPago
import com.universidad.avicola.data.model.PuntoEquilibrio
import com.universidad.avicola.data.model.ResumenFinanciero
import com.universidad.avicola.data.model.Transaccion
import com.universidad.avicola.data.model.TipoTransaccion
import com.universidad.avicola.data.repository.FinanzasRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import android.net.Uri

class FinanzasViewModel : ViewModel() {

    private val repository = FinanzasRepository()

    val todasLasTransacciones: LiveData<List<Transaccion>> =
        repository.obtenerTransacciones().asLiveData()

    val cuentasPendientes: LiveData<List<Transaccion>> =
        repository.obtenerCuentasPendientes().asLiveData()

    private val _transaccionesFiltradas = MutableLiveData<List<Transaccion>>()
    val transaccionesFiltradas: LiveData<List<Transaccion>> = _transaccionesFiltradas

    private val _resumen = MutableLiveData<ResumenFinanciero>()
    val resumen: LiveData<ResumenFinanciero> = _resumen

    private val _puntoEquilibrio = MutableLiveData<PuntoEquilibrio>()
    val puntoEquilibrio: LiveData<PuntoEquilibrio> = _puntoEquilibrio

    private val _mensaje = MutableLiveData<String>()
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> = _cargando

    var limiteGastosMensual: Double = 50000.0
    var totalAves: Int = 0
    var costoPromedioAve: Double = 0.0

    private var filtroTipo: String? = null
    private var filtroCategoria: String? = null
    private var filtroEstado: String? = null
    private var filtroMes: Int? = null
    private var filtroAnio: Int? = null

    // ── Resumen ──────────────────────────────────
    fun calcularResumen(lista: List<Transaccion>) {
        val cal = Calendar.getInstance()
        val mesActual = cal.get(Calendar.MONTH)
        val anioActual = cal.get(Calendar.YEAR)

        val delMes = lista.filter { t ->
            val c = Calendar.getInstance().apply { timeInMillis = t.fechaMs }
            c.get(Calendar.MONTH) == mesActual && c.get(Calendar.YEAR) == anioActual
        }

        val ingresos = delMes.filter { it.isIngreso() && it.estado == EstadoPago.PAGADO.name }
            .sumOf { it.monto }
        val gastos = delMes.filter { it.isGasto() && it.estado == EstadoPago.PAGADO.name }
            .sumOf { it.monto }
        val beneficio = ingresos - gastos
        val roi = if (gastos > 0) (beneficio / gastos) * 100 else 0.0
        val costoPorAve = if (totalAves > 0) gastos / totalAves else 0.0

        val porCobrar = lista.filter { it.isIngreso() && (it.isPendiente() || it.isParcial()) }
            .sumOf { it.montoPendiente() }
        val porPagar = lista.filter { it.isGasto() && (it.isPendiente() || it.isParcial()) }
            .sumOf { it.montoPendiente() }

        _resumen.value = ResumenFinanciero(
            ingresoTotal = ingresos,
            gastoTotal = gastos,
            beneficioNeto = beneficio,
            roi = roi,
            costoPorAve = costoPorAve,
            totalPorCobrar = porCobrar,
            totalPorPagar = porPagar,
            alertaGastosAltos = gastos > limiteGastosMensual,
            limiteGastosMensual = limiteGastosMensual
        )
    }

    // ── Filtros ──────────────────────────────────
    fun aplicarFiltros(lista: List<Transaccion>) {
        var resultado = lista

        filtroTipo?.let { tipo -> resultado = resultado.filter { it.tipo == tipo } }
        filtroCategoria?.let { cat -> resultado = resultado.filter { it.categoria == cat } }
        filtroEstado?.let { estado -> resultado = resultado.filter { it.estado == estado } }
        filtroMes?.let { mes ->
            val anio = filtroAnio ?: Calendar.getInstance().get(Calendar.YEAR)
            resultado = resultado.filter { t ->
                val c = Calendar.getInstance().apply { timeInMillis = t.fechaMs }
                c.get(Calendar.MONTH) == mes && c.get(Calendar.YEAR) == anio
            }
        }

        _transaccionesFiltradas.value = resultado.sortedByDescending { it.fechaMs }
        calcularResumen(resultado)
    }

    fun setFiltroTipo(tipo: String?) { filtroTipo = tipo }
    fun setFiltroEstado(estado: String?) { filtroEstado = estado }
    fun setFiltroMes(mes: Int?, anio: Int?) {
        filtroMes = mes
        filtroAnio = anio
        aplicarFiltros(todasLasTransacciones.value ?: emptyList())
    }

    fun limpiarFiltros() {
        filtroTipo = null
        filtroCategoria = null
        filtroEstado = null
        filtroMes = null
        filtroAnio = null
        aplicarFiltros(todasLasTransacciones.value ?: emptyList())
    }

    // ── Punto de equilibrio ──────────────────────
    fun calcularPuntoEquilibrio(
        gastosFijos: Double,
        precioVenta: Double,
        costoVariable: Double,
        tipoUnidad: String = "aves"
    ) {
        val margenUnitario = precioVenta - costoVariable
        val unidades = if (margenUnitario > 0) gastosFijos / margenUnitario else 0.0
        val ingresoNecesario = unidades * precioVenta

        _puntoEquilibrio.value = PuntoEquilibrio(
            gastosFijos = gastosFijos,
            precioVentaUnitario = precioVenta,
            costoVariableUnitario = costoVariable,
            unidadesNecesarias = unidades,
            tipoUnidad = tipoUnidad,
            ingresoNecesario = ingresoNecesario
        )
    }

    // ── CRUD ─────────────────────────────────────
    fun agregarTransaccion(t: Transaccion) {
        viewModelScope.launch {
            _cargando.value = true
            repository.agregarTransaccion(t).fold(
                onSuccess = { _mensaje.value = "✓ Transacción guardada" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun actualizarTransaccion(t: Transaccion) {
        viewModelScope.launch {
            _cargando.value = true
            repository.actualizarTransaccion(t).fold(
                onSuccess = { _mensaje.value = "✓ Transacción actualizada" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun eliminarTransaccion(id: String) {
        viewModelScope.launch {
            _cargando.value = true
            repository.eliminarTransaccion(id).fold(
                onSuccess = { _mensaje.value = "Transacción eliminada" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun marcarComoPagado(transaccion: Transaccion) {
        val actualizada = transaccion.copy(
            estado = EstadoPago.PAGADO.name,
            montoPagado = transaccion.monto
        )
        actualizarTransaccion(actualizada)
    }

    // ── Estadísticas ─────────────────────────────
    fun gastosPorCategoria(lista: List<Transaccion>): Map<String, Double> {
        return lista.filter { it.isGasto() && it.estado == EstadoPago.PAGADO.name }
            .groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.monto } }
    }

    fun ingresosPorCategoria(lista: List<Transaccion>): Map<String, Double> {
        return lista.filter { it.isIngreso() && it.estado == EstadoPago.PAGADO.name }
            .groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.monto } }
    }

    // ── Integración Inventario ───────────────────
    fun sugerirGastoDesdeInventario(
        nombreProducto: String,
        monto: Double,
        productoId: String,
        categoria: CategoriaGasto = CategoriaGasto.ALIMENTO
    ) {
        viewModelScope.launch {
            repository.sugerirGastoDesdeInventario(nombreProducto, monto, productoId, categoria)
        }
    }
    suspend fun subirFotoRecibo(uri: Uri): Result<String> {
        return repository.subirFotoRecibo(uri)
    }

    // ── Integración Galpones ─────────────────────
    fun registrarPerdidaMortalidad(
        galponId: String,
        cantidadBajas: Int,
        motivo: String
    ) {
        if (costoPromedioAve <= 0) {
            _mensaje.value = "Configure el costo promedio por ave primero"
            return
        }
        viewModelScope.launch {
            repository.registrarPerdidaMortalidad(
                galponId, cantidadBajas, costoPromedioAve, motivo
            ).fold(
                onSuccess = {
                    val perdida = cantidadBajas * costoPromedioAve
                    _mensaje.value = "Pérdida registrada: Q${String.format("%.2f", perdida)}"
                },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
        }
    }
}