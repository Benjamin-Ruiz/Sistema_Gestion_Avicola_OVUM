package com.universidad.avicola.data.model

import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
//  RegistroMedico.kt
//  Ubicación: app/src/main/java/com/universidad/avicola/data/model/
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Registro médico principal asociado a un lote.
 * Almacenado en Firestore colección "registros_medicos".
 */
data class RegistroMedico(
    val id: String = "",
    val loteId: String = "",
    val loteNombre: String = "",
    val galponId: String = "",
    val tipo: String = TipoRegistroMedico.OBSERVACION.name,
    val fechaMs: Long = System.currentTimeMillis(),
    val descripcion: String = "",
    val gravedad: String = GravedadSanitaria.LEVE.name,
    val sintomas: List<String> = emptyList(),
    val enfermedadSospechosa: String = "",
    val tratamientoAplicado: String = "",
    val medicamentoId: String = "",
    val medicamentoNombre: String = "",
    val dosis: String = "",
    val duracionDias: Int = 0,
    val avesAfectadas: Int = 0,
    val costo: Double = 0.0,
    val responsable: String = "",
    val observaciones: String = "",
    val userId: String = "",
    val resuelta: Boolean = false
) {
    constructor() : this("", "", "", "", TipoRegistroMedico.OBSERVACION.name,
        0L, "", GravedadSanitaria.LEVE.name, emptyList(), "", "", "", "", "",
        0, 0, 0.0, "", "", "", false)

    fun tipoDisplay(): String = TipoRegistroMedico.valueOf(tipo).displayName
    fun gravedadDisplay(): String = GravedadSanitaria.valueOf(gravedad).displayName
    fun esUrgente(): Boolean = gravedad == GravedadSanitaria.CRITICA.name ||
                               gravedad == GravedadSanitaria.ALTA.name
}

enum class TipoRegistroMedico(val displayName: String) {
    OBSERVACION("Observación general"),
    ENFERMEDAD("Enfermedad detectada"),
    TRATAMIENTO("Tratamiento aplicado"),
    VACUNACION("Vacunación"),
    MORTALIDAD("Registro de mortalidad"),
    BROTE("Alerta de brote")
}

enum class GravedadSanitaria(val displayName: String) {
    LEVE("Leve"),
    MODERADA("Moderada"),
    ALTA("Alta"),
    CRITICA("Crítica")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Vacunacion
// ─────────────────────────────────────────────────────────────────────────────

data class Vacunacion(
    val id: String = "",
    val loteId: String = "",
    val loteNombre: String = "",
    val nombreVacuna: String = "",
    val enfermedad: String = "",
    val fechaAplicacionMs: Long = 0L,
    val fechaProximaMs: Long = 0L,
    val dosis: String = "",
    val via: String = ViaAdministracion.AGUA.name,
    val avesVacunadas: Int = 0,
    val costo: Double = 0.0,
    val medicamentoId: String = "",
    val responsable: String = "",
    val observaciones: String = "",
    val userId: String = "",
    val aplicada: Boolean = false
) {
    constructor() : this("", "", "", "", "", 0L, 0L, "",
        ViaAdministracion.AGUA.name, 0, 0.0, "", "", "", "", false)

    fun viaDisplay(): String = ViaAdministracion.valueOf(via).displayName
    fun estaPendiente(): Boolean = !aplicada && fechaProximaMs > 0L
    fun diasParaProxima(): Long {
        if (fechaProximaMs == 0L) return -1L
        return TimeUnit.MILLISECONDS.toDays(fechaProximaMs - System.currentTimeMillis())
    }
    fun isVencida(): Boolean = estaPendiente() && diasParaProxima() < 0
    fun isProxima(): Boolean = estaPendiente() && diasParaProxima() in 0..7
}

enum class ViaAdministracion(val displayName: String) {
    AGUA("En el agua"),
    INYECTABLE("Inyectable"),
    OCULAR("Ocular / Nasal"),
    ASPERSION("Aspersión"),
    ALIMENTO("En el alimento")
}

// ─────────────────────────────────────────────────────────────────────────────
//  EstadoSanitarioLote — resumen de salud por lote
// ─────────────────────────────────────────────────────────────────────────────

data class EstadoSanitarioLote(
    val loteId: String = "",
    val loteNombre: String = "",
    val galponId: String = "",
    val cantidadAves: Int = 0,
    val mortalidadTotal: Int = 0,
    val mortalidadUltimos7Dias: Int = 0,
    val porcentajeMortalidad: Double = 0.0,
    val ultimaRevisionMs: Long = 0L,
    val estadoGeneral: String = EstadoSanidad.NORMAL.name,
    val vacunasPendientes: Int = 0,
    val tratamientosActivos: Int = 0,
    val alertas: List<String> = emptyList(),
    val costoSanitarioTotal: Double = 0.0
) {
    fun estadoDisplay(): String = EstadoSanidad.valueOf(estadoGeneral).displayName
    fun tieneAlertas(): Boolean = alertas.isNotEmpty()
    fun isEnRiesgo(): Boolean = estadoGeneral == EstadoSanidad.EN_RIESGO.name ||
                                estadoGeneral == EstadoSanidad.CRITICO.name
}

enum class EstadoSanidad(val displayName: String) {
    NORMAL("Normal"),
    VIGILANCIA("En vigilancia"),
    EN_RIESGO("En riesgo"),
    CRITICO("Crítico")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Motor de diagnóstico asistido
// ─────────────────────────────────────────────────────────────────────────────

data class SugerenciaDiagnostico(
    val enfermedad: String,
    val probabilidad: String,
    val tratamientoSugerido: String,
    val medicamentosRecomendados: List<String>,
    val urgencia: String
)

object DiagnosticoAsistido {

