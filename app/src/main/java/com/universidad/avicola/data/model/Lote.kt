package com.universidad.avicola.data.model

/**
 * Lote.kt — Módulo de Gestión de Aves
 */
data class Lote(
    val id: String = "",
    val galponId: String = "",
    val lineaGenetica: String = "",
    val proposito: String = PropositoLote.ENGORDE.name,
    val fechaIngreso: Long = System.currentTimeMillis(),
    val cantidadInicial: Int = 0,
    val cantidadActual: Int = 0,
    val estado: String = EstadoLote.ACTIVO.name
) {
    constructor() : this("", "", "", PropositoLote.ENGORDE.name, 0L, 0, 0, EstadoLote.ACTIVO.name)

    fun propositoDisplay(): String = PropositoLote.valueOf(proposito).displayName
    fun isActivo(): Boolean = estado == EstadoLote.ACTIVO.name
}

enum class PropositoLote(val displayName: String) {
    ENGORDE("Engorde"),
    POSTURA("Postura Comercial"),
    CRIA("Cría")
}

enum class EstadoLote(val displayName: String) {
    ACTIVO("Activo"),
    CERRADO("Cerrado")
}

/**
 * RegistroDiarioAves.kt — Control de Mortalidad y Pesaje
 */
data class RegistroDiarioAves(
    val id: String = "",
    val loteId: String = "",
    val fecha: Long = System.currentTimeMillis(),
    val mortalidad: Int = 0,
    val descarte: Int = 0,
    val pesoPromedio: Double = 0.0,
    val observaciones: String = ""
) {
    constructor() : this("", "", 0L, 0, 0, 0.0, "")
}
