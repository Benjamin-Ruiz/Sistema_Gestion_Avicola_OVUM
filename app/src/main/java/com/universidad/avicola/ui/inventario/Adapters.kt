package com.universidad.avicola.ui.inventario

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.universidad.avicola.data.model.InventoryLog
import com.universidad.avicola.data.model.ProductoInventario
import com.universidad.avicola.databinding.ItemAjusteBinding
import com.universidad.avicola.databinding.ItemLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AjustesAdapter.kt
 * Lista de productos en el panel de Ajustes con botones Editar y Eliminar
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/inventario/
 */
class AjustesAdapter(
    private val onEditar: (ProductoInventario) -> Unit,
    private val onEliminar: (ProductoInventario) -> Unit
) : ListAdapter<ProductoInventario, AjustesAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val b: ItemAjusteBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(p: ProductoInventario) {
            b.tvNombreAjuste.text = p.nombre
            b.tvCantidadAjuste.text = p.cantidadConUnidad()
            b.tvCategoriaAjuste.text = p.categoria
            b.btnEditarAjuste.setOnClickListener { onEditar(p) }
            b.btnEliminarAjuste.setOnClickListener { onEliminar(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemAjusteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<ProductoInventario>() {
        override fun areItemsTheSame(o: ProductoInventario, n: ProductoInventario) = o.id == n.id
        override fun areContentsTheSame(o: ProductoInventario, n: ProductoInventario) = o == n
    }
}

/**
 * InventoryLogAdapter.kt
 * Lista cronológica del historial de movimientos de un producto
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/inventario/
 */
class InventoryLogAdapter :
    ListAdapter<InventoryLog, InventoryLogAdapter.ViewHolder>(DiffCallback()) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    inner class ViewHolder(private val b: ItemLogBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(log: InventoryLog) {
            b.tvLogCambio.text = log.changeText()
            b.tvLogRazon.text = log.reason
            b.tvLogFecha.text = sdf.format(Date(log.timestamp))

            // Verde si positivo, rojo si negativo
            val color = if (log.isPositivo()) 0xFF2D6A4F.toInt() else 0xFFE53935.toInt()
            b.tvLogCambio.setTextColor(color)

            // Indicador lateral
            b.viewIndicador.setBackgroundColor(color)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class DiffCallback : DiffUtil.ItemCallback<InventoryLog>() {
        override fun areItemsTheSame(o: InventoryLog, n: InventoryLog) = o.id == n.id
        override fun areContentsTheSame(o: InventoryLog, n: InventoryLog) = o == n
    }
}