    private val base = mapOf(
        listOf("Secreción nasal", "Tos", "Estornudos") to SugerenciaDiagnostico(
            enfermedad = "Bronquitis Infecciosa",
            probabilidad = "Alta",
            tratamientoSugerido = "Antibióticos de amplio espectro, vitaminas A y E. Aislar lote afectado.",
            medicamentosRecomendados = listOf("Enrofloxacina", "Tilosina", "Vitamina A+E"),
            urgencia = "Alta"
        ),
        listOf("Secreción nasal", "Tos", "Baja producción", "Letargo") to SugerenciaDiagnostico(
            enfermedad = "Newcastle",
            probabilidad = "Alta",
            tratamientoSugerido = "No tiene tratamiento específico. Cuarentena inmediata. Notificar a autoridades sanitarias.",
            medicamentosRecomendados = listOf("Vitaminas electrolitos", "Antibióticos preventivos"),
            urgencia = "Crítica"
        ),
        listOf("Diarrea", "Letargo", "Baja producción") to SugerenciaDiagnostico(
            enfermedad = "Coccidiosis",
            probabilidad = "Alta",
            tratamientoSugerido = "Anticoccidiales en agua o alimento por 5-7 días.",
            medicamentosRecomendados = listOf("Amprolium", "Toltrazuril", "Diclazuril"),
            urgencia = "Moderada"
        ),
        listOf("Diarrea", "Mortalidad alta") to SugerenciaDiagnostico(
            enfermedad = "Salmonelosis",
            probabilidad = "Moderada",
            tratamientoSugerido = "Antibióticos sistémicos. Análisis de laboratorio recomendado.",
            medicamentosRecomendados = listOf("Amoxicilina", "Sulfametoxazol", "Enrofloxacina"),
            urgencia = "Alta"
        ),
        listOf("Baja producción") to SugerenciaDiagnostico(
            enfermedad = "Estrés por calor / Deficiencia nutricional",
            probabilidad = "Moderada",
            tratamientoSugerido = "Revisar ventilación, temperatura y formulación del alimento. Suplementar con electrolitos.",
            medicamentosRecomendados = listOf("Electrolitos", "Vitamina C", "Complejo B"),
            urgencia = "Leve"
        )
    )

    fun sugerir(sintomasSeleccionados: List<String>): List<SugerenciaDiagnostico> {
        if (sintomasSeleccionados.isEmpty()) return emptyList()
        return base.entries
            .filter { (key, _) -> key.any { it in sintomasSeleccionados } }
            .sortedByDescending { (key, _) -> key.count { it in sintomasSeleccionados } }
            .map { it.value }
            .distinctBy { it.enfermedad }
            .take(3)
    }

    val SINTOMAS_DISPONIBLES = listOf(
        "Secreción nasal",
        "Tos",
        "Estornudos",
        "Diarrea",
        "Letargo",
        "Baja producción",
        "Pérdida de apetito",
        "Mortalidad alta",
        "Parálisis",
        "Plumas erizadas",
        "Ojos hinchados",
        "Cabeza torcida"
    )

    val ENFERMEDADES_COMUNES = listOf(
        "Newcastle", "Bronquitis Infecciosa", "Gumboro",
        "Marek", "Coccidiosis", "Salmonelosis",
        "Micoplasmosis", "Coriza Infecciosa", "Laringotraqueitis",
        "Influenza Aviar", "Viruela Aviar", "Aerosaculitis"
    )
}
