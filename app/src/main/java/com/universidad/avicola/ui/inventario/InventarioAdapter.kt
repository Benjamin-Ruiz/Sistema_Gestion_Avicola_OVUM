package com.universidad.avicola.ui.inventario

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.universidad.avicola.R
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.databinding.ItemInventarioBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * InventarioAdapter.kt — Versión Pro
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/inventario/
 *
 * Colores de alerta:
 *  - Rojo suave  → stock crítico (cantidad <= minStock)
 *  - Naranja     → próximo a vencer (≤7 días)
 *  - Gris rosado → vencido
 *  - Blanco      → normal
 */
class InventarioAdapter(
    private val onItemClick: (ProductoInventario) -> Unit,
    private val onLongClick: (ProductoInventario) -> Unit,
    private val onHistorialClick: (ProductoInventario) -> Unit
) : ListAdapter<ProductoInventario, InventarioAdapter.ViewHolder>(DiffCallback()) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    inner class ViewHolder(private val b: ItemInventarioBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(p: ProductoInventario) {
            // Textos principales
            b.tvNombreItem.text = p.nombre
            b.tvCantidadItem.text = p.cantidadConUnidad()
            b.tvPrecioItem.text = "Q${String.format("%.2f", p.precioUnitario)}"

            // Categoría como badge
            b.tvCategoriaItem.text = p.categoria

            // Fecha de vencimiento
            if (p.fechaVencimientoMs > 0L) {
                b.tvFechaItem.text = sdf.format(Date(p.fechaVencimientoMs))
                b.tvFechaItem.visibility = android.view.View.VISIBLE
            } else {
                b.tvFechaItem.visibility = android.view.View.GONE
            }

            // ── Color de alerta ────────────────────────────
            val ctx = b.root.context
            when {
                p.isVencido() -> {
                    b.rowContenido.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.alerta_vencido)
                    )
                    b.tvIndicador.text = "VENCIDO"
                    b.tvIndicador.visibility = android.view.View.VISIBLE
                }
                p.isProximoAVencer() -> {
                    b.rowContenido.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.alerta_naranja)
                    )
                    val dias = ((p.fechaVencimientoMs - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                    b.tvIndicador.text = "Vence en $dias días"
                    b.tvIndicador.visibility = android.view.View.VISIBLE
                }
                p.isStockCritico() -> {
                    b.rowContenido.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.alerta_stock)
                    )
                    b.tvIndicador.text = "STOCK BAJO"
                    b.tvIndicador.visibility = android.view.View.VISIBLE
                }
                else -> {
                    b.rowContenido.setBackgroundColor(Color.TRANSPARENT)
                    b.tvIndicador.visibility = android.view.View.GONE
                }
            }

            // Click → editar producto
            b.rowContenido.setOnClickListener { onItemClick(p) }

            b.rowContenido.setOnLongClickListener {
                onLongClick(p)
                true
            }

            // Click en historial
            b.btnHistorial.setOnClickListener { onHistorialClick(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemInventarioBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<ProductoInventario>() {
        override fun areItemsTheSame(o: ProductoInventario, n: ProductoInventario) = o.id == n.id
        override fun areContentsTheSame(o: ProductoInventario, n: ProductoInventario) = o == n
    }
}
