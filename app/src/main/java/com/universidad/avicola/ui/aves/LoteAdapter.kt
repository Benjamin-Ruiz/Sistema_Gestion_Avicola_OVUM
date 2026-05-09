package com.universidad.avicola.ui.aves

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.universidad.avicola.data.model.Lote
import com.universidad.avicola.databinding.ItemLoteBinding
import java.util.concurrent.TimeUnit

class LoteAdapter(
    private val onActionClick: (Lote) -> Unit,
    private val onLongClick: (Lote) -> Unit
) : ListAdapter<Lote, LoteAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(private val b: ItemLoteBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(lote: Lote) {
            b.tvLoteId.text = "Lote: #${lote.id.takeLast(5).uppercase()}"
            b.tvEstado.text = lote.estado
            b.tvInfoPrincipal.text = "Galpón ${lote.galponId} • ${lote.lineaGenetica} • ${lote.proposito}"
            
            b.tvPoblacion.text = "${lote.cantidadActual} aves"
            
            // Calcular edad
            val diff = System.currentTimeMillis() - lote.fechaIngreso
            val dias = TimeUnit.MILLISECONDS.toDays(diff)
            b.tvEdad.text = "$dias días"
            
            // Viabilidad
            val viabilidad = if (lote.cantidadInicial > 0) {
                (lote.cantidadActual.toDouble() / lote.cantidadInicial.toDouble()) * 100
            } else 100.0
            b.tvViabilidad.text = String.format("%.1f%%", viabilidad)

            b.btnRegistrarDiario.setOnClickListener { onActionClick(lote) }

            b.root.setOnLongClickListener {
                onLongClick(lote)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemLoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Lote>() {
        override fun areItemsTheSame(oldItem: Lote, newItem: Lote) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Lote, newItem: Lote) = oldItem == newItem
    }
}
