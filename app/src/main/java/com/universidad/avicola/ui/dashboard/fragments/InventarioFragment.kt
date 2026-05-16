package com.universidad.avicola.ui.dashboard.fragments

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.universidad.avicola.R
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.databinding.DialogFiltrosBinding
import com.universidad.avicola.databinding.DialogHistorialBinding
import com.universidad.avicola.databinding.DialogProductoBinding
import com.universidad.avicola.databinding.DialogReportesBinding
import com.universidad.avicola.databinding.FragmentInventarioBinding
import com.universidad.avicola.ui.dashboard.DashboardActivity
import com.universidad.avicola.ui.inventario.InventarioAdapter
import com.universidad.avicola.ui.inventario.InventarioViewModel
import com.universidad.avicola.util.animateNumber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * InventarioFragment.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/dashboard/fragments/
 *
 * CORRECCIONES:
 * 1. onItemClick → abre dialog de edición (antes estaba vacío).
 * 2. onLongClick → abre confirmación con contraseña para eliminar (antes vacío).
 * 3. onHistorialClick → abre dialog de historial (antes vacío).
 * 4. fabAnadir → conectado para crear nuevos productos (antes no estaba configurado).
 * 5. El fragment ya no tiene el bug de ser expulsado — ese se corrigió en DashboardActivity.
 */
