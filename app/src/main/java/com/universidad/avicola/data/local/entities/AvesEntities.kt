package com.universidad.avicola.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.universidad.avicola.data.model.Lote
import com.universidad.avicola.data.model.RegistroDiarioAves

@Entity(tableName = "lotes")
data class LoteEntity(
    @PrimaryKey val id: String,
    val galponId: String,
    val lineaGenetica: String,
    val proposito: String,
    val fechaIngreso: Long,
    val cantidadInicial: Int,
    val cantidadActual: Int,
    val estado: String
) {
    fun toDomain() = Lote(
        id = id,
        galponId = galponId,
        lineaGenetica = lineaGenetica,
        proposito = proposito,
        fechaIngreso = fechaIngreso,
        cantidadInicial = cantidadInicial,
        cantidadActual = cantidadActual,
        estado = estado
    )

    companion object {
        fun fromDomain(l: Lote) = LoteEntity(
            id = l.id,
            galponId = l.galponId,
            lineaGenetica = l.lineaGenetica,
            proposito = l.proposito,
            fechaIngreso = l.fechaIngreso,
            cantidadInicial = l.cantidadInicial,
            cantidadActual = l.cantidadActual,
            estado = l.estado
        )
    }
}

@Entity(
    tableName = "registros_diarios_aves",
    foreignKeys = [
        ForeignKey(
            entity = LoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("loteId")]
)
data class RegistroDiarioEntity(
    @PrimaryKey val id: String,
    val loteId: String,
    val fecha: Long,
    val mortalidad: Int,
    val descarte: Int,
    val pesoPromedio: Double,
    val observaciones: String
) {
    fun toDomain() = RegistroDiarioAves(
        id = id,
        loteId = loteId,
        fecha = fecha,
        mortalidad = mortalidad,
        descarte = descarte,
        pesoPromedio = pesoPromedio,
        observaciones = observaciones
    )

    companion object {
        fun fromDomain(r: RegistroDiarioAves) = RegistroDiarioEntity(
            id = r.id,
            loteId = r.loteId,
            fecha = r.fecha,
            mortalidad = r.mortalidad,
            descarte = r.descarte,
            pesoPromedio = r.pesoPromedio,
            observaciones = r.observaciones
        )
    }
}
