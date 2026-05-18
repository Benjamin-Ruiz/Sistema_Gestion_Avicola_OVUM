package com.universidad.avicola.ui.costos

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.universidad.avicola.R
import com.universidad.avicola.data.model.*
import com.universidad.avicola.data.repository.CostosCalculator
import com.universidad.avicola.databinding.ActivityCostosFormBinding
import java.util.UUID

/**
 * CostosFormActivity.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/costos/
 *
 * Formulario completo de estimación con cálculo en tiempo real.
 * El usuario configura todos los parámetros y los resultados
 * se actualizan automáticamente al cambiar cualquier valor.
 *
 * Fix v2:
 *  - Al abrir el formulario y al volver a primer plano, se fuerza un refresh
 *    inmediato del inventario desde Firestore (con reconciliación de borrados),
 *    para que los spinners de alimentos y medicinas reflejen únicamente lo
 *    que existe actualmente en el módulo de Inventario.
 */
class CostosFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCostosFormBinding
    private val viewModel: CostosViewModel by viewModels()

    // Estado local del formulario
    private var loteSeleccionado: Lote? = null
    private var tipoAve: TipoAveEstimacion = TipoAveEstimacion.ENGORDE
    private val fasesActuales = mutableListOf<FaseAlimentacion>()
    private val itemsSanitarios = mutableListOf<ItemSanitario>()
    private val costosOperativos = mutableListOf<CostoOperativo>()
    private var estimacionId: String = ""
    private var productosDisponibles: List<ProductoInventario> = emptyList()

    // Adapters para los spinners de fases y productos
    private lateinit var loteAdapter: ArrayAdapter<String>
    private lateinit var faseAdapter: FaseAlimentacionAdapter
    private lateinit var sanitarioAdapter: ItemSanitarioAdapter
    private lateinit var operativoAdapter: CostoOperativoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCostosFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        estimacionId = intent.getStringExtra("estimacion_id") ?: ""

        configurarLoteSpinner()
        configurarTipoAve()
        configurarFases()
        configurarSanitarios()
        configurarOperativos()
        configurarWatchers()
        configurarBotones()
        observarViewModel()

        if (estimacionId.isNotEmpty()) cargarEstimacionExistente()

        // FIX: refrescar inventario apenas se abre el formulario para
        // limpiar productos fantasma antes de que se llenen los spinners.
        viewModel.recargarInventario()
    }

    override fun onResume() {
        super.onResume()
        // FIX: si el usuario fue al módulo de Inventario a agregar o eliminar
        // productos y vuelve aquí, garantizar que los spinners reflejen el
        // estado actual del inventario.
        viewModel.recargarInventario()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CONFIGURACIÓN INICIAL
    // ═══════════════════════════════════════════════════════════════════════

    private fun configurarLoteSpinner() {
        loteAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf("Sin lote asignado"))
        loteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLote.adapter = loteAdapter
        binding.spinnerLote.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val lotes = viewModel.lotes.value ?: emptyList()
                loteSeleccionado = if (pos > 0) lotes.getOrNull(pos - 1) else null
                loteSeleccionado?.let { lote ->
                    // Auto-rellenar cantidad si el campo está vacío
                    if (binding.etCantidadAves.text.isNullOrEmpty()) {
                        binding.etCantidadAves.setText(lote.cantidadActual.toString())
                    }
                }
                recalcular()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun configurarTipoAve() {
        val tipos = TipoAveEstimacion.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tipos)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTipoAve.adapter = adapter
        binding.spinnerTipoAve.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                tipoAve = TipoAveEstimacion.values()[pos]
                // Actualizar fases por defecto solo si no hay fases personalizadas
                if (fasesActuales.isEmpty() || fasesActuales.all { it.productoInventarioId.isEmpty() }) {
                    fasesActuales.clear()
                    fasesActuales.addAll(viewModel.fasesDefaultParaTipo(tipoAve))
                    faseAdapter.notifyDataSetChanged()
                }
                recalcular()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun configurarFases() {
        fasesActuales.addAll(viewModel.fasesDefaultParaTipo(TipoAveEstimacion.ENGORDE))
        faseAdapter = FaseAlimentacionAdapter(
            fases = fasesActuales,
            productos = productosDisponibles,
            onFaseChanged = { recalcular() }
        )
        binding.recyclerFases.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@CostosFormActivity)
            adapter = faseAdapter
            isNestedScrollingEnabled = false
        }
        binding.btnAgregarFase.setOnClickListener {
            fasesActuales.add(FaseAlimentacion("Nueva fase", 14, 100.0, 0.0))
            faseAdapter.notifyItemInserted(fasesActuales.size - 1)
            recalcular()
        }
    }

    private fun configurarSanitarios() {
        sanitarioAdapter = ItemSanitarioAdapter(
            items = itemsSanitarios,
            productos = productosDisponibles,
            onChanged = { recalcular() }
        )
        binding.recyclerSanitarios.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@CostosFormActivity)
            adapter = sanitarioAdapter
            isNestedScrollingEnabled = false
        }
        binding.btnAgregarSanitario.setOnClickListener {
            itemsSanitarios.add(ItemSanitario("Nuevo ítem", TipoSanitario.VACUNA.name, 1.0, 0.0))
            sanitarioAdapter.notifyItemInserted(itemsSanitarios.size - 1)
            recalcular()
        }
    }

    private fun configurarOperativos() {
        costosOperativos.addAll(CostosCalculator.costosOperativosDefault())
        operativoAdapter = CostoOperativoAdapter(
            items = costosOperativos,
            onChanged = { recalcular() }
        )
        binding.recyclerOperativos.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@CostosFormActivity)
            adapter = operativoAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun configurarWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { recalcular() }
        }
        binding.etCantidadAves.addTextChangedListener(watcher)
        binding.etDiasCrianza.addTextChangedListener(watcher)
        binding.etPrecioVenta.addTextChangedListener(watcher)
        binding.etMortalidad.addTextChangedListener(watcher)
        binding.etCostoAveInicial.addTextChangedListener(watcher)
    }

    private fun configurarBotones() {
        binding.btnGuardarEstimacion.setOnClickListener { guardar() }
        binding.btnCancelarForm.setOnClickListener { finish() }

        // Colapsar/expandir secciones
        binding.headerAlimentacion.setOnClickListener {
            toggleSection(binding.seccionAlimentacion)
        }
        binding.headerSanitario.setOnClickListener {
            toggleSection(binding.seccionSanitario)
        }
        binding.headerOperativo.setOnClickListener {
            toggleSection(binding.seccionOperativo)
        }
        binding.headerResultados.setOnClickListener {
            toggleSection(binding.seccionResultados)
        }
    }

    private fun toggleSection(view: View) {
        view.visibility = if (view.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  OBSERVADORES
    // ═══════════════════════════════════════════════════════════════════════

    private fun observarViewModel() {
        viewModel.lotes.observe(this) { lotes ->
            val nombres = mutableListOf("Sin lote asignado")
            nombres.addAll(lotes.map { "${it.lineaGenetica} — ${it.cantidadActual} aves (${it.propositoDisplay()})" })
            loteAdapter.clear()
            loteAdapter.addAll(nombres)
            loteAdapter.notifyDataSetChanged()
        }

        viewModel.productos.observe(this) { productos ->
            // El Flow emite cada vez que Room cambia (incluido cuando el sync
            // reconcilia y elimina productos obsoletos), por lo que los spinners
            // siempre reflejarán el estado real del inventario.
            productosDisponibles = productos
            faseAdapter.actualizarProductos(productos)
            sanitarioAdapter.actualizarProductos(productos)

            // Limpiar vínculos huérfanos: si una fase apunta a un producto que
            // ya no existe, desvincularla (evita guardar IDs muertos).
            limpiarVinculosHuerfanos(productos)
        }

        viewModel.resultado.observe(this) { r ->
            r ?: return@observe
            actualizarResultados(r)
        }

        viewModel.mensaje.observe(this) { msg ->
            if (msg.isNotEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (msg.contains("guardada")) finish()
            }
        }
    }

    /**
     * Si una fase o ítem sanitario tiene vinculado un producto que ya no existe
     * en el inventario actual, lo desvincula automáticamente. Esto previene
     * guardar estimaciones con IDs fantasma y mantiene la consistencia.
     */
    private fun limpiarVinculosHuerfanos(productosActuales: List<ProductoInventario>) {
        val idsValidos = productosActuales.map { it.id }.toSet()
        var huboCambios = false

        for (i in fasesActuales.indices) {
            val fase = fasesActuales[i]
            if (fase.productoInventarioId.isNotEmpty() && fase.productoInventarioId !in idsValidos) {
                fasesActuales[i] = fase.copy(
                    productoInventarioId = "",
                    productoNombre = "",
                    precioKg = 0.0,
                    stockDisponible = 0.0
                )
                huboCambios = true
            }
        }
        for (i in itemsSanitarios.indices) {
            val item = itemsSanitarios[i]
            if (item.productoInventarioId.isNotEmpty() && item.productoInventarioId !in idsValidos) {
                itemsSanitarios[i] = item.copy(
                    productoInventarioId = "",
                    precioUnitario = 0.0,
                    stockDisponible = 0.0
                )
                huboCambios = true
            }
        }
        if (huboCambios) {
            faseAdapter.notifyDataSetChanged()
            sanitarioAdapter.notifyDataSetChanged()
            recalcular()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CÁLCULO EN TIEMPO REAL
    // ═══════════════════════════════════════════════════════════════════════

    private fun recalcular() {
        val cantidad = binding.etCantidadAves.text.toString().toIntOrNull() ?: return
        val dias = binding.etDiasCrianza.text.toString().toIntOrNull() ?: 0
        val precioVenta = binding.etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
        val mortalidad = binding.etMortalidad.text.toString().toDoubleOrNull() ?: 5.0
        val costoAveInicial = binding.etCostoAveInicial.text.toString().toDoubleOrNull() ?: 0.0

        viewModel.recalcular(
            cantidadAves = cantidad,
            diasCrianza = dias,
            fases = fasesActuales.toList(),
            itemsSanitarios = itemsSanitarios.toList(),
            costosOperativos = costosOperativos.toList(),
            porcentajeMortalidad = mortalidad,
            precioVentaUnitario = precioVenta,
            costoAveInicial = costoAveInicial
        )
    }

    private fun actualizarResultados(r: ResultadoCalculo) {
        binding.tvResAlimentacion.text = "Q${String.format("%.2f", r.costoAlimentacion)}"
        binding.tvResSanitario.text = "Q${String.format("%.2f", r.costoSanitario)}"
        binding.tvResOperativo.text = "Q${String.format("%.2f", r.costoOperativo)}"
        binding.tvResMortalidad.text = "Q${String.format("%.2f", r.perdidaMortalidad)}"
        binding.tvResCostoTotal.text = "Q${String.format("%.2f", r.costoTotal)}"
        binding.tvResCostoPorAve.text = "Q${String.format("%.2f", r.costoPorAve)}"
        binding.tvResCostoDiario.text = "Q${String.format("%.2f", r.costoDiario)}"
        binding.tvResCostoSemanal.text = "Q${String.format("%.2f", r.costoSemanal)}"
        binding.tvResCostoMensual.text = "Q${String.format("%.2f", r.costoMensual)}"
        binding.tvResIngreso.text = "Q${String.format("%.2f", r.ingresoEstimado)}"

        val colorGanancia = if (r.gananciaNeta >= 0) R.color.verde_primario else R.color.rojo_salir
        binding.tvResGanancia.text = "Q${String.format("%.2f", r.gananciaNeta)}"
        binding.tvResGanancia.setTextColor(getColor(colorGanancia))

        binding.tvResRoi.text = "${String.format("%.1f", r.roi)}%"
        binding.tvResRoi.setTextColor(getColor(colorGanancia))

        binding.tvResPuntoEquilibrio.text = "${String.format("%.0f", r.puntoEquilibrioUnidades)} aves"

        // Alertas en tiempo real
        if (r.alertas.isNotEmpty()) {
            binding.cardAlertasForm.visibility = View.VISIBLE
            binding.tvAlertasForm.text = r.alertas.joinToString("\n")
        } else {
            binding.cardAlertasForm.visibility = View.GONE
        }

        // Alerta de stock insuficiente
        if (r.stockInsuficiente.isNotEmpty()) {
            binding.cardStockAlerta.visibility = View.VISIBLE
            binding.tvStockAlerta.text = "⛔ Stock insuficiente:\n${r.stockInsuficiente.joinToString("\n• ", "• ")}"
        } else {
            binding.cardStockAlerta.visibility = View.GONE
        }

        // Asegurarse que la sección de resultados esté visible
        binding.seccionResultados.visibility = View.VISIBLE
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  GUARDAR
    // ═══════════════════════════════════════════════════════════════════════

    private fun guardar() {
        val cantidad = binding.etCantidadAves.text.toString().toIntOrNull()
        if (cantidad == null || cantidad <= 0) {
            Toast.makeText(this, "Ingresa la cantidad de aves", Toast.LENGTH_SHORT).show()
            return
        }
        val dias = binding.etDiasCrianza.text.toString().toIntOrNull() ?: 0
        val precioVenta = binding.etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
        val mortalidad = binding.etMortalidad.text.toString().toDoubleOrNull() ?: 5.0
        val costoAveInicial = binding.etCostoAveInicial.text.toString().toDoubleOrNull() ?: 0.0
        val r = viewModel.resultado.value

        val estimacion = EstimacionCostos(
            id = if (estimacionId.isEmpty()) UUID.randomUUID().toString() else estimacionId,
            loteId = loteSeleccionado?.id ?: "",
            loteNombre = loteSeleccionado?.lineaGenetica
                ?: binding.etNombreEstimacion.text.toString().trim().ifEmpty { "Estimación sin nombre" },
            tipoAve = tipoAve.name,
            cantidadAves = cantidad,
            diasCrianza = dias,
            pesoObjetivoKg = binding.etPesoObjetivo.text.toString().toDoubleOrNull() ?: 0.0,
            fases = fasesActuales.toList(),
            itemsSanitarios = itemsSanitarios.toList(),
            costosOperativos = costosOperativos.toList(),
            porcentajeMortalidad = mortalidad,
            precioVentaUnitario = precioVenta,
            costoAlimentacionTotal = r?.costoAlimentacion ?: 0.0,
            costoSanitarioTotal = r?.costoSanitario ?: 0.0,
            costoOperativoTotal = r?.costoOperativo ?: 0.0,
            perdidaMortalidad = r?.perdidaMortalidad ?: 0.0,
            costoTotal = r?.costoTotal ?: 0.0,
            costoPorAve = r?.costoPorAve ?: 0.0,
            ingresoEstimado = r?.ingresoEstimado ?: 0.0,
            gananciaNeta = r?.gananciaNeta ?: 0.0,
            roi = r?.roi ?: 0.0,
            puntoEquilibrioUnidades = r?.puntoEquilibrioUnidades ?: 0.0,
            alertas = r?.alertas ?: emptyList(),
            notas = binding.etNotasEstimacion.text.toString().trim(),
            estado = EstadoEstimacion.BORRADOR.name
        )

        viewModel.guardarEstimacion(estimacion)
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CARGAR ESTIMACIÓN EXISTENTE
    // ═══════════════════════════════════════════════════════════════════════

    private fun cargarEstimacionExistente() {
        viewModel.estimaciones.observe(this) { lista ->
            val e = lista.firstOrNull { it.id == estimacionId } ?: return@observe
            binding.etNombreEstimacion.setText(e.loteNombre)
            binding.etCantidadAves.setText(e.cantidadAves.toString())
            binding.etDiasCrianza.setText(e.diasCrianza.toString())
            binding.etPesoObjetivo.setText(if (e.pesoObjetivoKg > 0) String.format("%.2f", e.pesoObjetivoKg) else "")
            binding.etPrecioVenta.setText(if (e.precioVentaUnitario > 0) String.format("%.2f", e.precioVentaUnitario) else "")
            binding.etMortalidad.setText(String.format("%.1f", e.porcentajeMortalidad))
            binding.etNotasEstimacion.setText(e.notas)

            // Tipo de ave
            val tipoIdx = TipoAveEstimacion.values().indexOfFirst { it.name == e.tipoAve }
            if (tipoIdx >= 0) binding.spinnerTipoAve.setSelection(tipoIdx)

            // Fases
            fasesActuales.clear()
            fasesActuales.addAll(e.fases)
            faseAdapter.notifyDataSetChanged()

            // Sanitarios
            itemsSanitarios.clear()
            itemsSanitarios.addAll(e.itemsSanitarios)
            sanitarioAdapter.notifyDataSetChanged()

            // Operativos
            costosOperativos.clear()
            costosOperativos.addAll(e.costosOperativos)
            operativoAdapter.notifyDataSetChanged()

            recalcular()
        }
    }
}