package com.universidad.avicola.ui.costos

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.universidad.avicola.R
import com.universidad.avicola.data.model.*
import com.universidad.avicola.databinding.ActivityCostosBinding
import com.universidad.avicola.ui.dashboard.DashboardActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CostosActivity.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/costos/
 */
class CostosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCostosBinding
    private val viewModel: CostosViewModel by viewModels()
    private lateinit var adapter: EstimacionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCostosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarRecycler()
        configurarBotones()
        configurarNavegacion()
        observarViewModel()
    }

    // ══════════════════════════════════════════════════════════════════
    //  RECYCLERVIEW
    // ══════════════════════════════════════════════════════════════════

    private fun configurarRecycler() {
        adapter = EstimacionAdapter(
            onClick = { abrirDetalle(it) },
            onLongClick = { mostrarOpciones(it) }
        )
        binding.recyclerEstimaciones.apply {
            layoutManager = LinearLayoutManager(this@CostosActivity)
            adapter = this@CostosActivity.adapter
            isNestedScrollingEnabled = false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  BOTONES Y FILTROS
    // ══════════════════════════════════════════════════════════════════

    private fun configurarBotones() {
        binding.fabNuevaEstimacion.setOnClickListener {
            abrirFormulario(null)
        }
        binding.btnFiltroTodas.setOnClickListener { viewModel.setFiltroEstado(null); marcarFiltro(0) }
        binding.btnFiltroBorrador.setOnClickListener { viewModel.setFiltroEstado(EstadoEstimacion.BORRADOR.name); marcarFiltro(1) }
        binding.btnFiltroActiva.setOnClickListener { viewModel.setFiltroEstado(EstadoEstimacion.ACTIVA.name); marcarFiltro(2) }
        binding.btnFiltroCompletada.setOnClickListener { viewModel.setFiltroEstado(EstadoEstimacion.COMPLETADA.name); marcarFiltro(3) }
    }

    private fun marcarFiltro(index: Int) {
        val btns = listOf(binding.btnFiltroTodas, binding.btnFiltroBorrador, binding.btnFiltroActiva, binding.btnFiltroCompletada)
        btns.forEachIndexed { i, btn ->
            btn.setBackgroundResource(if (i == index) R.drawable.bg_tab_activo else R.drawable.bg_tab_inactivo)
            btn.setTextColor(getColor(if (i == index) R.color.blanco else R.color.verde_primario))
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  OBSERVADORES
    // ══════════════════════════════════════════════════════════════════

    private fun observarViewModel() {
        viewModel.estimacionesFiltradas.observe(this) { lista ->
            adapter.submitList(lista)
            binding.tvSinEstimaciones.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerEstimaciones.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
            binding.tvContadorEstimaciones.text = "${lista.size} estimaciones"
        }

        viewModel.estimaciones.observe(this) { lista ->
            val metricas = viewModel.calcularMetricas(lista)
            binding.tvMetricaTotal.text = "Q${String.format("%.2f", metricas.costoTotalAcumulado)}"
            binding.tvMetricaRoi.text = "${String.format("%.1f", metricas.roiPromedio)}%"
            binding.tvMetricaRentables.text = "${metricas.estimacionesRentables}"
            binding.tvMetricaCantidad.text = "${metricas.totalEstimaciones}"
        }

        viewModel.mensaje.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.cargando.observe(this) { loading ->
            binding.progressCostos.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  OPCIONES EN LONG PRESS
    // ══════════════════════════════════════════════════════════════════

    private fun mostrarOpciones(e: EstimacionCostos) {
        val opciones = mutableListOf("Ver detalle", "Editar", "Duplicar", "Enviar a Finanzas", "Eliminar")
        if (e.estado == EstadoEstimacion.BORRADOR.name) opciones.add(2, "Activar producción")

        AlertDialog.Builder(this)
            .setTitle(e.loteNombre.ifEmpty { "Estimación" })
            .setItems(opciones.toTypedArray()) { _, cual ->
                when (opciones[cual]) {
                    "Ver detalle" -> abrirDetalle(e)
                    "Editar" -> abrirFormulario(e)
                    "Duplicar" -> viewModel.duplicarEstimacion(e.id)
                    "Enviar a Finanzas" -> viewModel.enviarCostoAFinanzas(e)
                    "Activar producción" -> confirmarActivarProduccion(e)
                    "Eliminar" -> confirmarEliminar(e)
                }
            }.show()
    }

    private fun confirmarEliminar(e: EstimacionCostos) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar estimación")
            .setMessage("¿Eliminar esta estimación? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarEstimacion(e.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarActivarProduccion(e: EstimacionCostos) {
        AlertDialog.Builder(this)
            .setTitle("Activar producción")
            .setMessage("Esto descontará los insumos del inventario y marcará la estimación como Activa. ¿Continuar?")
            .setPositiveButton("Activar") { _, _ -> viewModel.activarProduccion(e) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  DETALLE (vista de resultados)
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDetalle(e: EstimacionCostos) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_detalle_estimacion, null)
        sheet.setContentView(view)

        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        view.findViewById<TextView>(R.id.tvDetLote).text = e.loteNombre.ifEmpty { "Sin lote" }
        view.findViewById<TextView>(R.id.tvDetTipo).text = e.tipoAveDisplay()
        view.findViewById<TextView>(R.id.tvDetAves).text = "${e.cantidadAves} aves"
        view.findViewById<TextView>(R.id.tvDetDias).text = "${e.diasCrianza} días"
        view.findViewById<TextView>(R.id.tvDetFecha).text = sdf.format(Date(e.fechaCreacion))
        view.findViewById<TextView>(R.id.tvDetCostoAlim).text = "Q${String.format("%.2f", e.costoAlimentacionTotal)}"
        view.findViewById<TextView>(R.id.tvDetCostoSan).text = "Q${String.format("%.2f", e.costoSanitarioTotal)}"
        view.findViewById<TextView>(R.id.tvDetCostoOp).text = "Q${String.format("%.2f", e.costoOperativoTotal)}"
        view.findViewById<TextView>(R.id.tvDetMortalidad).text = "Q${String.format("%.2f", e.perdidaMortalidad)} (${String.format("%.1f", e.porcentajeMortalidad)}%)"
        view.findViewById<TextView>(R.id.tvDetCostoTotal).text = "Q${String.format("%.2f", e.costoTotal)}"
        view.findViewById<TextView>(R.id.tvDetCostoPorAve).text = "Q${String.format("%.2f", e.costoPorAve)}"
        view.findViewById<TextView>(R.id.tvDetIngreso).text = "Q${String.format("%.2f", e.ingresoEstimado)}"

        val tvGanancia = view.findViewById<TextView>(R.id.tvDetGanancia)
        tvGanancia.text = "Q${String.format("%.2f", e.gananciaNeta)}"
        tvGanancia.setTextColor(getColor(if (e.isRentable()) R.color.verde_primario else R.color.rojo_salir))

        view.findViewById<TextView>(R.id.tvDetRoi).text = "${String.format("%.1f", e.roi)}%"
        view.findViewById<TextView>(R.id.tvDetPE).text = "${String.format("%.0f", e.puntoEquilibrioUnidades)} aves"

        // Comparación real vs estimado
        val seccionReal = view.findViewById<View>(R.id.cardComparacion)
        if (e.costoRealRegistrado > 0) {
            seccionReal.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvDetCostoReal).text = "Q${String.format("%.2f", e.costoRealRegistrado)}"
            view.findViewById<TextView>(R.id.tvDetVariacion).text = "${String.format("%.1f", e.variacionPorcentaje)}%"
            view.findViewById<TextView>(R.id.tvDetEficiencia).text = e.eficienciaEstimacion()
            view.findViewById<TextView>(R.id.tvDetDiferencia).text = "Q${String.format("%.2f", e.diferenciaRealEstimado())}"
        } else {
            seccionReal.visibility = View.GONE
        }

        // Alertas
        val tvAlertas = view.findViewById<TextView>(R.id.tvDetAlertas)
        val cardAlertas = view.findViewById<View>(R.id.cardAlertas)
        if (e.tieneAlertas()) {
            cardAlertas.visibility = View.VISIBLE
            tvAlertas.text = e.alertas.joinToString("\n")
        } else {
            cardAlertas.visibility = View.GONE
        }

        // Botones
        view.findViewById<View>(R.id.btnDetEditar).setOnClickListener {
            sheet.dismiss(); abrirFormulario(e)
        }
        view.findViewById<View>(R.id.btnDetFinanzas).setOnClickListener {
            viewModel.enviarCostoAFinanzas(e); sheet.dismiss()
        }
        view.findViewById<View>(R.id.btnDetCostoReal).setOnClickListener {
            sheet.dismiss(); abrirDialogCostoReal(e)
        }
        view.findViewById<View>(R.id.btnDetCerrar).setOnClickListener { sheet.dismiss() }

        sheet.show()
    }

    private fun abrirDialogCostoReal(e: EstimacionCostos) {
        val view = layoutInflater.inflate(R.layout.dialog_costo_real, null)
        val et = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etCostoReal)
        et.setText(if (e.costoRealRegistrado > 0) String.format("%.2f", e.costoRealRegistrado) else "")

        AlertDialog.Builder(this)
            .setTitle("Registrar costo real")
            .setView(view)
            .setPositiveButton("Guardar") { _, _ ->
                val costoReal = et.text.toString().toDoubleOrNull() ?: 0.0
                if (costoReal > 0) viewModel.registrarCostoReal(e, costoReal)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  FORMULARIO NUEVA/EDITAR ESTIMACIÓN
    // ══════════════════════════════════════════════════════════════════

    private fun abrirFormulario(estimacion: EstimacionCostos?) {
        val intent = Intent(this, CostosFormActivity::class.java)
        estimacion?.let { intent.putExtra("estimacion_id", it.id) }
        startActivity(intent)
    }

    // ══════════════════════════════════════════════════════════════════
    //  NAVEGACIÓN
    // ══════════════════════════════════════════════════════════════════

    private fun configurarNavegacion() {
        binding.navInicio.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java)); finish()
        }
        binding.btnSalirNavCostos.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java)); finish()
        }
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    override fun onBackPressed() {
        startActivity(Intent(this, DashboardActivity::class.java)); finish()
    }
}
