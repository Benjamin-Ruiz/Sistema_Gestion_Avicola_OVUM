package com.universidad.avicola.ui.costos

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.universidad.avicola.R
import com.universidad.avicola.data.model.*

// ─────────────────────────────────────────────────────────────────────────────
//  Adapter de Fases de Alimentación
// ─────────────────────────────────────────────────────────────────────────────
class FaseAlimentacionAdapter(
    private val fases: MutableList<FaseAlimentacion>,
    private var productos: List<ProductoInventario>,
    private val onFaseChanged: () -> Unit
) : RecyclerView.Adapter<FaseAlimentacionAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val etNombre: TextInputEditText = view.findViewById(R.id.etFaseNombre)
        val etDias: TextInputEditText = view.findViewById(R.id.etFaseDias)
        val etConsumo: TextInputEditText = view.findViewById(R.id.etFaseConsumo)
        val spinnerProducto: Spinner = view.findViewById(R.id.spinnerFaseProducto)
        val tvCosto: TextView = view.findViewById(R.id.tvFaseCosto)
        val tvStockAlerta: TextView = view.findViewById(R.id.tvFaseStockAlerta)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnFaseEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_fase_alimentacion, parent, false)
        return VH(v)
    }

    override fun getItemCount() = fases.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val fase = fases[position]

        // Evitar disparar watchers durante el bind
        holder.etNombre.tag = "binding"
        holder.etDias.tag = "binding"
        holder.etConsumo.tag = "binding"

        holder.etNombre.setText(fase.nombre)
        holder.etDias.setText(if (fase.diasDuracion > 0) fase.diasDuracion.toString() else "")
        holder.etConsumo.setText(if (fase.consumoDiarioGrPorAve > 0) fase.consumoDiarioGrPorAve.toString() else "")

        holder.etNombre.tag = null
        holder.etDias.tag = null
        holder.etConsumo.tag = null

        // Spinner de productos del inventario (ALIMENTOS)
        val alimentos = productos.filter { it.categoria == Categoria.ALIMENTOS.name }
        val opciones = mutableListOf("Sin vincular")
        opciones.addAll(alimentos.map { "${it.nombre} — Q${String.format("%.2f", it.precioUnitario)}/${it.unitType}" })

        val spinnerAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, opciones)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinnerProducto.adapter = spinnerAdapter

        // Pre-seleccionar producto si ya está vinculado
        val idx = alimentos.indexOfFirst { it.id == fase.productoInventarioId }
        holder.spinnerProducto.setSelection(if (idx >= 0) idx + 1 else 0)

        // Calcular y mostrar costo de la fase
        actualizarCostoFase(holder, fase)

        // Alerta de stock
        if (!fase.stockSuficiente(0) && fase.productoInventarioId.isNotEmpty()) {
            holder.tvStockAlerta.visibility = View.VISIBLE
            holder.tvStockAlerta.text = "⚠ Stock insuficiente"
        } else {
            holder.tvStockAlerta.visibility = View.GONE
        }

        // Listeners
        holder.spinnerProducto.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos == 0) {
                    fases[holder.adapterPosition] = fases[holder.adapterPosition].copy(
                        productoInventarioId = "", productoNombre = "", precioKg = 0.0, stockDisponible = 0.0
                    )
                } else {
                    val producto = alimentos[pos - 1]
                    fases[holder.adapterPosition] = fases[holder.adapterPosition].copy(
                        productoInventarioId = producto.id,
                        productoNombre = producto.nombre,
                        precioKg = producto.precioUnitario,
                        stockDisponible = producto.cantidad
                    )
                }
                actualizarCostoFase(holder, fases[holder.adapterPosition])
                onFaseChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (holder.etNombre.tag == "binding") return
                val pos = holder.adapterPosition
                if (pos < 0 || pos >= fases.size) return
                fases[pos] = fases[pos].copy(
                    nombre = holder.etNombre.text.toString(),
                    diasDuracion = holder.etDias.text.toString().toIntOrNull() ?: 0,
                    consumoDiarioGrPorAve = holder.etConsumo.text.toString().toDoubleOrNull() ?: 0.0
                )
                actualizarCostoFase(holder, fases[pos])
                onFaseChanged()
            }
        }
        holder.etNombre.addTextChangedListener(watcher)
        holder.etDias.addTextChangedListener(watcher)
        holder.etConsumo.addTextChangedListener(watcher)

        holder.btnEliminar.setOnClickListener {
            val p = holder.adapterPosition
            if (p >= 0 && p < fases.size) {
                fases.removeAt(p)
                notifyItemRemoved(p)
                onFaseChanged()
            }
        }
    }

    private fun actualizarCostoFase(holder: VH, fase: FaseAlimentacion) {
        val ctx = holder.itemView.context
        // Estimación de costo con 100 aves como preview (el valor real se calcula con cantidadAves real)
        val costoPreview = fase.costoFase(100)
        holder.tvCosto.text = if (fase.precioKg > 0)
            ctx.getString(R.string.costo_fase_preview, costoPreview)
        else "Vincula un producto para calcular costo"
    }

    fun actualizarProductos(nuevosProductos: List<ProductoInventario>) {
        productos = nuevosProductos
        notifyDataSetChanged()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Adapter de Ítems Sanitarios
// ─────────────────────────────────────────────────────────────────────────────
class ItemSanitarioAdapter(
    private val items: MutableList<ItemSanitario>,
    private var productos: List<ProductoInventario>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<ItemSanitarioAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val etNombre: TextInputEditText = view.findViewById(R.id.etSanNombre)
        val spinnerTipo: Spinner = view.findViewById(R.id.spinnerSanTipo)
        val etDosis: TextInputEditText = view.findViewById(R.id.etSanDosis)
        val spinnerProducto: Spinner = view.findViewById(R.id.spinnerSanProducto)
        val tvCosto: TextView = view.findViewById(R.id.tvSanCosto)
        val tvStockAlerta: TextView = view.findViewById(R.id.tvSanStockAlerta)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnSanEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_sanitario, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]

        holder.etNombre.setText(item.nombre)
        holder.etDosis.setText(if (item.dosisParaLote > 0) item.dosisParaLote.toString() else "")

        // Spinner tipo
        val tiposAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item,
            TipoSanitario.values().map { it.displayName })
        tiposAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinnerTipo.adapter = tiposAdapter
        val tipoIdx = TipoSanitario.values().indexOfFirst { it.name == item.tipo }
        if (tipoIdx >= 0) holder.spinnerTipo.setSelection(tipoIdx)

        // Spinner productos (MEDICINAS)
        val medicinas = productos.filter { it.categoria == Categoria.MEDICINAS.name }
        val opciones = mutableListOf("Sin vincular")
        opciones.addAll(medicinas.map { "${it.nombre} — Q${String.format("%.2f", it.precioUnitario)}" })
        val sanAdapter = ArrayAdapter(holder.itemView.context, android.R.layout.simple_spinner_item, opciones)
        sanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.spinnerProducto.adapter = sanAdapter
        val prodIdx = medicinas.indexOfFirst { it.id == item.productoInventarioId }
        holder.spinnerProducto.setSelection(if (prodIdx >= 0) prodIdx + 1 else 0)

        // Costo
        holder.tvCosto.text = "Total: Q${String.format("%.2f", item.costoTotal())}"

        // Stock
        if (!item.stockSuficiente() && item.productoInventarioId.isNotEmpty()) {
            holder.tvStockAlerta.visibility = View.VISIBLE
        } else {
            holder.tvStockAlerta.visibility = View.GONE
        }

        holder.spinnerProducto.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val i = holder.adapterPosition; if (i < 0) return
                if (pos == 0) {
                    items[i] = items[i].copy(productoInventarioId = "", precioUnitario = 0.0, stockDisponible = 0.0)
                } else {
                    val prod = medicinas[pos - 1]
                    items[i] = items[i].copy(
                        productoInventarioId = prod.id,
                        nombre = prod.nombre,
                        precioUnitario = prod.precioUnitario,
                        stockDisponible = prod.cantidad
                    )
                }
                holder.tvCosto.text = "Total: Q${String.format("%.2f", items[holder.adapterPosition].costoTotal())}"
                onChanged()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        holder.etDosis.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val i = holder.adapterPosition; if (i < 0) return
                items[i] = items[i].copy(dosisParaLote = s.toString().toDoubleOrNull() ?: 0.0)
                holder.tvCosto.text = "Total: Q${String.format("%.2f", items[i].costoTotal())}"
                onChanged()
            }
        })

        holder.btnEliminar.setOnClickListener {
            val p = holder.adapterPosition
            if (p >= 0 && p < items.size) { items.removeAt(p); notifyItemRemoved(p); onChanged() }
        }
    }

    fun actualizarProductos(nuevosProductos: List<ProductoInventario>) {
        productos = nuevosProductos
        notifyDataSetChanged()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Adapter de Costos Operativos
// ─────────────────────────────────────────────────────────────────────────────
class CostoOperativoAdapter(
    private val items: MutableList<CostoOperativo>,
    private val onChanged: () -> Unit
) : RecyclerView.Adapter<CostoOperativoAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvOpNombre)
        val tvTipo: TextView = view.findViewById(R.id.tvOpTipo)
        val etMonto: TextInputEditText = view.findViewById(R.id.etOpMonto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_costo_operativo, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvNombre.text = item.nombre
        holder.tvTipo.text = item.tipoDisplay()
        holder.etMonto.setText(if (item.montoPorCiclo > 0) String.format("%.2f", item.montoPorCiclo) else "")

        holder.etMonto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val p = holder.adapterPosition; if (p < 0) return
                items[p] = items[p].copy(montoPorCiclo = s.toString().toDoubleOrNull() ?: 0.0)
                onChanged()
            }
        })
    }
}
