package com.universidad.avicola.ui.inventario

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.universidad.avicola.R
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.databinding.*
import com.universidad.avicola.ui.auth.LoginActivity
import com.universidad.avicola.ui.dashboard.DashboardActivity
import com.universidad.avicola.util.animateNumber
import java.text.SimpleDateFormat
import java.util.*

class InventarioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInventarioBinding
    private val viewModel: InventarioViewModel by viewModels()
    private lateinit var adapter: InventarioAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInventarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarUI()
        observarViewModel()
    }

    private fun configurarUI() {
        // RecyclerView
        adapter = InventarioAdapter(
            onItemClick = { abrirDialogProducto(it) },
            onLongClick = { confirmarEliminacionConPassword(it) },
            onHistorialClick = { abrirDialogHistorial(it) }
        )
        binding.recyclerInventario.layoutManager = LinearLayoutManager(this)
        binding.recyclerInventario.adapter = adapter

        // Buscador
        binding.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setBusqueda(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        // Chips de Alerta
        binding.chipStockCritico.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleStockCritico(isChecked)
        }
        binding.chipVencimiento.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleProximosVencer(isChecked)
        }

        // Chips de Categoría dinámicos
        Categoria.entries.forEach { categoria ->
            val chip = Chip(this).apply {
                text = categoria.displayName
                isCheckable = true
                setChipBackgroundColorResource(R.color.verde_suave)
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setCategoria(if (isChecked) categoria else null)
            }
            binding.chipGroupCategorias.addView(chip)
        }

        // FAB
        binding.fabAnadir.setOnClickListener { abrirDialogProducto(null) }

        // Configurar Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                    true
                }
                R.id.nav_reportes -> {
                    abrirDialogReportes()
                    true
                }

                R.id.nav_salir -> {
                    confirmarSalida()
                    true
                }
                else -> false
            }
        }

        // Botón Añadir Filtro Personalizado
        binding.btnAnadirFiltro.setOnClickListener {
            abrirDialogFiltros()
        }
    }

    private fun abrirDialogFiltros() {
        val sheet = BottomSheetDialog(this)
        val b = DialogFiltrosBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        b.btnCerrarFiltros.setOnClickListener { sheet.dismiss() }

        b.btnLimpiarFiltros.setOnClickListener {
            viewModel.limpiarFiltrosAvanzados()
            sheet.dismiss()
        }

        b.btnAplicarFiltros.setOnClickListener {
            val min = b.etPrecioMin.text.toString().toDoubleOrNull()
            val max = b.etPrecioMax.text.toString().toDoubleOrNull()
            
            val orden = when (b.rgOrden.checkedRadioButtonId) {
                R.id.rbPrecioMenor -> "PrecioMenor"
                R.id.rbPrecioMayor -> "PrecioMayor"
                R.id.rbCantidad -> "Cantidad"
                else -> "Nombre"
            }

            viewModel.aplicarFiltrosAvanzados(min, max, orden)
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun observarViewModel() {
        viewModel.productosFiltrados.observe(this) { lista ->
            adapter.submitList(lista)
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.mensaje.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // --- DIALOGS (Mantenemos la lógica pero simplificada) ---

    private fun abrirDialogProducto(producto: ProductoInventario?) {
        val sheet = BottomSheetDialog(this)
        val b = DialogProductoBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        b.btnCerrarDialog.setOnClickListener { sheet.dismiss() }
        b.btnCancelarDialog.setOnClickListener { sheet.dismiss() }

        // Configurar Spinners
        val categorias = Categoria.entries.map { it.displayName }
        val adapterCat = ArrayAdapter(this, android.R.layout.simple_spinner_item, categorias)
        adapterCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerCategoria.adapter = adapterCat

        val unidades = listOf("Unidades", "Sacos", "Libras", "Kilogramos", "Litros", "Mililitros", "Cajas")
        val adapterUni = ArrayAdapter(this, android.R.layout.simple_spinner_item, unidades)
        adapterUni.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerUnidad.adapter = adapterUni

        // Manejo de Fecha
        var fechaVencimientoMs = producto?.fechaVencimientoMs ?: 0L
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        if (fechaVencimientoMs > 0) {
            b.etFecha.setText(sdf.format(Date(fechaVencimientoMs)))
        }

        val datePickerListener = View.OnClickListener {
            val calendar = Calendar.getInstance()
            if (fechaVencimientoMs > 0) calendar.timeInMillis = fechaVencimientoMs
            
            val picker = android.app.DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val selected = Calendar.getInstance()
                    selected.set(year, month, dayOfMonth)
                    fechaVencimientoMs = selected.timeInMillis
                    b.etFecha.setText(sdf.format(selected.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            picker.show()
        }
        b.etFecha.setOnClickListener(datePickerListener)
        b.tilFecha.setEndIconOnClickListener(datePickerListener)

        // Lógica especial por Categoría
        b.spinnerCategoria.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val catSeleccionada = Categoria.entries[position]
                
                when (catSeleccionada) {
                    Categoria.HERRAMIENTAS -> {
                        // Ocultar fecha de vencimiento para herramientas
                        b.tvFechaLabel.visibility = View.GONE
                        b.tilFecha.visibility = View.GONE
                        fechaVencimientoMs = 0L // Resetear fecha
                        
                        b.tvPrecioLabel.text = "Precio de Compra"
                        b.tilPrecio.hint = "Precio de Compra"
                    }
                    Categoria.MEDICINAS -> {
                        // Mostrar fecha de vencimiento obligatoriamente para medicinas
                        b.tvFechaLabel.visibility = View.VISIBLE
                        b.tilFecha.visibility = View.VISIBLE
                        
                        b.tvPrecioLabel.text = "Precio Unitario"
                        b.tilPrecio.hint = "Precio Unitario"
                    }
                    else -> {
                        // Comportamiento normal para Alimentos u Otros
                        b.tvFechaLabel.visibility = View.VISIBLE
                        b.tilFecha.visibility = View.VISIBLE
                        
                        b.tvPrecioLabel.text = "Precio Unitario"
                        b.tilPrecio.hint = "Precio Unitario"
                    }
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Pre-rellenar si es edición
        producto?.let { p ->
            b.etNombreProducto.setText(p.nombre)
            b.etCantidad.setText(p.cantidad.toString())
            b.etPrecio.setText(p.precioUnitario.toString())
            b.etMinStock.setText(p.minStock.toString())
            b.etNumeroLote.setText(p.numeroLote)
            
            val catIndex = Categoria.entries.indexOfFirst { it.name == p.categoria }
            if (catIndex >= 0) b.spinnerCategoria.setSelection(catIndex)
            
            val uniIndex = unidades.indexOf(p.unitType)
            if (uniIndex >= 0) b.spinnerUnidad.setSelection(uniIndex)

            b.tvRazonLabel.visibility = View.VISIBLE
            b.tilRazon.visibility = View.VISIBLE
        }

        b.btnGuardarProducto.setOnClickListener {
            val nombre = b.etNombreProducto.text.toString()
            if (nombre.isEmpty()) {
                b.tilNombreProducto.error = "Campo obligatorio"
                return@setOnClickListener
            }

            val nuevo = (producto ?: ProductoInventario()).copy(
                nombre = nombre,
                cantidad = b.etCantidad.text.toString().toDoubleOrNull() ?: 0.0,
                precioUnitario = b.etPrecio.text.toString().toDoubleOrNull() ?: 0.0,
                minStock = b.etMinStock.text.toString().toDoubleOrNull() ?: 0.0,
                categoria = Categoria.entries[b.spinnerCategoria.selectedItemPosition].name,
                unitType = b.spinnerUnidad.selectedItem.toString(),
                numeroLote = b.etNumeroLote.text.toString(),
                fechaVencimientoMs = fechaVencimientoMs,
                fechaActualizacion = System.currentTimeMillis()
            )

            if (producto == null) viewModel.agregarProducto(nuevo)
            else viewModel.actualizarProducto(producto, nuevo, b.etRazonCambio.text.toString())
            
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun abrirDialogReportes() {
        val sheet = BottomSheetDialog(this)
        val b = DialogReportesBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        b.btnCerrarReportes.setOnClickListener { sheet.dismiss() }

        viewModel.reporte.observe(this) { r ->
            // Animaciones de números
            b.tvTotalProductos.animateNumber(r.totalProductos)
            b.tvValorTotal.animateNumber(r.valorTotal, isCurrency = true)
            b.tvStockCritico.animateNumber(r.stockCritico)
            b.tvProximosVencer.animateNumber(r.porVencer)

            // Desglose de categorías
            val sb = StringBuilder()
            r.desgloseCategorias.forEach { (catName, cant) ->
                val displayName = try { Categoria.valueOf(catName).displayName } catch (e: Exception) { catName }
                sb.append("• $displayName: $cant productos\n")
            }
            b.tvDesgloseCategorias.text = if (sb.isEmpty()) "Sin datos" else sb.toString()

            // Alertas críticas
            if (r.stockCritico > 0) {
                b.cardCriticos.visibility = View.VISIBLE
                b.tvListaCriticos.text = r.listaCriticos.joinToString("\n")
            } else {
                b.cardCriticos.visibility = View.GONE
            }

            // Alertas vencimiento
            if (r.porVencer > 0) {
                b.cardVencimientos.visibility = View.VISIBLE
                b.tvListaVencimientos.text = r.listaPorVencer.joinToString("\n")
            } else {
                b.cardVencimientos.visibility = View.GONE
            }

            if (r.vencidos > 0) {
                b.tvVencidos.visibility = View.VISIBLE
                b.tvVencidos.text = "⚠️ Hay ${r.vencidos} productos vencidos"
            } else {
                b.tvVencidos.visibility = View.GONE
            }
        }

        sheet.show()
    }



    private fun confirmarEliminacionConPassword(producto: ProductoInventario) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Sesión no válida", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Diálogo de confirmación inicial profesional
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Desea eliminar permanentemente el producto '${producto.nombre}'? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                abrirDialogVerificacionPassword(user.email!!, producto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirDialogVerificacionPassword(email: String, producto: ProductoInventario) {
        val inputLayout = TextInputLayout(this).apply {
            setPadding(60, 20, 60, 0)
            hint = "Ingrese su contraseña"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }
        val etPassword = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 14f
        }
        inputLayout.addView(etPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Autenticación Requerida")
            .setMessage("Por seguridad, verifique su identidad para eliminar este producto.")
            .setView(inputLayout)
            .setPositiveButton("Confirmar") { _, _ ->
                val password = etPassword.text.toString()
                if (password.isNotEmpty()) {
                    val credential = EmailAuthProvider.getCredential(email, password)
                    FirebaseAuth.getInstance().currentUser?.reauthenticate(credential)
                        ?.addOnSuccessListener {
                            viewModel.eliminarProducto(producto.id, producto.nombre)
                            Toast.makeText(this, "✓ Producto eliminado con éxito", Toast.LENGTH_SHORT).show()
                        }
                        ?.addOnFailureListener {
                            Toast.makeText(this, "❌ Error: Credenciales incorrectas", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }
        dialog.show()
    }

    private fun abrirDialogHistorial(producto: ProductoInventario) {
        val sheet = BottomSheetDialog(this)
        val b = DialogHistorialBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)
        b.btnCerrarHistorial.setOnClickListener { sheet.dismiss() }
        // Lógica de historial...
        sheet.show()
    }

    private fun confirmarSalida() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Deseas salir de la aplicación?")
            .setPositiveButton("Salir") { _, _ ->
                startActivity(Intent(this, LoginActivity::class.java))
                finishAffinity()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
