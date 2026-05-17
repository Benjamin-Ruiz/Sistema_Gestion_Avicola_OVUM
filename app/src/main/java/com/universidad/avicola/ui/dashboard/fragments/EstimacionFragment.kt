package com.universidad.avicola.ui.dashboard.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.universidad.avicola.R
import com.universidad.avicola.data.model.EstimacionCostos
import com.universidad.avicola.databinding.DialogDetalleEstimacionBinding
import com.universidad.avicola.databinding.FragmentCostosBinding
import com.universidad.avicola.ui.costos.CostosFormActivity
import com.universidad.avicola.ui.costos.CostosViewModel
import com.universidad.avicola.ui.costos.EstimacionAdapter
import java.util.*

class EstimacionFragment : Fragment() {

    private var _binding: FragmentCostosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CostosViewModel by viewModels()
    private lateinit var adapter: EstimacionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCostosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarRecycler()
        configurarBotones()
        observarViewModel()
    }

    private fun configurarRecycler() {
        adapter = EstimacionAdapter(
            onClick = { abrirDetalle(it) },
            onLongClick = { mostrarOpciones(it) }
        )
        binding.recyclerEstimaciones.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEstimaciones.adapter = adapter
    }

    private fun configurarBotones() {
        binding.btnFiltroTodas.setOnClickListener { viewModel.setFiltroEstado(null); marcarFiltro(0) }
        binding.btnFiltroBorrador.setOnClickListener { viewModel.setFiltroEstado("BORRADOR"); marcarFiltro(1) }
        binding.btnFiltroActiva.setOnClickListener { viewModel.setFiltroEstado("ACTIVA"); marcarFiltro(2) }
        binding.btnFiltroCompletada.setOnClickListener { viewModel.setFiltroEstado("COMPLETADA"); marcarFiltro(3) }

        binding.fabNuevaEstimacion.setOnClickListener { abrirFormulario(null) }

        // Inicializar estado visual
        marcarFiltro(0)
    }

    private fun marcarFiltro(pos: Int) {
        val btns = listOf(binding.btnFiltroTodas, binding.btnFiltroBorrador, binding.btnFiltroActiva, binding.btnFiltroCompletada)
        btns.forEachIndexed { i, btn ->
            if (i == pos) {
                btn.setBackgroundResource(R.drawable.bg_tab_activo)
            } else {
                btn.setBackgroundResource(R.drawable.bg_tab_inactivo)
            }
            // El texto siempre es blanco por solicitud final del usuario
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.blanco))
        }
    }

    private fun observarViewModel() {
        viewModel.estimacionesFiltradas.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvSinEstimaciones.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            binding.tvContadorEstimaciones.text = "${lista.size} estimaciones"
            
            val metricas = viewModel.calcularMetricas(lista)
            binding.tvMetricaTotal.text = "Q${String.format(Locale.getDefault(), "%.2f", metricas.costoTotalAcumulado)}"
            binding.tvMetricaCantidad.text = metricas.totalEstimaciones.toString()
            binding.tvMetricaRentables.text = metricas.estimacionesRentables.toString()
            binding.tvMetricaRoi.text = "${String.format(Locale.getDefault(), "%.1f", metricas.roiPromedio)}%"
        }

        viewModel.mensaje.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarOpciones(est: EstimacionCostos) {
        val opciones = if (est.estado == "BORRADOR") arrayOf("Editar", "Eliminar") else arrayOf("Eliminar")
        AlertDialog.Builder(requireContext())
            .setTitle(est.loteNombre)
            .setItems(opciones) { _, pos ->
                when (opciones[pos]) {
                    "Editar" -> abrirFormulario(est)
                    "Eliminar" -> confirmarEliminar(est)
                }
            }.show()
    }

    private fun confirmarEliminar(est: EstimacionCostos) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Estimación")
            .setMessage("¿Estás seguro de eliminar esta estimación?")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarEstimacion(est.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirDetalle(est: EstimacionCostos) {
        try {
            val sheet = BottomSheetDialog(requireContext())
            val b = DialogDetalleEstimacionBinding.inflate(layoutInflater)
            sheet.setContentView(b.root)

            b.tvDetLote.text = est.loteNombre
            b.tvDetAves.text = "${est.cantidadAves} aves"
            b.tvDetCostoTotal.text = "Q${String.format(Locale.getDefault(), "%.2f", est.costoTotal)}"
            b.tvDetIngreso.text = "Q${String.format(Locale.getDefault(), "%.2f", est.precioVentaUnitario * est.cantidadAves)}"
            b.tvDetGanancia.text = "Q${String.format(Locale.getDefault(), "%.2f", est.gananciaNeta)}"
            b.tvDetRoi.text = "${String.format(Locale.getDefault(), "%.1f", est.roi)}%"

            if (est.estado == "COMPLETADA") {
                b.cardComparacion.visibility = View.VISIBLE
                b.tvDetCostoReal.text = "Q${String.format(Locale.getDefault(), "%.2f", est.costoRealRegistrado)}"
                val dif = est.gananciaNeta - (est.precioVentaUnitario * est.cantidadAves - est.costoRealRegistrado)
                b.tvDetDiferencia.text = "Dif: Q${String.format(Locale.getDefault(), "%.2f", dif)}"
            }

            b.btnDetCerrar.setOnClickListener { sheet.dismiss() }
            sheet.show()
        } catch (e: Exception) {
            AlertDialog.Builder(requireContext())
                .setTitle(est.loteNombre)
                .setMessage("Costo Total: Q${est.costoTotal}\nEstado: ${est.estado}")
                .setPositiveButton("Cerrar", null)
                .show()
        }
    }

    private fun abrirFormulario(est: EstimacionCostos?) {
        val intent = Intent(requireContext(), CostosFormActivity::class.java)
        est?.let { intent.putExtra("ESTIMACION_ID", it.id) }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}