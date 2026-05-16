package com.universidad.avicola.data.model

/**
 * EstimacionCostos.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/model/
 *
 * Modelo principal del módulo de Estimación de Costos.
 * Se integra con: Lote (aves), ProductoInventario (inventario), Transaccion (finanzas).
 */
data class EstimacionCostos(
    val id: String = "",
    val loteId: String = "",
    val loteNombre: String = "",
    val tipoAve: String = TipoAveEstimacion.ENGORDE.name,
    val cantidadAves: Int = 0,
    val diasCrianza: Int = 0,
    val pesoObjetivoKg: Double = 0.0,
    val fechaCreacion: Long = System.currentTimeMillis(),
    val estado: String = EstadoEstimacion.BORRADOR.name,

    // ── Costos de alimentación ──────────────────────────────
    val costoAlimentacionTotal: Double = 0.0,
    val fases: List<FaseAlimentacion> = emptyList(),

    // ── Costos sanitarios ───────────────────────────────────
    val costoSanitarioTotal: Double = 0.0,
    val itemsSanitarios: List<ItemSanitario> = emptyList(),

    // ── Costos operativos ───────────────────────────────────
    val costoOperativoTotal: Double = 0.0,
    val costosOperativos: List<CostoOperativo> = emptyList(),

    // ── Mortalidad ──────────────────────────────────────────
    val porcentajeMortalidad: Double = 5.0,
    val perdidaMortalidad: Double = 0.0,

    // ── Ingresos proyectados ────────────────────────────────
    val precioVentaUnitario: Double = 0.0,
    val ingresoEstimado: Double = 0.0,

    // ── Resultados calculados ───────────────────────────────
    val costoTotal: Double = 0.0,
    val costoPorAve: Double = 0.0,
    val gananciaNeta: Double = 0.0,
    val roi: Double = 0.0,
    val puntoEquilibrioUnidades: Double = 0.0,

    // ── Comparación real vs estimado ────────────────────────
    val costoRealRegistrado: Double = 0.0,
    val variacionPorcentaje: Double = 0.0,

    // ── Alertas ─────────────────────────────────────────────
    val alertas: List<String> = emptyList(),

    // ── Notas ───────────────────────────────────────────────
    val notas: String = ""
) {
    constructor() : this("", "", "", TipoAveEstimacion.ENGORDE.name, 0, 0, 0.0, 0L,
        EstadoEstimacion.BORRADOR.name, 0.0, emptyList(), 0.0, emptyList(), 0.0,
        emptyList(), 5.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList(), "")

    fun tipoAveDisplay(): String = TipoAveEstimacion.valueOf(tipoAve).displayName
    fun estadoDisplay(): String = EstadoEstimacion.valueOf(estado).displayName
    fun isRentable(): Boolean = gananciaNeta > 0
    fun tieneAlertas(): Boolean = alertas.isNotEmpty()

    /** Variación de costo real vs estimado (solo si ya hay datos reales) */
    fun diferenciaRealEstimado(): Double = costoRealRegistrado - costoTotal
    fun eficienciaEstimacion(): String {
        if (costoRealRegistrado == 0.0 || costoTotal == 0.0) return "Sin datos reales"
        val variacion = Math.abs(variacionPorcentaje)
        return when {
            variacion <= 5.0 -> "Excelente (±${String.format("%.1f", variacion)}%)"
            variacion <= 15.0 -> "Buena (±${String.format("%.1f", variacion)}%)"
            variacion <= 30.0 -> "Regular (±${String.format("%.1f", variacion)}%)"
            else -> "Baja precisión (±${String.format("%.1f", variacion)}%)"
        }
    }
}

enum class TipoAveEstimacion(val displayName: String) {
    ENGORDE("Pollo de Engorde"),
    PONEDORA("Gallina Ponedora"),
    REPRODUCTORA("Reproductora")
}

enum class EstadoEstimacion(val displayName: String) {
    BORRADOR("Borrador"),
    ACTIVA("Activa"),
    COMPLETADA("Completada"),
    ARCHIVADA("Archivada")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Fase de alimentación (inicio / crecimiento / engorde / producción)
// ─────────────────────────────────────────────────────────────────────────────
data class FaseAlimentacion(
    val nombre: String = "",
    val diasDuracion: Int = 0,
    val consumoDiarioGrPorAve: Double = 0.0,
    val precioKg: Double = 0.0,
    val productoInventarioId: String = "",
    val productoNombre: String = "",
    val stockDisponible: Double = 0.0
) {
    fun consumoTotalKg(cantidadAves: Int): Double =
        (consumoDiarioGrPorAve * cantidadAves * diasDuracion) / 1000.0

    fun costoFase(cantidadAves: Int): Double = consumoTotalKg(cantidadAves) * precioKg

    fun stockSuficiente(cantidadAves: Int): Boolean =
        stockDisponible == 0.0 || stockDisponible >= consumoTotalKg(cantidadAves)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Item sanitario (vacuna, vitamina, antibiótico, etc.)
// ─────────────────────────────────────────────────────────────────────────────
data class ItemSanitario(
    val nombre: String = "",
    val tipo: String = TipoSanitario.VACUNA.name,
    val dosisParaLote: Double = 0.0,
    val precioUnitario: Double = 0.0,
    val productoInventarioId: String = "",
    val stockDisponible: Double = 0.0
) {
    fun costoTotal(): Double = dosisParaLote * precioUnitario
    fun tipoDisplay(): String = TipoSanitario.values().firstOrNull { it.name == tipo }?.displayName ?: tipo
    fun stockSuficiente(): Boolean = stockDisponible == 0.0 || stockDisponible >= dosisParaLote
}

enum class TipoSanitario(val displayName: String) {
    VACUNA("Vacuna"),
    VITAMINA("Vitamina"),
    ANTIBIOTICO("Antibiótico"),
    DESPARASITANTE("Desparasitante"),
    TRATAMIENTO("Tratamiento"),
    OTRO("Otro")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Costo operativo (agua, luz, mano de obra, etc.)
// ─────────────────────────────────────────────────────────────────────────────
data class CostoOperativo(
    val nombre: String = "",
    val tipo: String = TipoCostoOperativo.FIJO.name,
    val montoPorCiclo: Double = 0.0
) {
    fun tipoDisplay(): String = if (tipo == TipoCostoOperativo.FIJO.name) "Fijo" else "Variable"
}

enum class TipoCostoOperativo { FIJO, VARIABLE }

// ─────────────────────────────────────────────────────────────────────────────
//  Resultado de cálculo (usado por el motor de cálculo)
// ─────────────────────────────────────────────────────────────────────────────
data class ResultadoCalculo(
    val costoAlimentacion: Double = 0.0,
    val costoSanitario: Double = 0.0,
    val costoOperativo: Double = 0.0,
    val perdidaMortalidad: Double = 0.0,
    val costoTotal: Double = 0.0,
    val costoPorAve: Double = 0.0,
    val costoDiario: Double = 0.0,
    val costoSemanal: Double = 0.0,
    val costoMensual: Double = 0.0,
    val ingresoEstimado: Double = 0.0,
    val gananciaNeta: Double = 0.0,
    val roi: Double = 0.0,
    val puntoEquilibrioUnidades: Double = 0.0,
    val alertas: List<String> = emptyList(),
    val stockInsuficiente: List<String> = emptyList()
)
