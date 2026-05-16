package com.universidad.avicola.ui.salud

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.universidad.avicola.R
import com.universidad.avicola.data.model.*
import com.universidad.avicola.databinding.ActivitySaludBinding
import com.universidad.avicola.ui.dashboard.DashboardActivity
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  SaludActivity.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/ui/salud/
// ─────────────────────────────────────────────────────────────────────────────

class SaludActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySaludBinding
    private val viewModel: SaludViewModel by viewModels()
    private lateinit var loteAdapter: EstadoSanitarioAdapter
    private lateinit var registroAdapter: RegistroMedicoAdapter
    private lateinit var vacunaAdapter: VacunacionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaludBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarRecyclers()
        configurarTabs()
        configurarBotones()
        configurarNavegacion()
        observarViewModel()
    }

    // ══════════════════════════════════════════════════════════════════
    //  RECYCLERS
    // ══════════════════════════════════════════════════════════════════

    private fun configurarRecyclers() {
        loteAdapter = EstadoSanitarioAdapter(
            onClick      = { abrirDetalleLote(it) },
            onLongClick  = { mostrarOpcionesLote(it) }
        )
        registroAdapter = RegistroMedicoAdapter(
            onClick     = { abrirDetalleRegistro(it) },
            onResolver  = { viewModel.marcarResuelta(it.id) },
            onEliminar  = { confirmarEliminarRegistro(it) }
        )
        vacunaAdapter = VacunacionAdapter(
            onClick    = { abrirDetalleVacuna(it) },
            onAplicar  = { viewModel.aplicarVacuna(it) },
            onEliminar = { confirmarEliminarVacuna(it) }
        )
        binding.recyclerLotes.apply {
            layoutManager = LinearLayoutManager(this@SaludActivity)
            adapter = loteAdapter; isNestedScrollingEnabled = false
        }
        binding.recyclerRegistros.apply {
            layoutManager = LinearLayoutManager(this@SaludActivity)
            adapter = registroAdapter; isNestedScrollingEnabled = false
        }
        binding.recyclerVacunas.apply {
            layoutManager = LinearLayoutManager(this@SaludActivity)
            adapter = vacunaAdapter; isNestedScrollingEnabled = false
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  TABS
    // ══════════════════════════════════════════════════════════════════

    private fun configurarTabs() {
        fun seleccionar(idx: Int) {
            val tabs = listOf(binding.btnTabLotes, binding.btnTabRegistros,
                binding.btnTabVacunas, binding.btnTabAlertas)
            tabs.forEachIndexed { i, btn ->
                btn.setBackgroundResource(if (i == idx) R.drawable.bg_tab_activo else R.drawable.bg_tab_inactivo)
                btn.setTextColor(getColor(if (i == idx) R.color.blanco else R.color.verde_primario))
            }
            binding.sectionLotes.visibility    = if (idx == 0) View.VISIBLE else View.GONE
            binding.sectionRegistros.visibility = if (idx == 1) View.VISIBLE else View.GONE
            binding.sectionVacunas.visibility  = if (idx == 2) View.VISIBLE else View.GONE
            binding.sectionAlertas.visibility  = if (idx == 3) View.VISIBLE else View.GONE
        }
        seleccionar(0)
        binding.btnTabLotes.setOnClickListener    { seleccionar(0); viewModel.setTab(SaludViewModel.TabSalud.LOTES) }
        binding.btnTabRegistros.setOnClickListener { seleccionar(1); viewModel.setTab(SaludViewModel.TabSalud.REGISTROS) }
        binding.btnTabVacunas.setOnClickListener  { seleccionar(2); viewModel.setTab(SaludViewModel.TabSalud.VACUNAS) }
        binding.btnTabAlertas.setOnClickListener  { seleccionar(3); viewModel.setTab(SaludViewModel.TabSalud.ALERTAS) }
    }

    // ══════════════════════════════════════════════════════════════════
    //  BOTONES
    // ══════════════════════════════════════════════════════════════════

    private fun configurarBotones() {
        binding.fabNuevoRegistro.setOnClickListener { abrirFormRegistro(null, null) }
        binding.btnNuevaVacuna.setOnClickListener   { abrirFormVacuna(null, null) }
        binding.btnDiagnostico.setOnClickListener   { abrirDiagnosticoAsistido() }
    }

    // ══════════════════════════════════════════════════════════════════
    //  OBSERVADORES
    // ══════════════════════════════════════════════════════════════════

    private fun observarViewModel() {
        viewModel.estadosSanitarios.observe(this) { estados ->
            loteAdapter.submitList(estados)
            binding.tvSinLotes.visibility = if (estados.isEmpty()) View.VISIBLE else View.GONE

            val resumen = viewModel.calcularResumen()
            binding.tvResumenEnRiesgo.text    = "${resumen.lotesEnRiesgo}"
            binding.tvResumenUrgentes.text    = "${resumen.casosUrgentes}"
            binding.tvResumenVacunas.text     = "${resumen.vacunasPendientes}"
            binding.tvResumenCosto.text       = "Q${String.format("%.2f", resumen.costoSanitarioTotal)}"

            // Alerta crítica si hay lotes en riesgo
            binding.cardEmergencia.visibility =
                if (resumen.lotesEnRiesgo > 0 || resumen.casosUrgentes > 0) View.VISIBLE else View.GONE
            binding.tvEmergencia.text =
                "⛔ ${resumen.lotesEnRiesgo} lote(s) en riesgo · ${resumen.casosUrgentes} caso(s) urgente(s)"
        }

        viewModel.registros.observe(this) { registros ->
            registroAdapter.submitList(registros)
            binding.tvSinRegistros.visibility = if (registros.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.vacunaciones.observe(this) { vacunas ->
            vacunaAdapter.submitList(vacunas)
            binding.tvSinVacunas.visibility = if (vacunas.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.registrosUrgentes.observe(this) { urgentes ->
            val alertAdapter = RegistroMedicoAdapter(
                onClick    = { abrirDetalleRegistro(it) },
                onResolver = { viewModel.marcarResuelta(it.id) },
                onEliminar = { confirmarEliminarRegistro(it) }
            )
            binding.recyclerAlertas.layoutManager = LinearLayoutManager(this)
            binding.recyclerAlertas.adapter = alertAdapter
            alertAdapter.submitList(urgentes)
            binding.tvSinAlertas.visibility = if (urgentes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.vacunacionesPendientes.observe(this) { pendientes ->
            binding.badgeVacunas.visibility = if (pendientes.isNotEmpty()) View.VISIBLE else View.GONE
            binding.badgeVacunas.text = "${pendientes.size}"
        }

        viewModel.mensaje.observe(this) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        viewModel.cargando.observe(this) { loading ->
            binding.progressSalud.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  DETALLE LOTE SANITARIO
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDetalleLote(estado: EstadoSanitarioLote) {
        val sdf          = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        val registros    = viewModel.registrosPorLote(estado.loteId)
        val vacunaciones = viewModel.vacunacionesPorLote(estado.loteId)

        val resumen = buildString {
            append("Aves actuales: ${estado.cantidadAves}\n")
            append("Bajas totales: ${estado.mortalidadTotal} ")
            append("(${String.format("%.1f", estado.porcentajeMortalidad)}%)\n")
            append("Estado sanitario: ${estado.estadoDisplay()}\n")
            append("Costo sanitario: Q${String.format("%.2f", estado.costoSanitarioTotal)}\n")
            append("Vacunas pendientes: ${estado.vacunasPendientes}\n")
            append("Casos activos: ${estado.tratamientosActivos}\n")
            if (estado.tieneAlertas()) {
                append("\nAlertas:\n")
                estado.alertas.forEach { alerta -> append("  $alerta\n") }
            }
            if (registros.isNotEmpty()) {
                append("\nUltimos registros:\n")
                registros.take(3).forEach { r ->
                    append("  - ${r.tipoDisplay()} / ${r.gravedadDisplay()}\n")
                }
            }
            if (vacunaciones.isNotEmpty()) {
                append("\nVacunaciones:\n")
                vacunaciones.take(3).forEach { v ->
                    val est = if (v.aplicada) "Aplicada" else "Pendiente"
                    append("  - ${v.nombreVacuna}: $est\n")
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Lote: ${estado.loteNombre}")
            .setMessage(resumen)
            .setPositiveButton("Nuevo registro") { _, _ ->
                abrirFormRegistro(estado.loteId, estado.loteNombre)
            }
            .setNeutralButton("Nueva vacuna") { _, _ ->
                abrirFormVacuna(estado.loteId, estado.loteNombre)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun mostrarOpcionesLote(estado: EstadoSanitarioLote) {
        val opciones = arrayOf("Nuevo registro médico", "Nueva vacunación", "Ver historial")
        AlertDialog.Builder(this)
            .setTitle(estado.loteNombre)
            .setItems(opciones) { _, cual ->
                when (cual) {
                    0 -> abrirFormRegistro(estado.loteId, estado.loteNombre)
                    1 -> abrirFormVacuna(estado.loteId, estado.loteNombre)
                    2 -> abrirDetalleLote(estado)
                }
            }.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  FORMULARIO REGISTRO MÉDICO
    // ══════════════════════════════════════════════════════════════════

    private fun abrirFormRegistro(loteIdPresel: String?, loteNombrePresel: String?) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_registro_medico, null)
        sheet.setContentView(view)

        val spinnerLote     = view.findViewById<Spinner>(R.id.spinnerRegistroLote)
        val spinnerTipo     = view.findViewById<Spinner>(R.id.spinnerRegistroTipo)
        val spinnerGravedad = view.findViewById<Spinner>(R.id.spinnerRegistroGravedad)
        val spinnerMed      = view.findViewById<Spinner>(R.id.spinnerRegistroMedicamento)
        val etDescripcion   = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroDescripcion)
        val etEnfermedad    = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroEnfermedad)
        val etTratamiento   = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroTratamiento)
        val etDosis         = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroDosis)
        val etAvesAfectadas = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroAvesAfectadas)
        val etCosto         = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroCosto)
        val etResponsable   = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etRegistroResponsable)
        val btnGuardar      = view.findViewById<View>(R.id.btnGuardarRegistro)
        val btnCerrar       = view.findViewById<View>(R.id.btnCerrarRegistro)

        // Lotes
        val lotes = viewModel.lotes.value ?: emptyList()
        val nombresLotes = mutableListOf("Seleccionar lote")
        nombresLotes.addAll(lotes.map { "${it.lineaGenetica} (${it.cantidadActual} aves)" })
        spinnerLote.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nombresLotes)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        if (loteIdPresel != null) {
            val idx = lotes.indexOfFirst { it.id == loteIdPresel }
            if (idx >= 0) spinnerLote.setSelection(idx + 1)
        }

        // Tipos y gravedad
        spinnerTipo.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            TipoRegistroMedico.values().map { it.displayName })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerGravedad.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            GravedadSanitaria.values().map { it.displayName })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Medicamentos del inventario
        val productos = viewModel.productosMedicos.value ?: emptyList()
        val opcionesMed = mutableListOf("Sin medicamento")
        opcionesMed.addAll(productos.map { "${it.nombre} (${it.cantidadConUnidad()})" })
        spinnerMed.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opcionesMed)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnCerrar.setOnClickListener { sheet.dismiss() }
        btnGuardar.setOnClickListener {
            val posLote = spinnerLote.selectedItemPosition
            if (posLote == 0) {
                Toast.makeText(this, "Selecciona un lote", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val lote = lotes[posLote - 1]
            val posMed = spinnerMed.selectedItemPosition
            val medSeleccionado = if (posMed > 0) productos[posMed - 1] else null

            val registro = RegistroMedico(
                loteId              = lote.id,
                loteNombre          = lote.lineaGenetica,
                galponId            = lote.galponId,
                tipo                = TipoRegistroMedico.values()[spinnerTipo.selectedItemPosition].name,
                gravedad            = GravedadSanitaria.values()[spinnerGravedad.selectedItemPosition].name,
                descripcion         = etDescripcion.text.toString().trim(),
                enfermedadSospechosa = etEnfermedad.text.toString().trim(),
                tratamientoAplicado = etTratamiento.text.toString().trim(),
                medicamentoId       = medSeleccionado?.id ?: "",
                medicamentoNombre   = medSeleccionado?.nombre ?: "",
                dosis               = etDosis.text.toString().trim(),
                avesAfectadas       = etAvesAfectadas.text.toString().toIntOrNull() ?: 0,
                costo               = etCosto.text.toString().toDoubleOrNull() ?: 0.0,
                responsable         = etResponsable.text.toString().trim(),
                fechaMs             = System.currentTimeMillis()
            )
            viewModel.guardarRegistro(registro)
            sheet.dismiss()
        }
        sheet.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  FORMULARIO VACUNACIÓN
    // ══════════════════════════════════════════════════════════════════

    private fun abrirFormVacuna(loteIdPresel: String?, loteNombrePresel: String?) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_vacunacion, null)
        sheet.setContentView(view)

        val spinnerLote    = view.findViewById<Spinner>(R.id.spinnerVacunaLote)
        val spinnerVia     = view.findViewById<Spinner>(R.id.spinnerVacunaVia)
        val spinnerMed     = view.findViewById<Spinner>(R.id.spinnerVacunaMedicamento)
        val etNombre       = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaNombre)
        val etEnfermedad   = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaEnfermedad)
        val etDosis        = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaDosis)
        val etAves         = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaAves)
        val etCosto        = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaCosto)
        val etResponsable  = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaResponsable)
        val etProxima      = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVacunaProxima)
        val btnGuardar     = view.findViewById<View>(R.id.btnGuardarVacuna)
        val btnCerrar      = view.findViewById<View>(R.id.btnCerrarVacuna)

        val lotes = viewModel.lotes.value ?: emptyList()
        val nombresLotes = mutableListOf("Seleccionar lote")
        nombresLotes.addAll(lotes.map { "${it.lineaGenetica} (${it.cantidadActual} aves)" })
        spinnerLote.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, nombresLotes)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        if (loteIdPresel != null) {
            val idx = lotes.indexOfFirst { it.id == loteIdPresel }
            if (idx >= 0) spinnerLote.setSelection(idx + 1)
        }

        spinnerVia.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            ViaAdministracion.values().map { it.displayName })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val productos = viewModel.productosMedicos.value ?: emptyList()
        val opcionesMed = mutableListOf("Sin medicamento")
        opcionesMed.addAll(productos.map { it.nombre })
        spinnerMed.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, opcionesMed)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Picker de fecha próxima vacunación
        var fechaProximaMs = 0L
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        etProxima.setOnClickListener {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(this, { _, y, m, d ->
                val sel = Calendar.getInstance().also { it.set(y, m, d) }
                fechaProximaMs = sel.timeInMillis
                etProxima.setText(sdf.format(sel.time))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnCerrar.setOnClickListener { sheet.dismiss() }
        btnGuardar.setOnClickListener {
            val posLote = spinnerLote.selectedItemPosition
            if (posLote == 0) {
                Toast.makeText(this, "Selecciona un lote", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val nombre = etNombre.text.toString().trim()
            if (nombre.isEmpty()) {
                Toast.makeText(this, "Ingresa el nombre de la vacuna", Toast.LENGTH_SHORT).show(); return@setOnClickListener
            }
            val lote = lotes[posLote - 1]
            val posMed = spinnerMed.selectedItemPosition
            val medSeleccionado = if (posMed > 0) productos[posMed - 1] else null

            val vacunacion = Vacunacion(
                loteId          = lote.id,
                loteNombre      = lote.lineaGenetica,
                nombreVacuna    = nombre,
                enfermedad      = etEnfermedad.text.toString().trim(),
                via             = ViaAdministracion.values()[spinnerVia.selectedItemPosition].name,
                dosis           = etDosis.text.toString().trim(),
                avesVacunadas   = etAves.text.toString().toIntOrNull() ?: lote.cantidadActual,
                costo           = etCosto.text.toString().toDoubleOrNull() ?: 0.0,
                medicamentoId   = medSeleccionado?.id ?: "",
                responsable     = etResponsable.text.toString().trim(),
                fechaProximaMs  = fechaProximaMs,
                aplicada        = false
            )
            viewModel.guardarVacunacion(vacunacion)
            sheet.dismiss()
        }
        sheet.show()
    }

    // ══════════════════════════════════════════════════════════════════
    //  DIAGNÓSTICO ASISTIDO
    // ══════════════════════════════════════════════════════════════════

    private fun abrirDiagnosticoAsistido() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_diagnostico, null)
        sheet.setContentView(view)

        val chipGroup   = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupSintomas)
        val tvResultado = view.findViewById<TextView>(R.id.tvDiagnosticoResultado)
        val btnAnalizar = view.findViewById<View>(R.id.btnAnalizarSintomas)
        val btnCerrar   = view.findViewById<View>(R.id.btnCerrarDiagnostico)

        // Crear chips de síntomas
        DiagnosticoAsistido.SINTOMAS_DISPONIBLES.forEach { sintoma ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = sintoma; isCheckable = true
                setChipBackgroundColorResource(R.color.verde_suave)
            }
            chipGroup.addView(chip)
        }

        btnAnalizar.setOnClickListener {
            val sintomasSeleccionados = (0 until chipGroup.childCount)
                .mapNotNull { chipGroup.getChildAt(it) as? com.google.android.material.chip.Chip }
                .filter { it.isChecked }
                .map { it.text.toString() }

            if (sintomasSeleccionados.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos un síntoma", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.analizarSintomas(sintomasSeleccionados)
            val sugerencias = viewModel.sugerencias.value ?: emptyList()
            if (sugerencias.isEmpty()) {
                tvResultado.text = "No se encontraron coincidencias para los síntomas seleccionados."
                return@setOnClickListener
            }

            val sb = StringBuilder()
            sugerencias.forEachIndexed { i, s ->
                sb.append("${i + 1}. ${s.enfermedad}\n")
                sb.append("   Probabilidad: ${s.probabilidad} | Urgencia: ${s.urgencia}\n")
                sb.append("   ${s.tratamientoSugerido}\n")
                if (s.medicamentosRecomendados.isNotEmpty())
                    sb.append("   Medicamentos: ${s.medicamentosRecomendados.joinToString(", ")}\n")
                sb.append("\n")
            }
            tvResultado.text = sb.toString()
        }
        btnCerrar.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun abrirDetalleRegistro(r: RegistroMedico) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        AlertDialog.Builder(this)
            .setTitle("${r.tipoDisplay()} — ${r.loteNombre}")
            .setMessage(buildString {
                append("Fecha: ${sdf.format(Date(r.fechaMs))}\n")
                append("Gravedad: ${r.gravedadDisplay()}\n")
                if (r.enfermedadSospechosa.isNotEmpty()) append("Enfermedad sospechosa: ${r.enfermedadSospechosa}\n")
                if (r.descripcion.isNotEmpty()) append("Descripción: ${r.descripcion}\n")
                if (r.tratamientoAplicado.isNotEmpty()) append("Tratamiento: ${r.tratamientoAplicado}\n")
                if (r.medicamentoNombre.isNotEmpty()) append("Medicamento: ${r.medicamentoNombre} — ${r.dosis}\n")
                if (r.avesAfectadas > 0) append("Aves afectadas: ${r.avesAfectadas}\n")
                if (r.costo > 0) append("Costo: Q${String.format("%.2f", r.costo)}\n")
                if (r.responsable.isNotEmpty()) append("Responsable: ${r.responsable}\n")
                append("Estado: ${if (r.resuelta) "✔ Resuelto" else "⏳ Pendiente"}")
            })
            .setPositiveButton(if (r.resuelta) "Cerrar" else "Marcar resuelto") { _, _ ->
                if (!r.resuelta) viewModel.marcarResuelta(r.id)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun abrirDetalleVacuna(v: Vacunacion) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        AlertDialog.Builder(this)
            .setTitle("${v.nombreVacuna} — ${v.loteNombre}")
            .setMessage(buildString {
                if (v.enfermedad.isNotEmpty()) append("Enfermedad: ${v.enfermedad}\n")
                append("Vía: ${v.viaDisplay()}\n")
                if (v.dosis.isNotEmpty()) append("Dosis: ${v.dosis}\n")
                append("Aves vacunadas: ${v.avesVacunadas}\n")
                if (v.costo > 0) append("Costo: Q${String.format("%.2f", v.costo)}\n")
                if (v.aplicada && v.fechaAplicacionMs > 0) append("Aplicada: ${sdf.format(Date(v.fechaAplicacionMs))}\n")
                if (!v.aplicada && v.fechaProximaMs > 0) append("Próxima: ${sdf.format(Date(v.fechaProximaMs))} (${v.diasParaProxima()} días)\n")
                if (v.responsable.isNotEmpty()) append("Responsable: ${v.responsable}\n")
                append("Estado: ${if (v.aplicada) "✔ Aplicada" else "⏳ Pendiente"}")
            })
            .setPositiveButton(if (v.aplicada) "Cerrar" else "Marcar aplicada") { _, _ ->
                if (!v.aplicada) viewModel.aplicarVacuna(v)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun confirmarEliminarRegistro(r: RegistroMedico) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar registro")
            .setMessage("¿Eliminar este registro médico? No se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarRegistro(r.id) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun confirmarEliminarVacuna(v: Vacunacion) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar vacunación")
            .setMessage("¿Eliminar este registro de vacunación?")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarVacunacion(v.id) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun configurarNavegacion() {
        binding.navInicio.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java)); finish()
        }
        binding.btnSalirNavSalud.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java)); finish()
        }
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    override fun onBackPressed() {
        startActivity(Intent(this, DashboardActivity::class.java)); finish()
    }
}