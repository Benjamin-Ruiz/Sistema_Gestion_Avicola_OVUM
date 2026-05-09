package com.universidad.avicola.data.model

/**
 * ProductoInventario.kt — Modelo escalado (Versión Pro)
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/data/model/
 *
 * Nuevos campos añadidos:
 *  - minStock       → stock mínimo para alertas de stock crítico
 *  - categoria      → categoría del producto (enum Categoria)
 *  - unitType       → tipo de unidad (Sacos, ml, Unidades, etc.)
 *  - numerolote     → número de lote del producto
 *  - fechaVencimientoMs → fecha de vencimiento en millisegundos
 */
data class ProductoInventario(
    val id: String = "",
    val nombre: String = "",
    val cantidad: Double = 0.0,
    val precioUnitario: Double = 0.0,

    // ── Stock crítico ──────────────────────────────
    val minStock: Double = 0.0,

    // ── Categoría y unidades ───────────────────────
    val categoria: String = Categoria.ALIMENTOS.name,
    val unitType: String = "Unidades",

    // ── Lote y caducidad ──────────────────────────
    val numeroLote: String = "",
    val fechaVencimientoMs: Long = 0L,

    // ── Auditoría ─────────────────────────────────
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActualizacion: Long = System.currentTimeMillis(),
) {
    constructor() : this("", "", 0.0, 0.0, 0.0, Categoria.ALIMENTOS.name, "Unidades", "", 0L, 0L, 0L)

    /** Retorna true si el stock actual está en nivel crítico */
    fun isStockCritico(): Boolean = cantidad <= minStock && minStock > 0

    /** Retorna true si vence en los próximos 7 días */
    fun isProximoAVencer(): Boolean {
        if (fechaVencimientoMs == 0L) return false
        val diasRestantes = (fechaVencimientoMs - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)
        return diasRestantes in 0..7
    }

    /** Retorna true si ya venció */
    fun isVencido(): Boolean {
        if (fechaVencimientoMs == 0L) return false
        return fechaVencimientoMs < System.currentTimeMillis()
    }

    /** Texto de cantidad con unidad: "10 Sacos", "500 ml" */
    fun cantidadConUnidad(): String {
        val cantStr = if (cantidad % 1.0 == 0.0) cantidad.toInt().toString()
                      else String.format("%.1f", cantidad)
        return "$cantStr $unitType"
    }
}

/**
 * Enum de categorías del inventario avícola
 */
enum class Categoria(val displayName: String) {
    ALIMENTOS("Alimentos"),
    MEDICINAS("Medicinas"),
    HERRAMIENTAS("Herramientas"),
    OTROS("Otros")
}
