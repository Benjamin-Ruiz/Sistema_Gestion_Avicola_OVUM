package com.universidad.avicola.data.model

/**
 * Galpon.kt — Módulo de Aves (Galpones)
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/data/model/
 */
data class Galpon(
    val id: String = "",
    val numero: Int = 0,
    val cantidadAves: Int = 0,
    val edadSemanas: Int = 0,
    val tipoAve: String = TipoAve.POLLO_ENGORDE.name,
    val estado: String = EstadoGalpon.ACTIVO.name,
    val fechaIngreso: Long = System.currentTimeMillis(),
    val observaciones: String = ""
) {
    constructor() : this("", 0, 0, 0, TipoAve.POLLO_ENGORDE.name, EstadoGalpon.ACTIVO.name, 0L, "")

    fun tipoAveDisplay(): String = TipoAve.valueOf(tipoAve).displayName
    fun estadoDisplay(): String = EstadoGalpon.valueOf(estado).displayName
    fun isActivo(): Boolean = estado == EstadoGalpon.ACTIVO.name
}

enum class TipoAve(val displayName: String) {
    POLLO_ENGORDE("Pollo de Engorde"),
    GALLINA_PONEDORA("Gallina Ponedora"),
    PAVO("Pavo"),
    PATO("Pato"),
    OTRO("Otro")
}

enum class EstadoGalpon(val displayName: String) {
    ACTIVO("Activo"),
    VACIO("Vacío"),
    EN_LIMPIEZA("En Limpieza"),
    INACTIVO("Inactivo")
}

/**
 * MovimientoAves.kt — Registro de bajas y traslados
 */
data class MovimientoAves(
    val id: String = "",
    val galponOrigenId: String = "",
    val galponDestinoId: String = "",   // vacío si es baja por mortalidad
    val cantidadAves: Int = 0,
    val tipoMovimiento: String = TipoMovimiento.BAJA.name,
    val motivo: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", 0, TipoMovimiento.BAJA.name, "", 0L)
}

enum class TipoMovimiento(val displayName: String) {
    BAJA("Baja por Mortalidad"),
    TRASLADO("Traslado entre Galpones"),
    VENTA("Venta"),
    INGRESO("Ingreso de Aves")
}
