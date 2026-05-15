package com.universidad.avicola.ui.dashboard.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.universidad.avicola.R
import com.universidad.avicola.data.model.Categoria
import com.universidad.avicola.databinding.DialogFiltrosBinding
import com.universidad.avicola.databinding.DialogReportesBinding
import com.universidad.avicola.databinding.FragmentInventarioBinding
import com.universidad.avicola.ui.dashboard.DashboardActivity
import com.universidad.avicola.ui.inventario.InventarioAdapter
import com.universidad.avicola.ui.inventario.InventarioViewModel
import com.universidad.avicola.util.animateNumber

class InventarioFragment : Fragment() {

    private var _binding: FragmentInventarioBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: InventarioViewModel by viewModels()
    private lateinit var adapter: InventarioAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInventarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        configurarUI()
        observarViewModel()
        configurarBotonReportes()
    }

    private fun configurarBotonReportes() {
        (activity as? DashboardActivity)?.binding?.btnToolbarReportes?.setOnClickListener {
            abrirDialogReportes()
        }
    }

    private fun configurarUI() {
        adapter = InventarioAdapter(
            onItemClick = { /* Detalle */ },
            onLongClick = { /* Opciones */ },
            onHistorialClick = { /* Historial */ }
        )
        binding.recyclerInventario.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerInventario.adapter = adapter

        binding.etBuscar.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setBusqueda(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipStockCritico.setOnClickListener {
            viewModel.toggleStockCritico((it as Chip).isChecked)
        }

        binding.chipVencimiento.setOnClickListener {
            viewModel.toggleProximosVencer((it as Chip).isChecked)
        }

        binding.btnAnadirFiltro.setOnClickListener {
            abrirDialogFiltros()
        }
    }

    private fun observarViewModel() {
        viewModel.productosFiltrados.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
        }

        // Generar chips de categorías dinámicamente
        val categorias = Categoria.entries
        binding.chipGroupCategorias.removeAllViews()
        categorias.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat.displayName
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) viewModel.setCategoria(cat)
                    else viewModel.setCategoria(null)
                }
            }
            binding.chipGroupCategorias.addView(chip)
        }
    }

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
                R.id.rbCantidad -> "Cantidad"
                else -> "Nombre"
            }

            viewModel.aplicarFiltrosAvanzados(min, max, orden)
            sheet.dismiss()
        }

        sheet.show()
    }

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
                val displayName = try { Categoria.valueOf(catName).displayName } catch (e: Exception) { catName }
                sb.append("• $displayName: $cant productos\n")
            }
            b.tvDesgloseCategorias.text = if (sb.isEmpty()) "Sin datos" else sb.toString()

            if (r.stockCritico > 0) {
                b.cardCriticos.visibility = View.VISIBLE
                b.tvListaCriticos.text = r.listaCriticos.joinToString("\n")
            } else {
                b.cardCriticos.visibility = View.GONE
            }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}