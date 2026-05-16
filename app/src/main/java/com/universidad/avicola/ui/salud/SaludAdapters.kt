package com.universidad.avicola.ui.salud

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.universidad.avicola.R
import com.universidad.avicola.data.model.*
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
//  SaludAdapters.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/ui/salud/
// ─────────────────────────────────────────────────────────────────────────────

// ── Estado Sanitario por Lote ─────────────────────────────────────────────────
class EstadoSanitarioAdapter(
    private val onClick:     (EstadoSanitarioLote) -> Unit,
    private val onLongClick: (EstadoSanitarioLote) -> Unit
) : ListAdapter<EstadoSanitarioLote, EstadoSanitarioAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card:          CardView = view.findViewById(R.id.cardSaludLote)
        val tvNombre:      TextView = view.findViewById(R.id.tvSaludLoteNombre)
        val tvAves:        TextView = view.findViewById(R.id.tvSaludLoteAves)
        val tvMortalidad:  TextView = view.findViewById(R.id.tvSaludLoteMortalidad)
        val tvEstado:      TextView = view.findViewById(R.id.tvSaludLoteEstado)
        val tvCosto:       TextView = view.findViewById(R.id.tvSaludLoteCosto)
        val tvAlertas:     TextView = view.findViewById(R.id.tvSaludLoteAlertas)
        val tvVacunas:     TextView = view.findViewById(R.id.tvSaludLoteVacunas)
        val tvTratamientos:TextView = view.findViewById(R.id.tvSaludLoteTratamientos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_estado_sanitario, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx  = holder.itemView.context

        holder.tvNombre.text     = item.loteNombre.ifEmpty { "Lote sin nombre" }
        holder.tvAves.text       = "${item.cantidadAves} aves"
        holder.tvMortalidad.text = "${item.mortalidadTotal} bajas (${String.format("%.1f", item.porcentajeMortalidad)}%)"
        holder.tvCosto.text      = "Q${String.format("%.2f", item.costoSanitarioTotal)}"
        holder.tvVacunas.text    = "${item.vacunasPendientes} vacuna(s) pendiente(s)"
        holder.tvTratamientos.text = "${item.tratamientosActivos} caso(s) activo(s)"

        holder.tvEstado.text = item.estadoDisplay()
        val (bgEstado, colorTexto) = when (item.estadoGeneral) {
            EstadoSanidad.CRITICO.name    -> Pair(R.drawable.bg_estado_pagado,   R.color.blanco)
            EstadoSanidad.EN_RIESGO.name  -> Pair(R.drawable.bg_estado_parcial,  R.color.blanco)
            EstadoSanidad.VIGILANCIA.name -> Pair(R.drawable.bg_estado_pendiente,R.color.blanco)
            else                          -> Pair(R.drawable.bg_badge_verde,     R.color.blanco)
        }
        holder.tvEstado.setBackgroundResource(bgEstado)
        holder.tvEstado.setTextColor(ctx.getColor(colorTexto))

        if (item.tieneAlertas()) {
            holder.tvAlertas.visibility = View.VISIBLE
            holder.tvAlertas.text = item.alertas.first()
        } else {
            holder.tvAlertas.visibility = View.GONE
        }

        holder.card.setOnClickListener     { onClick(item) }
        holder.card.setOnLongClickListener { onLongClick(item); true }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EstadoSanitarioLote>() {
            override fun areItemsTheSame(a: EstadoSanitarioLote, b: EstadoSanitarioLote) = a.loteId == b.loteId
            override fun areContentsTheSame(a: EstadoSanitarioLote, b: EstadoSanitarioLote) = a == b
        }
    }
}

