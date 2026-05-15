package com.universidad.avicola.ui.finanzas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.universidad.avicola.R
import com.universidad.avicola.data.model.EstadoPago
import com.universidad.avicola.data.model.Transaccion
import com.universidad.avicola.databinding.ItemTransaccionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TransaccionAdapter.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/finanzas/
 */
class TransaccionAdapter(
    private val onClick: (Transaccion) -> Unit,
    private val onLongClick: (Transaccion) -> Unit
) : ListAdapter<Transaccion, TransaccionAdapter.ViewHolder>(DiffCallback()) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    inner class ViewHolder(private val b: ItemTransaccionBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(t: Transaccion) {
            b.tvDescripcionTx.text = t.descripcion.ifEmpty { t.categoriaDisplay() }
            b.tvCategoriaTx.text = t.categoriaDisplay()
            b.tvFechaTx.text = sdf.format(Date(t.fechaMs))
            b.tvContactoTx.text = t.contacto.ifEmpty { "" }

            // Monto con signo y color
            val signo = if (t.isIngreso()) "+" else "-"
            b.tvMontoTx.text = "${signo}Q${String.format("%.2f", t.monto)}"
            b.tvMontoTx.setTextColor(
                ContextCompat.getColor(
                    b.root.context,
                    if (t.isIngreso()) R.color.verde_primario else R.color.rojo_salir
                )
            )

            // Barra lateral de color
            val barColor = if (t.isIngreso()) {
                ContextCompat.getColor(b.root.context, R.color.verde_primario)
            } else {
                ContextCompat.getColor(b.root.context, R.color.rojo_salir)
            }
            b.viewBarraLateral.setBackgroundColor(barColor)

            // Estado badge
            when (t.estado) {
                EstadoPago.PAGADO.name -> {
                    b.tvEstadoTx.text = "Pagado"
                    b.tvEstadoTx.setBackgroundResource(R.drawable.bg_estado_pagado)
                }
                EstadoPago.PENDIENTE.name -> {
                    b.tvEstadoTx.text = "Pendiente"
                    b.tvEstadoTx.setBackgroundResource(R.drawable.bg_estado_pendiente)
                }
                EstadoPago.PARCIAL.name -> {
                    b.tvEstadoTx.text = "Parcial"
                    b.tvEstadoTx.setBackgroundResource(R.drawable.bg_estado_parcial)
                }
            }

            // Fondo de alerta si pendiente
            if (t.isPendiente() || t.isParcial()) {
                b.cardTx.setCardBackgroundColor(
                    ContextCompat.getColor(b.root.context, R.color.alerta_naranja)
                )
            } else {
                b.cardTx.setCardBackgroundColor(
                    ContextCompat.getColor(b.root.context, R.color.blanco)
                )
            }

            b.root.setOnClickListener { onClick(t) }
            b.root.setOnLongClickListener { onLongClick(t); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransaccionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaccion>() {
        override fun areItemsTheSame(oldItem: Transaccion, newItem: Transaccion) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Transaccion, newItem: Transaccion) = oldItem == newItem
    }
}