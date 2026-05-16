package com.universidad.avicola.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.universidad.avicola.data.model.*
import org.json.JSONArray

// ─────────────────────────────────────────────────────────────────────────────
//  SaludEntities.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/data/local/entities/
// ─────────────────────────────────────────────────────────────────────────────

/**
 * CORRECCIÓN: Room requiere que los @TypeConverter sean funciones de instancia
 * directas en la clase (no dentro de un companion object).
 * Las funciones estáticas del companion object son invisibles para kapt.
 */
class SaludConverters {

    @TypeConverter
    fun listToJson(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun listFromJson(json: String): List<String> {
        if (json.isEmpty() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RegistroMedicoEntity
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "registros_medicos")
@TypeConverters(SaludConverters::class)
data class RegistroMedicoEntity(
    @PrimaryKey val id: String,
    val loteId: String,
    val loteNombre: String,
    val galponId: String,
    val tipo: String,
    val fechaMs: Long,
    val descripcion: String,
    val gravedad: String,
    val sintomasJson: String,
    val enfermedadSospechosa: String,
    val tratamientoAplicado: String,
    val medicamentoId: String,
    val medicamentoNombre: String,
    val dosis: String,
    val duracionDias: Int,
    val avesAfectadas: Int,
    val costo: Double,
    val responsable: String,
    val observaciones: String,
    val userId: String,
    val resuelta: Boolean
) {
    fun toDomain() = RegistroMedico(
        id                   = id,
        loteId               = loteId,
        loteNombre           = loteNombre,
        galponId             = galponId,
        tipo                 = tipo,
        fechaMs              = fechaMs,
        descripcion          = descripcion,
        gravedad             = gravedad,
        sintomas             = SaludConverters().listFromJson(sintomasJson),
        enfermedadSospechosa = enfermedadSospechosa,
        tratamientoAplicado  = tratamientoAplicado,
        medicamentoId        = medicamentoId,
        medicamentoNombre    = medicamentoNombre,
        dosis                = dosis,
        duracionDias         = duracionDias,
        avesAfectadas        = avesAfectadas,
        costo                = costo,
        responsable          = responsable,
        observaciones        = observaciones,
        userId               = userId,
        resuelta             = resuelta
    )

    companion object {
        fun fromDomain(r: RegistroMedico) = RegistroMedicoEntity(
            id                   = r.id,
            loteId               = r.loteId,
            loteNombre           = r.loteNombre,
            galponId             = r.galponId,
            tipo                 = r.tipo,
            fechaMs              = r.fechaMs,
            descripcion          = r.descripcion,
            gravedad             = r.gravedad,
            sintomasJson         = SaludConverters().listToJson(r.sintomas),
            enfermedadSospechosa = r.enfermedadSospechosa,
            tratamientoAplicado  = r.tratamientoAplicado,
            medicamentoId        = r.medicamentoId,
            medicamentoNombre    = r.medicamentoNombre,
            dosis                = r.dosis,
            duracionDias         = r.duracionDias,
            avesAfectadas        = r.avesAfectadas,
            costo                = r.costo,
            responsable          = r.responsable,
            observaciones        = r.observaciones,
            userId               = r.userId,
            resuelta             = r.resuelta
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  VacunacionEntity
// ─────────────────────────────────────────────────────────────────────────────

@Entity(tableName = "vacunaciones")
data class VacunacionEntity(
    @PrimaryKey val id: String,
    val loteId: String,
    val loteNombre: String,
    val nombreVacuna: String,
    val enfermedad: String,
    val fechaAplicacionMs: Long,
    val fechaProximaMs: Long,
    val dosis: String,
    val via: String,
    val avesVacunadas: Int,
    val costo: Double,
    val medicamentoId: String,
    val responsable: String,
    val observaciones: String,
    val userId: String,
    val aplicada: Boolean
) {
    fun toDomain() = Vacunacion(
        id               = id,
        loteId           = loteId,
        loteNombre       = loteNombre,
        nombreVacuna     = nombreVacuna,
        enfermedad       = enfermedad,
        fechaAplicacionMs = fechaAplicacionMs,
        fechaProximaMs   = fechaProximaMs,
        dosis            = dosis,
        via              = via,
        avesVacunadas    = avesVacunadas,
        costo            = costo,
        medicamentoId    = medicamentoId,
        responsable      = responsable,
        observaciones    = observaciones,
        userId           = userId,
        aplicada         = aplicada
    )

    companion object {
        fun fromDomain(v: Vacunacion) = VacunacionEntity(
            id                = v.id,
            loteId            = v.loteId,
            loteNombre        = v.loteNombre,
            nombreVacuna      = v.nombreVacuna,
            enfermedad        = v.enfermedad,
            fechaAplicacionMs = v.fechaAplicacionMs,
            fechaProximaMs    = v.fechaProximaMs,
            dosis             = v.dosis,
            via               = v.via,
            avesVacunadas     = v.avesVacunadas,
            costo             = v.costo,
            medicamentoId     = v.medicamentoId,
            responsable       = v.responsable,
            observaciones     = v.observaciones,
            userId            = v.userId,
            aplicada          = v.aplicada
        )
    }
}