// ── Registro Médico ───────────────────────────────────────────────────────────
class RegistroMedicoAdapter(
    private val onClick:    (RegistroMedico) -> Unit,
    private val onResolver: (RegistroMedico) -> Unit,
    private val onEliminar: (RegistroMedico) -> Unit
) : ListAdapter<RegistroMedico, RegistroMedicoAdapter.VH>(DIFF) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card:       CardView = view.findViewById(R.id.cardRegistroMedico)
        val tvTipo:     TextView = view.findViewById(R.id.tvRegTipo)
        val tvLote:     TextView = view.findViewById(R.id.tvRegLote)
        val tvGravedad: TextView = view.findViewById(R.id.tvRegGravedad)
        val tvFecha:    TextView = view.findViewById(R.id.tvRegFecha)
        val tvDescripcion:TextView = view.findViewById(R.id.tvRegDescripcion)
        val tvCosto:    TextView = view.findViewById(R.id.tvRegCosto)
        val tvEstado:   TextView = view.findViewById(R.id.tvRegEstado)
        val btnResolver:View     = view.findViewById(R.id.btnRegResolver)
        val btnEliminar:View     = view.findViewById(R.id.btnRegEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_registro_medico, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx  = holder.itemView.context

        holder.tvTipo.text       = item.tipoDisplay()
        holder.tvLote.text       = item.loteNombre
        holder.tvGravedad.text   = item.gravedadDisplay()
        holder.tvFecha.text      = sdf.format(Date(item.fechaMs))
        holder.tvDescripcion.text = item.descripcion.ifEmpty { item.enfermedadSospechosa }
        holder.tvCosto.text      = if (item.costo > 0) "Q${String.format("%.2f", item.costo)}" else ""
        holder.tvEstado.text     = if (item.resuelta) "✔ Resuelto" else "⏳ Pendiente"
        holder.tvEstado.setTextColor(ctx.getColor(
            if (item.resuelta) R.color.verde_primario else R.color.dorado))

        val colorGravedad = when (item.gravedad) {
            GravedadSanitaria.CRITICA.name  -> R.color.rojo_salir
            GravedadSanitaria.ALTA.name     -> R.color.dorado
            GravedadSanitaria.MODERADA.name -> R.color.verde_claro
            else                            -> R.color.gris_hint
        }
        holder.tvGravedad.setTextColor(ctx.getColor(colorGravedad))

        holder.btnResolver.visibility = if (item.resuelta) View.GONE else View.VISIBLE
        holder.btnResolver.setOnClickListener { onResolver(item) }
        holder.btnEliminar.setOnClickListener { onEliminar(item) }
        holder.card.setOnClickListener { onClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RegistroMedico>() {
            override fun areItemsTheSame(a: RegistroMedico, b: RegistroMedico) = a.id == b.id
            override fun areContentsTheSame(a: RegistroMedico, b: RegistroMedico) = a == b
        }
    }
}

// ── Vacunación ────────────────────────────────────────────────────────────────
class VacunacionAdapter(
    private val onClick:    (Vacunacion) -> Unit,
    private val onAplicar:  (Vacunacion) -> Unit,
    private val onEliminar: (Vacunacion) -> Unit
) : ListAdapter<Vacunacion, VacunacionAdapter.VH>(DIFF) {

    private val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card:        CardView = view.findViewById(R.id.cardVacunacion)
        val tvNombre:    TextView = view.findViewById(R.id.tvVacNombre)
        val tvLote:      TextView = view.findViewById(R.id.tvVacLote)
        val tvVia:       TextView = view.findViewById(R.id.tvVacVia)
        val tvProxima:   TextView = view.findViewById(R.id.tvVacProxima)
        val tvCosto:     TextView = view.findViewById(R.id.tvVacCosto)
        val tvEstado:    TextView = view.findViewById(R.id.tvVacEstado)
        val btnAplicar:  View     = view.findViewById(R.id.btnVacAplicar)
        val btnEliminar: View     = view.findViewById(R.id.btnVacEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_vacunacion, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx  = holder.itemView.context

        holder.tvNombre.text  = item.nombreVacuna
        holder.tvLote.text    = item.loteNombre
        holder.tvVia.text     = item.viaDisplay()
        holder.tvCosto.text   = if (item.costo > 0) "Q${String.format("%.2f", item.costo)}" else "Sin costo"

        if (item.aplicada) {
            holder.tvEstado.text = "✔ Aplicada"
            holder.tvEstado.setTextColor(ctx.getColor(R.color.verde_primario))
            holder.tvProxima.text = if (item.fechaProximaMs > 0)
                "Próxima: ${sdf.format(Date(item.fechaProximaMs))}" else ""
            holder.btnAplicar.visibility = View.GONE
        } else {
            val dias = item.diasParaProxima()
            holder.tvEstado.text = when {
                item.isVencida()  -> "⛔ Vencida"
                item.isProxima()  -> "⚠ En $dias días"
                dias >= 0         -> "📅 En $dias días"
                else              -> "⏳ Pendiente"
            }
            holder.tvEstado.setTextColor(ctx.getColor(when {
                item.isVencida() -> R.color.rojo_salir
                item.isProxima() -> R.color.dorado
                else             -> R.color.gris_hint
            }))
            holder.tvProxima.text = if (item.fechaProximaMs > 0)
                sdf.format(Date(item.fechaProximaMs)) else "Sin fecha"
            holder.btnAplicar.visibility = View.VISIBLE
        }

        holder.btnAplicar.setOnClickListener  { onAplicar(item) }
        holder.btnEliminar.setOnClickListener { onEliminar(item) }
        holder.card.setOnClickListener { onClick(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Vacunacion>() {
            override fun areItemsTheSame(a: Vacunacion, b: Vacunacion) = a.id == b.id
            override fun areContentsTheSame(a: Vacunacion, b: Vacunacion) = a == b
        }
    }
}