class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InventarioViewModel by viewModels()
    private lateinit var adapter: InventarioAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecycler()
        configurarFiltros()
        configurarFab()
        observarViewModel()
        configurarBotonReportes()
    }

    // ══════════════════════════════════════════════════════════════════
    //  RECYCLERVIEW
    // ══════════════════════════════════════════════════════════════════

    private fun configurarRecycler() {
        adapter = InventarioAdapter(
            onItemClick      = { abrirDialogProducto(it) },          // CORRECCIÓN: antes vacío
            onLongClick      = { confirmarEliminacionConPassword(it) }, // CORRECCIÓN: antes vacío
            onHistorialClick = { abrirDialogHistorial(it) }          // CORRECCIÓN: antes vacío
        )
        binding.recyclerInventario.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerInventario.adapter = adapter
    }

    // ══════════════════════════════════════════════════════════════════
    //  FAB — AÑADIR PRODUCTO
    // ══════════════════════════════════════════════════════════════════

    private fun configurarFab() {
        binding.fabAnadir.setOnClickListener { abrirDialogProducto(null) } // CORRECCIÓN: antes faltaba
    }

    // ══════════════════════════════════════════════════════════════════
    //  FILTROS
    // ══════════════════════════════════════════════════════════════════

    private fun configurarFiltros() {
        binding.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setBusqueda(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipStockCritico.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleStockCritico(isChecked)
        }

        binding.chipVencimiento.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleProximosVencer(isChecked)
        }

        // Chips de categoría dinámicos
        binding.chipGroupCategorias.removeAllViews()
        Categoria.entries.forEach { categoria ->
            val chip = Chip(requireContext()).apply {
                text = categoria.displayName
                isCheckable = true
                setChipBackgroundColorResource(R.color.verde_suave)
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.setCategoria(if (isChecked) categoria else null)
                }
            }
            binding.chipGroupCategorias.addView(chip)
        }

        binding.btnAnadirFiltro.setOnClickListener { abrirDialogFiltros() }
    }

    // ══════════════════════════════════════════════════════════════════
    //  OBSERVADORES
    // ══════════════════════════════════════════════════════════════════

    private fun observarViewModel() {
        viewModel.productosFiltrados.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.mensaje.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  TOOLBAR REPORTES
    // ══════════════════════════════════════════════════════════════════

    private fun configurarBotonReportes() {
        (activity as? DashboardActivity)?.let { dashboard ->
            dashboard.binding.btnToolbarReportes.visibility = View.VISIBLE
            dashboard.binding.btnToolbarReportes.setOnClickListener { abrirDialogReportes() }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  DIALOG PRODUCTO (CREAR / EDITAR)
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDialogProducto(producto: ProductoInventario?) {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogProductoBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        b.btnCerrarDialog.setOnClickListener { sheet.dismiss() }
        b.btnCancelarDialog.setOnClickListener { sheet.dismiss() }

        // Spinner categoría
        val categorias = Categoria.entries.map { it.displayName }
        val adapterCat = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categorias)
        adapterCat.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerCategoria.adapter = adapterCat

        // Spinner unidad
        val unidades = listOf("Unidades", "Sacos", "Libras", "Kilogramos", "Litros", "Mililitros", "Cajas")
        val adapterUni = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, unidades)
        adapterUni.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerUnidad.adapter = adapterUni

        // Fecha de vencimiento
        var fechaVencimientoMs = producto?.fechaVencimientoMs ?: 0L
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        if (fechaVencimientoMs > 0) b.etFecha.setText(sdf.format(Date(fechaVencimientoMs)))

        val datePickerListener = View.OnClickListener {
            val calendar = Calendar.getInstance()
            if (fechaVencimientoMs > 0) calendar.timeInMillis = fechaVencimientoMs
            DatePickerDialog(requireContext(), { _, year, month, day ->
                val selected = Calendar.getInstance().also { it.set(year, month, day) }
                fechaVencimientoMs = selected.timeInMillis
                b.etFecha.setText(sdf.format(selected.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
        b.etFecha.setOnClickListener(datePickerListener)
        b.tilFecha.setEndIconOnClickListener(datePickerListener)

        // Lógica por categoría
        b.spinnerCategoria.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                when (Categoria.entries[pos]) {
                    Categoria.HERRAMIENTAS -> {
                        b.tvFechaLabel.visibility = View.GONE
                        b.tilFecha.visibility = View.GONE
                        fechaVencimientoMs = 0L
                        b.tvPrecioLabel.text = "Precio de Compra"
                        b.tilPrecio.hint = "Precio de Compra"
                    }
                    else -> {
                        b.tvFechaLabel.visibility = View.VISIBLE
                        b.tilFecha.visibility = View.VISIBLE
                        b.tvPrecioLabel.text = "Precio Unitario"
                        b.tilPrecio.hint = "Precio Unitario"
                    }
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
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
            val nombre = b.etNombreProducto.text.toString().trim()
            if (nombre.isEmpty()) {
                b.tilNombreProducto.error = "Campo obligatorio"
                return@setOnClickListener
            }
            val nuevo = (producto ?: ProductoInventario()).copy(
                nombre            = nombre,
                cantidad          = b.etCantidad.text.toString().toDoubleOrNull() ?: 0.0,
                precioUnitario    = b.etPrecio.text.toString().toDoubleOrNull() ?: 0.0,
                minStock          = b.etMinStock.text.toString().toDoubleOrNull() ?: 0.0,
                categoria         = Categoria.entries[b.spinnerCategoria.selectedItemPosition].name,
                unitType          = b.spinnerUnidad.selectedItem.toString(),
                numeroLote        = b.etNumeroLote.text.toString(),
                fechaVencimientoMs = fechaVencimientoMs,
                fechaActualizacion = System.currentTimeMillis()
            )
            if (producto == null) viewModel.agregarProducto(nuevo)
            else viewModel.actualizarProducto(producto, nuevo, b.etRazonCambio.text.toString())
            sheet.dismiss()
        }
        sheet.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  ELIMINACIÓN CON CONTRASEÑA
    // ══════════════════════════════════════════════════════════════════

    private fun confirmarEliminacionConPassword(producto: ProductoInventario) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) {
            Toast.makeText(requireContext(), "Sesión no válida", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Desea eliminar permanentemente '${producto.nombre}'? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                abrirDialogVerificacionPassword(user.email!!, producto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirDialogVerificacionPassword(email: String, producto: ProductoInventario) {
        val inputLayout = TextInputLayout(requireContext()).apply {
            setPadding(60, 20, 60, 0)
            hint = "Ingrese su contraseña"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }
        val etPassword = TextInputEditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 14f
        }
        inputLayout.addView(etPassword)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Autenticación Requerida")
            .setMessage("Por seguridad, verifique su identidad para eliminar este producto.")
            .setView(inputLayout)
            .setPositiveButton("Confirmar") { _, _ ->
                val password = etPassword.text.toString()
                if (password.isNotEmpty()) {
                    val credential = EmailAuthProvider.getCredential(email, password)
                    FirebaseAuth.getInstance().currentUser
                        ?.reauthenticate(credential)
                        ?.addOnSuccessListener {
                            viewModel.eliminarProducto(producto.id, producto.nombre)
                        }
                        ?.addOnFailureListener {
                            Toast.makeText(requireContext(), "Credenciales incorrectas", Toast.LENGTH_LONG).show()
                        }
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(android.graphics.Color.RED)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }
        dialog.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  DIALOG HISTORIAL
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDialogHistorial(producto: ProductoInventario) {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogHistorialBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)
        b.btnCerrarHistorial.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  DIALOG FILTROS AVANZADOS
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDialogFiltros() {
        val sheet = BottomSheetDialog(requireContext())
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
                R.id.rbCantidad    -> "Cantidad"
                else               -> "Nombre"
            }
            viewModel.aplicarFiltrosAvanzados(min, max, orden)
            sheet.dismiss()
        }
        sheet.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  DIALOG REPORTES
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDialogReportes() {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogReportesBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)
        b.btnCerrarReportes.setOnClickListener { sheet.dismiss() }

        viewModel.reporte.observe(viewLifecycleOwner) { r ->
            b.tvTotalProductos.animateNumber(r.totalProductos)
            b.tvValorTotal.animateNumber(r.valorTotal, isCurrency = true)
            b.tvStockCritico.animateNumber(r.stockCritico)
            b.tvProximosVencer.animateNumber(r.porVencer)

            val sb = StringBuilder()
            r.desgloseCategorias.forEach { (catName, cant) ->
                val display = try { Categoria.valueOf(catName).displayName } catch (e: Exception) { catName }
                sb.append("• $display: $cant productos\n")
            }
            b.tvDesgloseCategorias.text = if (sb.isEmpty()) "Sin datos" else sb.toString()

            b.cardCriticos.visibility    = if (r.stockCritico > 0) View.VISIBLE else View.GONE
            b.tvListaCriticos.text       = r.listaCriticos.joinToString("\n")
            b.cardVencimientos.visibility = if (r.porVencer > 0) View.VISIBLE else View.GONE
            b.tvListaVencimientos.text   = r.listaPorVencer.joinToString("\n")
            b.tvVencidos.visibility      = if (r.vencidos > 0) View.VISIBLE else View.GONE
            b.tvVencidos.text            = "⚠ Hay ${r.vencidos} productos vencidos"
        }
        sheet.show()
    }

    override fun onDestroyView() {
        // Ocultar botón de reportes al salir del fragment
        (activity as? DashboardActivity)
            ?.binding?.btnToolbarReportes
            ?.visibility = View.GONE
        super.onDestroyView()
        _binding = null
    }
}