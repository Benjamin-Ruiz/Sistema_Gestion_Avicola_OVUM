package com.universidad.avicola.ui.inventario

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.data.repository.InventarioRepository
import kotlinx.coroutines.launch

/**
 * InventarioViewModel.kt — Versión Pro
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/inventario/
 */
class InventarioViewModel : ViewModel() {

    private val repository = InventarioRepository()

    // ── Datos crudos de Firestore ──────────────────
    val productos: LiveData<List<ProductoInventario>> =
        repository.obtenerProductos().asLiveData()

    // ── Estado de UI ───────────────────────────────
    private val _productosFiltrados = MutableLiveData<List<ProductoInventario>>()
    val productosFiltrados: LiveData<List<ProductoInventario>> = _productosFiltrados

    private val _mensaje = MutableLiveData<String>()
    val mensaje: LiveData<String> = _mensaje

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> = _cargando

    // ── Estado de filtros activos ──────────────────
    private var textoBusqueda = ""
    private var categoriaFiltro: Categoria? = null
    private var soloStockCritico = false
    private var soloProximosAVencer = false

    // ── Estadísticas para reportes ─────────────────
    val totalProductos: Int get() = productos.value?.size ?: 0
    val totalStockCritico: Int get() = productos.value?.count { it.isStockCritico() } ?: 0
    val totalProximosVencer: Int get() = productos.value?.count { it.isProximoAVencer() } ?: 0
    val valorTotalInventario: Double get() =
        productos.value?.sumOf { it.cantidad * it.precioUnitario } ?: 0.0

    // ════════════════════════════════════════════════
    //  FILTROS
    // ════════════════════════════════════════════════

    fun aplicarFiltros(lista: List<ProductoInventario>) {
        var resultado = lista

        if (textoBusqueda.isNotBlank()) {
            resultado = resultado.filter {
                it.nombre.lowercase().contains(textoBusqueda.lowercase())
            }
        }
        if (categoriaFiltro != null) {
            resultado = resultado.filter { it.categoria == categoriaFiltro!!.name }
        }
        if (soloStockCritico) {
            resultado = resultado.filter { it.isStockCritico() }
        }
        if (soloProximosAVencer) {
            resultado = resultado.filter { it.isProximoAVencer() || it.isVencido() }
        }

        // Ordenar: críticos primero, luego por nombre
        resultado = resultado.sortedWith(
            compareByDescending<ProductoInventario> { it.isStockCritico() }
                .thenByDescending { it.isProximoAVencer() }
                .thenBy { it.nombre }
        )

        _productosFiltrados.value = resultado
    }

    fun setBusqueda(texto: String) {
        textoBusqueda = texto
        aplicarFiltros(productos.value ?: emptyList())
    }

    fun setCategoria(categoria: Categoria?) {
        categoriaFiltro = categoria
        aplicarFiltros(productos.value ?: emptyList())
    }

    fun toggleStockCritico(activo: Boolean) {
        soloStockCritico = activo
        aplicarFiltros(productos.value ?: emptyList())
    }

    fun toggleProximosVencer(activo: Boolean) {
        soloProximosAVencer = activo
        aplicarFiltros(productos.value ?: emptyList())
    }

    fun limpiarFiltros() {
        textoBusqueda = ""
        categoriaFiltro = null
        soloStockCritico = false
        soloProximosAVencer = false
        aplicarFiltros(productos.value ?: emptyList())
    }

    // ════════════════════════════════════════════════
    //  CRUD
    // ════════════════════════════════════════════════

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

    fun actualizarProducto(
        actual: ProductoInventario,
        nuevo: ProductoInventario,
        reason: String = "Ajuste manual"
    ) {
        viewModelScope.launch {
            _cargando.value = true
            repository.actualizarProducto(actual, nuevo, reason).fold(
                onSuccess = { _mensaje.value = "✓ Producto actualizado" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun eliminarProducto(id: String, nombre: String) {
        viewModelScope.launch {
            _cargando.value = true
            repository.eliminarProducto(id, nombre).fold(
                onSuccess = { _mensaje.value = "Producto eliminado" },
                onFailure = { _mensaje.value = "Error: ${it.message}" }
            )
            _cargando.value = false
        }
    }

    fun obtenerLogsPorProducto(productoId: String) =
        repository.obtenerLogsPorProducto(productoId).asLiveData()
}
