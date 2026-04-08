package com.universidad.avicola.ui.inventario

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.universidad.avicola.R
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.databinding.ActivityInventarioBinding
import com.universidad.avicola.databinding.DialogProductoBinding
import com.universidad.avicola.databinding.DialogHistorialBinding
import com.universidad.avicola.databinding.DialogReportesBinding
import com.universidad.avicola.databinding.DialogAjustesBinding
import com.universidad.avicola.ui.dashboard.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * InventarioActivity.kt — Versión Pro
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/inventario/
 */
class InventarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventarioBinding
    private val viewModel: InventarioViewModel by viewModels()
    private lateinit var adapter: InventarioAdapter
    private var productoEditando: ProductoInventario? = null
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarRecyclerView()
        configurarBuscador()
        configurarChipsFiltros()
        configurarFab()
        configurarNavegacion()
        configurarToolbarBotones()
        observarViewModel()
    }

    // ════════════════════════════════════════════════
    //  RECYCLERVIEW
    // ════════════════════════════════════════════════
    private fun configurarRecyclerView() {
        adapter = InventarioAdapter(
            onItemClick = { abrirDialogProducto(it) },
            onHistorialClick = { abrirDialogHistorial(it) }
        )
        binding.recyclerInventario.apply {
            layoutManager = LinearLayoutManager(this@InventarioActivity)
            adapter = this@InventarioActivity.adapter
            isNestedScrollingEnabled = false
        }
    }

    // ════════════════════════════════════════════════
    //  BUSCADOR
    // ════════════════════════════════════════════════
    private fun configurarBuscador() {
        binding.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setBusqueda(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ════════════════════════════════════════════════
    //  CHIPS DE FILTRO
    // ════════════════════════════════════════════════
    private fun configurarChipsFiltros() {
        // Chip: Stock crítico
        binding.chipStockCritico.setOnCheckedChangeListener { _, checked ->
            viewModel.toggleStockCritico(checked)
        }

        // Chip: Próximos a vencer
        binding.chipVencimiento.setOnCheckedChangeListener { _, checked ->
            viewModel.toggleProximosVencer(checked)
        }

        // Chips de categoría
        Categoria.values().forEach { categoria ->
            val chip = Chip(this).apply {
                text = categoria.displayName
                isCheckable = true
                setChipBackgroundColorResource(R.color.verde_suave)
                setTextColor(getColor(R.color.verde_oscuro))
            }
            chip.setOnCheckedChangeListener { _, checked ->
                viewModel.setCategoria(if (checked) categoria else null)
                // Desmarcar otros chips de categoría
                if (checked) {
                    for (i in 0 until binding.chipGroupCategorias.childCount) {
                        val c = binding.chipGroupCategorias.getChildAt(i) as? Chip
                        if (c != chip) c?.isChecked = false
                    }
                }
            }
            binding.chipGroupCategorias.addView(chip)
        }
    }

    // ════════════════════════════════════════════════
    //  BOTONES TOOLBAR — REPORTES Y AJUSTES
    // ════════════════════════════════════════════════
    private fun configurarToolbarBotones() {
        // Botón Reportes
        binding.btnReportes.setOnClickListener {
            abrirDialogReportes()
        }

        // Botón Ajustes/Configuración
        binding.btnConfigToolbar.setOnClickListener {
            abrirDialogAjustes()
        }
    }

    // ════════════════════════════════════════════════
    //  DIALOG REPORTES
    // ════════════════════════════════════════════════
    private fun abrirDialogReportes() {
        val sheet = BottomSheetDialog(this)
        val b = DialogReportesBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        val lista = viewModel.productos.value ?: emptyList()

        // Estadísticas generales
        b.tvTotalProductos.text = lista.size.toString()
        b.tvStockCritico.text = lista.count { it.isStockCritico() }.toString()
        b.tvProximosVencer.text = lista.count { it.isProximoAVencer() }.toString()
        b.tvVencidos.text = lista.count { it.isVencido() }.toString()
        b.tvValorTotal.text = "Q${String.format("%.2f", lista.sumOf { it.cantidad * it.precioUnitario })}"

        // Desglose por categoría
        val desglose = StringBuilder()
        Categoria.values().forEach { cat ->
            val count = lista.count { it.categoria == cat.name }
            val valor = lista.filter { it.categoria == cat.name }
                .sumOf { it.cantidad * it.precioUnitario }
            if (count > 0) {
                desglose.appendLine("${cat.displayName}: $count productos — Q${String.format("%.2f", valor)}")
            }
        }
        b.tvDesgloseCategorias.text = desglose.toString().ifEmpty { "Sin productos registrados" }

        // Productos con stock crítico
        val criticos = lista.filter { it.isStockCritico() }
        if (criticos.isNotEmpty()) {
            val sb = StringBuilder()
            criticos.forEach { sb.appendLine("• ${it.nombre}: ${it.cantidadConUnidad()} (mín: ${it.minStock})") }
            b.tvListaCriticos.text = sb.toString()
            b.cardCriticos.visibility = View.VISIBLE
        } else {
            b.cardCriticos.visibility = View.GONE
        }

        // Productos próximos a vencer
        val vencen = lista.filter { it.isProximoAVencer() || it.isVencido() }
        if (vencen.isNotEmpty()) {
            val sb = StringBuilder()
            vencen.forEach { p ->
                val fecha = if (p.fechaVencimientoMs > 0) sdf.format(Date(p.fechaVencimientoMs)) else "N/A"
                val estado = when {
                    p.isVencido() -> "VENCIDO"
                    else -> {
                        val dias = ((p.fechaVencimientoMs - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                        "Vence en $dias días"
                    }
                }
                sb.appendLine("• ${p.nombre} — $fecha ($estado)")
            }
            b.tvListaVencimientos.text = sb.toString()
            b.cardVencimientos.visibility = View.VISIBLE
        } else {
            b.cardVencimientos.visibility = View.GONE
        }

        b.btnCerrarReportes.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    // ════════════════════════════════════════════════
    //  DIALOG AJUSTES — EDITAR / ELIMINAR
    // ════════════════════════════════════════════════
    private fun abrirDialogAjustes() {
        val sheet = BottomSheetDialog(this)
        val b = DialogAjustesBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        val lista = viewModel.productos.value ?: emptyList()

        if (lista.isEmpty()) {
            b.tvSinProductos.visibility = View.VISIBLE
            b.recyclerAjustes.visibility = View.GONE
        } else {
            b.tvSinProductos.visibility = View.GONE
            b.recyclerAjustes.visibility = View.VISIBLE

            // Adapter simple para listar productos con opciones editar/eliminar
            val ajustesAdapter = AjustesAdapter(
                onEditar = { producto ->
                    sheet.dismiss()
                    abrirDialogProducto(producto)
                },
                onEliminar = { producto ->
                    sheet.dismiss()
                    confirmarEliminar(producto)
                }
            )
            b.recyclerAjustes.apply {
                layoutManager = LinearLayoutManager(this@InventarioActivity)
                adapter = ajustesAdapter
            }
            ajustesAdapter.submitList(lista)
        }

        b.btnCerrarAjustes.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    // ════════════════════════════════════════════════
    //  DIALOG HISTORIAL DE MOVIMIENTOS
    // ════════════════════════════════════════════════
    private fun abrirDialogHistorial(producto: ProductoInventario) {
        val sheet = BottomSheetDialog(this)
        val b = DialogHistorialBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        b.tvTituloHistorial.text = "Historial: ${producto.nombre}"

        val logAdapter = InventoryLogAdapter()
        b.recyclerHistorial.apply {
            layoutManager = LinearLayoutManager(this@InventarioActivity)
            adapter = logAdapter
        }

        viewModel.obtenerLogsPorProducto(producto.id).observe(this) { logs ->
            if (logs.isEmpty()) {
                b.tvSinLogs.visibility = View.VISIBLE
                b.recyclerHistorial.visibility = View.GONE
            } else {
                b.tvSinLogs.visibility = View.GONE
                b.recyclerHistorial.visibility = View.VISIBLE
                logAdapter.submitList(logs)
            }
        }

        b.btnCerrarHistorial.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    // ════════════════════════════════════════════════
    //  DIALOG AÑADIR / EDITAR PRODUCTO
    // ════════════════════════════════════════════════
    private fun abrirDialogProducto(producto: ProductoInventario? = null) {
        productoEditando = producto
        val sheet = BottomSheetDialog(this)
        val b = DialogProductoBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        // Spinner de categorías
        val categorias = Categoria.values().map { it.displayName }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categorias)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerCategoria.adapter = spinnerAdapter

        // Spinner de unidades
        val unidades = listOf("Unidades", "Sacos", "Kg", "Gramos", "Litros", "ml", "Cajas", "Frascos")
        val unidadesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, unidades)
        unidadesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerUnidad.adapter = unidadesAdapter

        // Pre-rellenar si es edición
        producto?.let { p ->
            b.etNombreProducto.setText(p.nombre)
            b.etCantidad.setText(if (p.cantidad % 1.0 == 0.0) p.cantidad.toInt().toString() else "${p.cantidad}")
            b.etPrecio.setText(if (p.precioUnitario % 1.0 == 0.0) p.precioUnitario.toInt().toString() else String.format("%.2f", p.precioUnitario))
            b.etMinStock.setText(if (p.minStock % 1.0 == 0.0) p.minStock.toInt().toString() else "${p.minStock}")
            b.etNumeroLote.setText(p.numeroLote)
            if (p.fechaVencimientoMs > 0L) b.etFecha.setText(sdf.format(Date(p.fechaVencimientoMs)))

            val catIndex = Categoria.values().indexOfFirst { it.name == p.categoria }
            if (catIndex >= 0) b.spinnerCategoria.setSelection(catIndex)

            val uniIndex = unidades.indexOf(p.unitType)
            if (uniIndex >= 0) b.spinnerUnidad.setSelection(uniIndex)

            b.etRazonCambio.visibility = View.VISIBLE
            b.tvRazonLabel.visibility = View.VISIBLE
        }

        // DatePicker
        val abrirFecha = {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                b.etFecha.setText(String.format("%02d/%02d/%04d", d, m + 1, y))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        b.etFecha.setOnClickListener { abrirFecha() }
        b.tilFecha.setEndIconOnClickListener { abrirFecha() }

        // Cancelar
        b.btnCancelarDialog.setOnClickListener { sheet.dismiss() }
        b.btnCerrarDialog.setOnClickListener { sheet.dismiss() }

        // Guardar
        b.btnGuardarProducto.setOnClickListener {
            val nombre = b.etNombreProducto.text.toString().trim()
            val cantidadStr = b.etCantidad.text.toString().trim()
            if (nombre.isEmpty() || cantidadStr.isEmpty()) {
                Toast.makeText(this, "Nombre y cantidad son obligatorios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cantidad = cantidadStr.toDoubleOrNull() ?: 0.0
            val precio = b.etPrecio.text.toString().toDoubleOrNull() ?: 0.0
            val minStock = b.etMinStock.text.toString().toDoubleOrNull() ?: 0.0
            val categoria = Categoria.values()[b.spinnerCategoria.selectedItemPosition].name
            val unitType = unidades[b.spinnerUnidad.selectedItemPosition]
            val lote = b.etNumeroLote.text.toString().trim()
            val razon = b.etRazonCambio.text.toString().trim().ifEmpty { "Ajuste manual" }

            // Parsear fecha
            var fechaMs = 0L
            val fechaStr = b.etFecha.text.toString().trim()
            if (fechaStr.isNotEmpty()) {
                try { fechaMs = sdf.parse(fechaStr)?.time ?: 0L } catch (e: Exception) {}
            }

            val nuevoProducto = ProductoInventario(
                id = productoEditando?.id ?: "",
                nombre = nombre,
                cantidad = cantidad,
                precioUnitario = precio,
                minStock = minStock,
                categoria = categoria,
                unitType = unitType,
                numeroLote = lote,
                fechaVencimientoMs = fechaMs,
                fechaCreacion = productoEditando?.fechaCreacion ?: System.currentTimeMillis()
            )

            if (productoEditando == null) {
                viewModel.agregarProducto(nuevoProducto)
            } else {
                viewModel.actualizarProducto(productoEditando!!, nuevoProducto, razon)
            }
            sheet.dismiss()
        }

        sheet.show()
    }

    // ════════════════════════════════════════════════
    //  CONFIRMAR ELIMINACIÓN
    // ════════════════════════════════════════════════
    private fun confirmarEliminar(producto: ProductoInventario) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Eliminar \"${producto.nombre}\" del inventario?\nEsta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.eliminarProducto(producto.id, producto.nombre)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ════════════════════════════════════════════════
    //  FAB
    // ════════════════════════════════════════════════
    private fun configurarFab() {
        binding.fabAnadir.setOnClickListener {
            productoEditando = null
            abrirDialogProducto(null)
        }
    }

    // ════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ════════════════════════════════════════════════
    private fun configurarNavegacion() {
        binding.navInicio.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
        binding.btnSalirNav.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Salir")
                .setMessage("¿Volver al menú principal?")
                .setPositiveButton("Sí") { _, _ ->
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                .setNegativeButton("No", null)
                .show()
        }
    }

    // ════════════════════════════════════════════════
    //  OBSERVADORES
    // ════════════════════════════════════════════════
    private fun observarViewModel() {
        viewModel.productos.observe(this) { lista ->
            viewModel.aplicarFiltros(lista)
        }

        viewModel.productosFiltrados.observe(this) { lista ->
            adapter.submitList(lista)
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerInventario.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
            // Actualizar contador
            binding.tvContadorProductos.text = "${lista.size} productos"
        }

        viewModel.mensaje.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
