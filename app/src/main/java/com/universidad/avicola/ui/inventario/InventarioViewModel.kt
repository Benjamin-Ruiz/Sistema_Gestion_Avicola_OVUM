package com.universidad.avicola.ui.inventario

import androidx.lifecycle.*
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.data.repository.InventarioFirestoreRepository
import kotlinx.coroutines.launch

class InventarioViewModel : ViewModel() {

    private val repository = InventarioFirestoreRepository()
    private fun currentUserId(): String =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

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

    private val _productosBase = repository.obtenerProductos().asLiveData()

    private val textoBusqueda = MutableLiveData("")
    private val categoriaFiltro = MutableLiveData<Categoria?>(null)
    private val soloStockCritico = MutableLiveData(false)
    private val soloProximosAVencer = MutableLiveData(false)
    private val precioMin = MutableLiveData<Double?>(null)
    private val precioMax = MutableLiveData<Double?>(null)
    private val ordenarPor = MutableLiveData("Nombre")

    private val _mensaje = MutableLiveData<String>()
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> = _cargando

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

    // En: app/src/main/java/com/universidad/avicola/ui/inventario/InventarioViewModel.kt

    fun agregarProducto(producto: ProductoInventario) {
        viewModelScope.launch {
            _cargando.value = true
            // ── CAMBIO: forzar userId antes de enviar ───────
            val productoConUid = producto.copy(userId = currentUserId())
            repository.agregarProducto(productoConUid).fold(
                onSuccess = {
                    _mensaje.value = "✓ Producto guardado en la nube"
                },
                onFailure = { error ->
                    _mensaje.value = "Error al guardar: ${error.localizedMessage}"
                }
            )
            _cargando.value = false
        }
    }

    fun actualizarProducto(actual: ProductoInventario, nuevo: ProductoInventario, razon: String) {
        viewModelScope.launch {
            _cargando.value = true
            // ── CAMBIO: forzar userId antes de enviar ───────
            val nuevoConUid = nuevo.copy(userId = currentUserId())
            repository.actualizarProducto(nuevoConUid).fold(
                onSuccess = {
                    _mensaje.value = "✓ Producto actualizado con éxito"
                },
                onFailure = { error ->
                    _mensaje.value = "Error al actualizar: ${error.localizedMessage}"
                }
            )
            _cargando.value = false
        }
    }

    fun eliminarProducto(id: String, nombre: String) {
        viewModelScope.launch {
            _cargando.value = true
            repository.eliminarProducto(id).fold(
                onSuccess = {
                    _mensaje.value = "✓ '$nombre' eliminado correctamente"
                },
                onFailure = { error ->
                    _mensaje.value = "Error al eliminar: ${error.localizedMessage}"
                }
            )
            _cargando.value = false
        }
    }
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
                criticosList.add("${p.nombre} (${p. cantidadConUnidad()})")
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
}