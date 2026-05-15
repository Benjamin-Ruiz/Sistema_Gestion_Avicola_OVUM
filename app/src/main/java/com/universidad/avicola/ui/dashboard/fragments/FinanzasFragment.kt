package com.universidad.avicola.ui.dashboard.fragments

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.R
import com.universidad.avicola.data.model.*
import com.universidad.avicola.databinding.*
import com.universidad.avicola.ui.finanzas.FinanzasViewModel
import com.universidad.avicola.ui.finanzas.TransaccionAdapter
import com.universidad.avicola.util.animateNumber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinanzasFragment : Fragment() {

    private var _binding: FragmentFinanzasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FinanzasViewModel by viewModels()
    private lateinit var adapter: TransaccionAdapter
    private var transaccionEditando: Transaccion? = null
    private var fotoUriSeleccionada: Uri? = null
    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private val fotoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            fotoUriSeleccionada = it
            Toast.makeText(requireContext(), "Foto seleccionada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFinanzasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarRecyclerView()
        configurarTabs()
        configurarBotones()
        observarViewModel()
    }

    private fun configurarRecyclerView() {
        adapter = TransaccionAdapter(
            onClick = { abrirDetalleTransaccion(it) },
            onLongClick = { mostrarOpcionesTransaccion(it) }
        )
        binding.recyclerTransacciones.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FinanzasFragment.adapter
            isNestedScrollingEnabled = true
        }
    }

    private fun configurarTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { viewModel.setFiltroTipo(null); viewModel.setFiltroEstado(null) }
                    1 -> { viewModel.setFiltroTipo(TipoTransaccion.INGRESO.name); viewModel.setFiltroEstado(null) }
                    2 -> { viewModel.setFiltroTipo(TipoTransaccion.GASTO.name); viewModel.setFiltroEstado(null) }
                    3 -> { viewModel.setFiltroTipo(null); viewModel.setFiltroEstado(EstadoPago.PENDIENTE.name) }
                }
                viewModel.aplicarFiltros(viewModel.todasLasTransacciones.value ?: emptyList())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun configurarBotones() {
        binding.fabAgregarTransaccion.setOnClickListener {
            transaccionEditando = null
            abrirFormularioTransaccion(null)
        }
        binding.btnVerReportes.setOnClickListener { abrirDialogReporte() }
        binding.btnPuntoEquilibrio.setOnClickListener { abrirDialogPuntoEquilibrio() }
        binding.btnDeudas.setOnClickListener { abrirDialogDeudas() }
    }

    private fun abrirFormularioTransaccion(transaccion: Transaccion?) {
        transaccionEditando = transaccion
        fotoUriSeleccionada = null

        val sheet = BottomSheetDialog(requireContext())
        val b = DialogTransaccionBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        var tipoActual = transaccion?.tipo ?: TipoTransaccion.GASTO.name
        actualizarCategoriasPorTipo(b, tipoActual)

        b.btnTipoIngreso.setOnClickListener {
            tipoActual = TipoTransaccion.INGRESO.name
            actualizarFondosTipo(b, tipoActual)
            actualizarCategoriasPorTipo(b, tipoActual)
        }
        b.btnTipoGasto.setOnClickListener {
            tipoActual = TipoTransaccion.GASTO.name
            actualizarFondosTipo(b, tipoActual)
            actualizarCategoriasPorTipo(b, tipoActual)
        }

        transaccion?.let { t ->
            b.etDescripcionTx.setText(t.descripcion)
            b.etMontoTx.setText(if (t.monto % 1.0 == 0.0) t.monto.toInt().toString() else String.format("%.2f", t.monto))
            b.etContactoTx.setText(t.contacto)
            b.etNotasTx.setText(t.notas)
            if (t.fechaVencimientoMs > 0L) b.etFechaVencTx.setText(sdf.format(Date(t.fechaVencimientoMs)))
            tipoActual = t.tipo
            actualizarCategoriasPorTipo(b, tipoActual)

            val estadoIdx = EstadoPago.values().indexOfFirst { it.name == t.estado }
            if (estadoIdx >= 0) b.spinnerEstado.setSelection(estadoIdx)

            if (t.isParcial()) {
                b.tilMontoPagado.visibility = View.VISIBLE
                b.etMontoPagado.setText(if (t.montoPagado % 1.0 == 0.0) t.montoPagado.toInt().toString() else String.format("%.2f", t.montoPagado))
            }
        }

        val estados = EstadoPago.values().map { it.displayName }
        val estadoAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, estados)
        estadoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerEstado.adapter = estadoAdapter
        b.spinnerEstado.setSelection(0)

        b.spinnerEstado.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                b.tilMontoPagado.visibility = if (EstadoPago.values()[pos] == EstadoPago.PARCIAL) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        b.etFechaVencTx.setOnClickListener { mostrarDatePicker { b.etFechaVencTx.setText(it) } }
        b.tilFechaVenc.setEndIconOnClickListener { mostrarDatePicker { b.etFechaVencTx.setText(it) } }

        b.btnAdjuntarFoto.setOnClickListener { fotoLauncher.launch("image/*") }

        b.btnCancelarTx.setOnClickListener { sheet.dismiss() }
        b.btnCerrarTx.setOnClickListener { sheet.dismiss() }

        b.btnGuardarTx.setOnClickListener {
            val desc = b.etDescripcionTx.text.toString().trim()
            val montoStr = b.etMontoTx.text.toString().trim()

            if (montoStr.isEmpty()) {
                Toast.makeText(requireContext(), "El monto es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val monto = montoStr.toDoubleOrNull() ?: 0.0
            val estado = EstadoPago.values()[b.spinnerEstado.selectedItemPosition]
            val montoPagado = if (estado == EstadoPago.PARCIAL)
                b.etMontoPagado.text.toString().toDoubleOrNull() ?: 0.0
            else if (estado == EstadoPago.PAGADO) monto else 0.0

            var fechaVencMs = 0L
            val fechaVencStr = b.etFechaVencTx.text.toString().trim()
            if (fechaVencStr.isNotEmpty()) {
                try { fechaVencMs = sdf.parse(fechaVencStr)?.time ?: 0L } catch (e: Exception) {}
            }

            val categoriaPos = b.spinnerCategoria.selectedItemPosition
            val categoria = if (tipoActual == TipoTransaccion.INGRESO.name)
                CategoriaIngreso.values().getOrNull(categoriaPos)?.name ?: CategoriaIngreso.VENTA_CARNE.name
            else
                CategoriaGasto.values().getOrNull(categoriaPos)?.name ?: CategoriaGasto.ALIMENTO.name

            b.btnGuardarTx.isEnabled = false
            b.btnGuardarTx.text = "Guardando..."

            lifecycleScope.launch(Dispatchers.IO) {
                var fotoUrl = transaccionEditando?.fotoUrl ?: ""

                if (fotoUriSeleccionada != null) {
                    val result = viewModel.subirFotoRecibo(fotoUriSeleccionada!!)
                    if (result.isSuccess) {
                        fotoUrl = result.getOrDefault(fotoUrl)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Error al subir la imagen", Toast.LENGTH_SHORT).show()
                            b.btnGuardarTx.isEnabled = true
                            b.btnGuardarTx.text = "Guardar"
                        }
                        return@launch
                    }
                }

                val nueva = Transaccion(
                    id = transaccionEditando?.id ?: "",
                    tipo = tipoActual,
                    categoria = categoria,
                    descripcion = desc,
                    monto = monto,
                    estado = estado.name,
                    fechaMs = transaccionEditando?.fechaMs ?: System.currentTimeMillis(),
                    fechaVencimientoMs = fechaVencMs,
                    montoPagado = montoPagado,
                    contacto = b.etContactoTx.text.toString().trim(),
                    notas = b.etNotasTx.text.toString().trim(),
                    fotoUrl = fotoUrl
                )

                if (transaccionEditando == null) viewModel.agregarTransaccion(nueva)
                else viewModel.actualizarTransaccion(nueva)

                withContext(Dispatchers.Main) {
                    sheet.dismiss()
                }
            }
        }
        sheet.show()
    }

    private fun actualizarCategoriasPorTipo(b: DialogTransaccionBinding, tipo: String) {
        val categorias = if (tipo == TipoTransaccion.INGRESO.name)
            CategoriaIngreso.values().map { it.displayName }
        else
            CategoriaGasto.values().map { it.displayName }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categorias)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerCategoria.adapter = adapter
    }

    private fun actualizarFondosTipo(b: DialogTransaccionBinding, tipoActivo: String) {
        val activo = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(ContextCompat.getColor(requireContext(), R.color.verde_primario))
            cornerRadius = 8f * resources.displayMetrics.density
        }

        val inactivo = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE)
            setStroke(3, ContextCompat.getColor(requireContext(), R.color.verde_primario))
            cornerRadius = 8f * resources.displayMetrics.density
        }

        b.btnTipoIngreso.background = null
        b.btnTipoGasto.background = null

        if (tipoActivo == TipoTransaccion.INGRESO.name) {
            b.btnTipoIngreso.background = activo
            b.btnTipoIngreso.setTextColor(ContextCompat.getColor(requireContext(), R.color.blanco))
            b.btnTipoGasto.background = inactivo
            b.btnTipoGasto.setTextColor(ContextCompat.getColor(requireContext(), R.color.verde_oscuro))
        } else {
            b.btnTipoGasto.background = activo
            b.btnTipoGasto.setTextColor(ContextCompat.getColor(requireContext(), R.color.blanco))
            b.btnTipoIngreso.background = inactivo
            b.btnTipoIngreso.setTextColor(ContextCompat.getColor(requireContext(), R.color.verde_oscuro))
        }

        b.btnTipoIngreso.invalidate()
        b.btnTipoGasto.invalidate()
    }

    private fun abrirDetalleTransaccion(t: Transaccion) = abrirFormularioTransaccion(t)

    private fun mostrarOpcionesTransaccion(t: Transaccion) {
        val opciones = mutableListOf("Editar", "Eliminar")
        if (t.isPendiente() || t.isParcial()) opciones.add(0, "Marcar como Pagado")
        AlertDialog.Builder(requireContext())
            .setTitle(t.descripcion.ifEmpty { t.categoriaDisplay() })
            .setItems(opciones.toTypedArray()) { _, cual ->
                when (opciones[cual]) {
                    "Marcar como Pagado" -> viewModel.marcarComoPagado(t)
                    "Editar" -> abrirFormularioTransaccion(t)
                    "Eliminar" -> confirmarEliminar(t)
                }
            }.show()
    }

    private fun confirmarEliminar(t: Transaccion) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar transacción")
            .setMessage("¿Eliminar esta transacción? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.eliminarTransaccion(t.id) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun abrirDialogPuntoEquilibrio() {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogPuntoEquilibrioBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        val gastos = viewModel.resumen.value?.gastoTotal ?: 0.0
        b.etGastosFijos.setText(String.format("%.2f", gastos))

        b.btnCalcularPE.setOnClickListener {
            val gf = b.etGastosFijos.text.toString().toDoubleOrNull() ?: 0.0
            val pv = b.etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
            val cv = b.etCostoVariable.text.toString().toDoubleOrNull() ?: 0.0
            val tipo = if (b.radioAves.isChecked) "aves" else "docenas de huevo"

            if (pv <= cv) {
                Toast.makeText(requireContext(), "El precio de venta debe ser mayor al costo variable", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.calcularPuntoEquilibrio(gf, pv, cv, tipo)
        }

        viewModel.puntoEquilibrio.observe(viewLifecycleOwner) { pe ->
            if (pe.unidadesNecesarias > 0) {
                b.cardResultadoPE.visibility = View.VISIBLE
                b.tvUnidadesNecesarias.text = String.format("%.0f ${pe.tipoUnidad}", pe.unidadesNecesarias)
                b.tvIngresoNecesario.text = "Q${String.format("%.2f", pe.ingresoNecesario)}"
                b.tvMargenUnitario.text = "Q${String.format("%.2f", pe.precioVentaUnitario - pe.costoVariableUnitario)} por ${pe.tipoUnidad.dropLast(1)}"
                b.tvInterpretacion.text = buildString {
                    append("Necesitas vender al menos ")
                    append(String.format("%.0f", pe.unidadesNecesarias))
                    append(" ${pe.tipoUnidad} para cubrir todos tus gastos del mes.")
                    if (pe.unidadesNecesarias > 500) append(" Considera reducir costos variables.")
                }
            }
        }

        b.btnCerrarPE.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun abrirDialogReporte() {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogReporteFinancieroBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        val lista = viewModel.todasLasTransacciones.value ?: emptyList()
        val resumen = viewModel.resumen.value

        b.tvRptIngresos.text = "Q${String.format("%.2f", resumen?.ingresoTotal ?: 0.0)}"
        b.tvRptGastos.text = "Q${String.format("%.2f", resumen?.gastoTotal ?: 0.0)}"
        b.tvRptBeneficio.text = "Q${String.format("%.2f", resumen?.beneficioNeto ?: 0.0)}"
        b.tvRptRoi.text = "${String.format("%.1f", resumen?.roi ?: 0.0)}%"

        val gastosCat = viewModel.gastosPorCategoria(lista)
        val sbGastos = StringBuilder()
        gastosCat.entries.sortedByDescending { it.value }.forEach { (cat, total) ->
            val nombre = CategoriaGasto.values().firstOrNull { it.name == cat }?.displayName ?: cat
            sbGastos.appendLine("• $nombre: Q${String.format("%.2f", total)}")
        }
        b.tvRptDesgloseCat.text = sbGastos.toString().ifEmpty { "Sin gastos registrados" }

        b.btnFiltroSemana.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val inicio = cal.timeInMillis
            val filtrada = lista.filter { it.fechaMs >= inicio }
            viewModel.aplicarFiltros(filtrada)
            Toast.makeText(requireContext(), "Mostrando última semana", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        b.btnFiltroMes.setOnClickListener {
            val cal = Calendar.getInstance()
            viewModel.setFiltroMes(cal.get(Calendar.MONTH), cal.get(Calendar.YEAR))
            Toast.makeText(requireContext(), "Mostrando mes actual", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        b.btnFiltroAnio.setOnClickListener {
            val anio = Calendar.getInstance().get(Calendar.YEAR)
            val filtrada = lista.filter { t ->
                val c = Calendar.getInstance().apply { timeInMillis = t.fechaMs }
                c.get(Calendar.YEAR) == anio
            }
            viewModel.aplicarFiltros(filtrada)
            Toast.makeText(requireContext(), "Mostrando año $anio", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        b.btnExportarPdf.setOnClickListener {
            exportarTransaccionesAPdf(lista)
            sheet.dismiss()
        }

        b.btnExportarExcel.setOnClickListener {
            exportarTransaccionesACSV(lista)
            sheet.dismiss()
        }

        b.btnCerrarReporte.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun exportarTransaccionesAPdf(lista: List<Transaccion>) {
        if (lista.isEmpty()) {
            Toast.makeText(requireContext(), "No hay transacciones para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val document = PdfDocument()
            val paint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }
            val headerPaint = Paint().apply {
                color = ContextCompat.getColor(requireContext(), R.color.verde_primario)
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }

            var y = 40
            val sdfFull = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val pageWidth = 595
            val pageHeight = 842

            var page = document.startPage(PageInfo.Builder(pageWidth, pageHeight, 1).create())
            var canvas = page.canvas

            canvas.drawText("Reporte Financiero - OVUM", 20f, y.toFloat(), headerPaint)
            y += 30
            canvas.drawText("Tipo | Categoría | Monto | Descripción | Fecha | Estado", 20f, y.toFloat(), paint)
            y += 20

            for (t in lista) {
                val linea = "${t.tipo} | ${t.categoriaDisplay()} | Q${String.format("%.2f", t.monto)} | ${t.descripcion.take(30)} | ${sdfFull.format(Date(t.fechaMs))} | ${t.estado}"
                canvas.drawText(linea, 20f, y.toFloat(), paint)
                y += 20
                if (y > pageHeight - 40) {
                    document.finishPage(page)
                    page = document.startPage(PageInfo.Builder(pageWidth, pageHeight, 2).create())
                    canvas = page.canvas
                    y = 40
                }
            }

            document.finishPage(page)

            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
            val file = File(dir, "reporte_finanzas_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out -> document.writeTo(out) }
            document.close()

            Snackbar.make(binding.root, "PDF exportado correctamente", Snackbar.LENGTH_LONG)
                .setAction("Abrir") { compartirArchivo(file) }
                .show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportarTransaccionesACSV(lista: List<Transaccion>) {
        if (lista.isEmpty()) {
            Toast.makeText(requireContext(), "No hay transacciones para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val sdfFull = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("Tipo,Categoría,Monto,Descripción,Fecha,Estado")
            for (t in lista) {
                sb.appendLine("${t.tipo},${t.categoriaDisplay()},${String.format("%.2f", t.monto)},${t.descripcion.replace(",", " ")},${sdfFull.format(Date(t.fechaMs))},${t.estado}")
            }

            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return
            val file = File(dir, "reporte_finanzas_${System.currentTimeMillis()}.csv")
            file.writeText(sb.toString(), Charsets.UTF_8)

            Snackbar.make(binding.root, "CSV exportado correctamente", Snackbar.LENGTH_LONG)
                .setAction("Abrir") { compartirArchivo(file) }
                .show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al generar CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun compartirArchivo(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (file.extension.lowercase() == "csv") "text/csv" else "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(Intent.createChooser(intent, "Abrir reporte"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No se encontró una app para abrir el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun abrirDialogDeudas() {
        val sheet = BottomSheetDialog(requireContext())
        val b = DialogDeudaBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        val observer = androidx.lifecycle.Observer { pendientes: List<Transaccion> ->
            val porCobrar = pendientes.filter { it.isIngreso() }
            val porPagar = pendientes.filter { it.isGasto() }

            val sbCobrar = StringBuilder()
            var totalCobrar = 0.0
            for (t in porCobrar) {
                totalCobrar += t.montoPendiente()
                val venc = if (t.fechaVencimientoMs > 0) " (Vence: ${sdf.format(Date(t.fechaVencimientoMs))})" else ""
                sbCobrar.appendLine("• ${t.contacto.ifEmpty { "Cliente" }}: Q${String.format("%.2f", t.montoPendiente())} — ${t.estado}$venc")
            }
            b.tvListaCobrar.text = sbCobrar.toString().ifEmpty { "Sin cuentas por cobrar" }
            b.tvTotalCobrar.text = "Total: Q${String.format("%.2f", totalCobrar)}"

            val sbPagar = StringBuilder()
            var totalPagar = 0.0
            for (t in porPagar) {
                totalPagar += t.montoPendiente()
                val venc = if (t.fechaVencimientoMs > 0) " (Vence: ${sdf.format(Date(t.fechaVencimientoMs))})" else ""
                sbPagar.appendLine("• ${t.contacto.ifEmpty { "Proveedor" }}: Q${String.format("%.2f", t.montoPendiente())} — ${t.categoriaDisplay()}$venc")
            }
            b.tvListaPagar.text = sbPagar.toString().ifEmpty { "Sin cuentas por pagar" }
            b.tvTotalPagar.text = "Total: Q${String.format("%.2f", totalPagar)}"
        }

        viewModel.cuentasPendientes.observe(viewLifecycleOwner, observer)

        sheet.setOnDismissListener {
            viewModel.cuentasPendientes.removeObserver(observer)
        }

        b.btnCerrarDeudas.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun observarViewModel() {
        viewModel.todasLasTransacciones.observe(viewLifecycleOwner) { lista ->
            viewModel.aplicarFiltros(lista)
            viewModel.calcularResumen(lista)
        }

        viewModel.transaccionesFiltradas.observe(viewLifecycleOwner) { lista ->
            adapter.submitList(lista)
            binding.tvSinTransacciones.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerTransacciones.visibility = if (lista.isEmpty()) View.GONE else View.VISIBLE
            binding.tvContadorTx.text = "${lista.size} transacciones"
        }

        viewModel.resumen.observe(viewLifecycleOwner) { r ->
            binding.tvIngresoTotal.text = "Q${String.format(Locale.getDefault(), "%.2f", r.ingresoTotal)}"
            binding.tvGastoTotal.text = "Q${String.format(Locale.getDefault(), "%.2f", r.gastoTotal)}"
            binding.tvBeneficioNeto.text = "Q${String.format(Locale.getDefault(), "%.2f", r.beneficioNeto)}"
            binding.tvRoi.text = "${String.format(Locale.getDefault(), "%.1f", r.roi)}%"
            binding.tvCostoPorAve.text = "Q${String.format(Locale.getDefault(), "%.2f", r.costoPorAve)}"

            val colorBeneficio = if (r.beneficioNeto >= 0) 
                ContextCompat.getColor(requireContext(), R.color.verde_primario) 
            else 
                ContextCompat.getColor(requireContext(), R.color.rojo_salir)
            binding.tvBeneficioNeto.setTextColor(colorBeneficio)

            if (r.alertaGastosAltos) {
                binding.cardAlertaGastos.visibility = View.VISIBLE
                binding.tvAlertaGastos.text = "⚠ Los gastos del mes superan el límite de Q${String.format(Locale.getDefault(), "%.2f", r.limiteGastosMensual)}"
            } else {
                binding.cardAlertaGastos.visibility = View.GONE
            }

            binding.tvPorCobrarResumen.text = "Q${String.format(Locale.getDefault(), "%.2f", r.totalPorCobrar)}"
            binding.tvPorPagarResumen.text = "Q${String.format(Locale.getDefault(), "%.2f", r.totalPorPagar)}"
        }

        viewModel.mensaje.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDatePicker(onFecha: (String) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, y, m, d ->
            onFecha(String.format("%02d/%02d/%04d", d, m + 1, y))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}