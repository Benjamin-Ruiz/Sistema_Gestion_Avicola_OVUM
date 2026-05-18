package com.universidad.avicola.ui.costos

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.universidad.avicola.R
import com.universidad.avicola.data.model.EstimacionCostos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EstimacionAdapter.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/costos/
 */
class EstimacionAdapter(
    private val onClick: (EstimacionCostos) -> Unit,
    private val onLongClick: (EstimacionCostos) -> Unit,
    private val onActionClick: (EstimacionCostos) -> Unit
) : ListAdapter<EstimacionCostos, EstimacionAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardEstimacion)
        val tvLote: TextView = view.findViewById(R.id.tvEstLote)
        val tvTipoAve: TextView = view.findViewById(R.id.tvEstTipoAve)
        val tvAves: TextView = view.findViewById(R.id.tvEstAves)
        val tvFecha: TextView = view.findViewById(R.id.tvEstFecha)
        val tvCostoTotal: TextView = view.findViewById(R.id.tvEstCostoTotal)
        val tvGanancia: TextView = view.findViewById(R.id.tvEstGanancia)
        val tvRoi: TextView = view.findViewById(R.id.tvEstRoi)
        val tvEstado: TextView = view.findViewById(R.id.tvEstEstado)
        val tvAlerta: TextView = view.findViewById(R.id.tvEstAlerta)
        val btnAccion: MaterialButton = view.findViewById(R.id.btnEstAccion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_estimacion, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        holder.tvLote.text = item.loteNombre.ifEmpty { "Sin lote asignado" }
        holder.tvTipoAve.text = item.tipoAveDisplay()
        holder.tvAves.text = "${item.cantidadAves} aves · ${item.diasCrianza} días"
        holder.tvFecha.text = sdf.format(Date(item.fechaCreacion))
        holder.tvCostoTotal.text = "Q${String.format("%.2f", item.costoTotal)}"

        // Ganancia con color
        val ganancia = item.gananciaNeta
        holder.tvGanancia.text = "Q${String.format("%.2f", ganancia)}"
        holder.tvGanancia.setTextColor(
            ctx.getColor(if (ganancia >= 0) R.color.verde_primario else R.color.rojo_salir)
        )

        // ROI
        holder.tvRoi.text = "ROI: ${String.format("%.1f", item.roi)}%"
        holder.tvRoi.setTextColor(
            ctx.getColor(if (item.roi >= 0) R.color.verde_primario else R.color.rojo_salir)
        )

        // Estado badge
        holder.tvEstado.text = item.estadoDisplay()
        val bgEstado = when (item.estado) {
            "ACTIVA" -> R.drawable.bg_badge_verde
            "BORRADOR" -> R.drawable.bg_estado_pendiente
            "COMPLETADA" -> R.drawable.bg_estado_pagado
            else -> R.drawable.bg_estado_parcial
        }
        holder.tvEstado.setBackgroundResource(bgEstado)

        // Lógica del botón de acción (Activar / Completar)
        val ctx_color = ContextCompat.getColor(ctx, R.color.verde_primario)
        val ctx_color_dorado = ContextCompat.getColor(ctx, R.color.dorado)

        when (item.estado) {
            "BORRADOR" -> {
                holder.btnAccion.visibility = View.VISIBLE
                holder.btnAccion.text = "Activar Producción"
                holder.btnAccion.backgroundTintList = ColorStateList.valueOf(ctx_color)
            }
            "ACTIVA" -> {
                holder.btnAccion.visibility = View.VISIBLE
                holder.btnAccion.text = "✅ Completar Lote"
                holder.btnAccion.backgroundTintList = ColorStateList.valueOf(ctx_color_dorado)
            }
            else -> {
                holder.btnAccion.visibility = View.GONE
            }
        }

        // Alertas
        if (item.tieneAlertas()) {
            holder.tvAlerta.visibility = View.VISIBLE
            holder.tvAlerta.text = item.alertas.first()
        } else {
            holder.tvAlerta.visibility = View.GONE
        }

        holder.btnAccion.setOnClickListener { onActionClick(item) }
        holder.card.setOnClickListener { onClick(item) }
        holder.card.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EstimacionCostos>() {
            override fun areItemsTheSame(a: EstimacionCostos, b: EstimacionCostos) = a.id == b.id
            override fun areContentsTheSame(a: EstimacionCostos, b: EstimacionCostos) = a == b
        }
    }
}
