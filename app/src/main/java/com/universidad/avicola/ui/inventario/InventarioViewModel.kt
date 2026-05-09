package com.universidad.avicola.ui.inventario

import android.app.Application
import androidx.lifecycle.*
import androidx.lifecycle.asLiveData
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.data.repository.InventarioRepository
import kotlinx.coroutines.launch

class InventarioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InventarioRepository(application)

    // Clase para el reporte
    data class InventarioReporte(
        val totalProductos: Int = 0,
        val valorTotal: Double = 0.0,
        val stockCritico: Int = 0,
        val porVencer: Int = 0,
        val vencidos: Int = 0,
        val desgloseCategorias: Map<String, Int> = emptyMap(),
        val listaCriticos: List<String> = emptyList(),
        val listaPorVencer: List<String> = emptyList(),
    )

    private val _reporte = MediatorLiveData<InventarioReporte>(InventarioReporte())
    val reporte: LiveData<InventarioReporte> = _reporte

    // Datos de origen
    private val _productosBase = repository.obtenerProductos().asLiveData()
    
    // Estados de filtros
    private val textoBusqueda = MutableLiveData("")
    private val categoriaFiltro = MutableLiveData<Categoria?>(null)
    private val soloStockCritico = MutableLiveData(false)
    private val soloProximosAVencer = MutableLiveData(false)
    
    // Filtros Avanzados
    private val precioMin = MutableLiveData<Double?>(null)
    private val precioMax = MutableLiveData<Double?>(null)
    private val ordenarPor = MutableLiveData("Nombre")

    private val _mensaje = MutableLiveData<String>()
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> = _cargando

    // LiveData Mediador: Se actualiza automáticamente si cualquiera de los filtros o la base de datos cambia
    val productosFiltrados = MediatorLiveData<List<ProductoInventario>>().apply {
        val observer = Observer<Any?> {
            val lista = _productosBase.value ?: emptyList()
            value = filtrar(lista)
            _reporte.value = calcularReporte(lista)
        }
        addSource(_productosBase, observer)
        addSource(textoBusqueda, observer)
        addSource(categoriaFiltro, observer)
        addSource(soloStockCritico, observer)
        addSource(soloProximosAVencer, observer)
        addSource(precioMin, observer)
        addSource(precioMax, observer)
        addSource(ordenarPor, observer)
    }

    private fun calcularReporte(lista: List<ProductoInventario>): InventarioReporte {
        var valor = 0.0
        var critico = 0
        var porVencer = 0
        var vencidos = 0
        val categorias = mutableMapOf<String, Int>()
        val criticosList = mutableListOf<String>()
        val vencerList = mutableListOf<String>()

        lista.forEach { p ->
            valor += (p.cantidad * p.precioUnitario)
            if (p.isStockCritico()) {
                critico++
                criticosList.add("${p.nombre} (${p.cantidadConUnidad()})")
            }
            if (p.isVencido()) vencidos++
            else if (p.isProximoAVencer()) {
                porVencer++
                vencerList.add(p.nombre)
            }
            categorias[p.categoria] = (categorias[p.categoria] ?: 0) + 1
        }

        return InventarioReporte(
            totalProductos = lista.size,
            valorTotal = valor,
            stockCritico = critico,
            porVencer = porVencer,
            vencidos = vencidos,
            desgloseCategorias = categorias,
            listaCriticos = criticosList,
            listaPorVencer = vencerList
        )
    }

    private fun filtrar(lista: List<ProductoInventario>): List<ProductoInventario> {
        val query = textoBusqueda.value?.lowercase() ?: ""
        val cat = categoriaFiltro.value
        val critico = soloStockCritico.value ?: false
        val vencen = soloProximosAVencer.value ?: false
        val pMin = precioMin.value
        val pMax = precioMax.value
        val orden = ordenarPor.value

        val filtrada = lista.filter {
            (query.isEmpty() || it.nombre.lowercase().contains(query)) &&
            (cat == null || it.categoria == cat.name) &&
            (!critico || it.isStockCritico()) &&
            (!vencen || it.isProximoAVencer() || it.isVencido()) &&
            (pMin == null || it.precioUnitario >= pMin) &&
            (pMax == null || it.precioUnitario <= pMax)
        }

        return when (orden) {
            "PrecioMenor" -> filtrada.sortedBy { it.precioUnitario }
            "PrecioMayor" -> filtrada.sortedByDescending { it.precioUnitario }
            "Cantidad" -> filtrada.sortedByDescending { it.cantidad }
            else -> filtrada.sortedBy { it.nombre }
        }
    }

    // Funciones para actualizar filtros
    fun setBusqueda(texto: String) { textoBusqueda.value = texto }
    fun setCategoria(categoria: Categoria?) { categoriaFiltro.value = categoria }
    fun toggleStockCritico(activo: Boolean) { soloStockCritico.value = activo }
    fun toggleProximosVencer(activo: Boolean) { soloProximosAVencer.value = activo }

    fun aplicarFiltrosAvanzados(min: Double?, max: Double?, orden: String) {
        precioMin.value = min
        precioMax.value = max
        ordenarPor.value = orden
    }

    fun limpiarFiltrosAvanzados() {
        precioMin.value = null
        precioMax.value = null
        ordenarPor.value = "Nombre"
    }

    // CRUD
    fun agregarProducto(producto: ProductoInventario) {
        viewModelScope.launch {
            _cargando.value = true
            repository.agregarProducto(producto).fold(
                onSuccess = { _mensaje.value = "✓ Producto guardado" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun actualizarProducto(actual: ProductoInventario, nuevo: ProductoInventario, razon: String) {
        viewModelScope.launch {
            _cargando.value = true
            repository.actualizarProducto(actual, nuevo, razon).fold(
                onSuccess = { _mensaje.value = "✓ Producto actualizado" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun eliminarProducto(id: String, nombre: String) {
        viewModelScope.launch {
            repository.eliminarProducto(id, nombre)
        }
    }

    fun obtenerLogsPorProducto(productoId: String) =
        repository.obtenerLogsPorProducto(productoId).asLiveData()
}
