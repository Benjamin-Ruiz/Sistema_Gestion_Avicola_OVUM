package com.universidad.avicola.data.model

/**
 * InventoryLog.kt — Historial de movimientos
 * ─────────────────────────────────────────────────────
 * Ubicación: app/src/main/java/com/universidad/avicola/data/model/
 *
 * Cada vez que se agrega, edita o elimina un producto,
 * se registra un log con el cambio de cantidad y la razón.
 */
data class InventoryLog(
    val id: String = "",
    val productoId: String = "",
    val productoNombre: String = "",
    val quantityChange: Double = 0.0,   // positivo = entrada, negativo = salida
    val reason: String = "",            // "Compra", "Ajuste", "Merma", "Venta", etc.
    val timestamp: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", 0.0, "", 0L)

    /** Texto con signo: "+50 Sacos" o "-10 Unidades" */
    fun changeText(unitType: String = "Unidades"): String {
        val signo = if (quantityChange >= 0) "+" else ""
        val cantStr = if (quantityChange % 1.0 == 0.0)
            quantityChange.toInt().toString()
        else String.format("%.1f", quantityChange)
        return "$signo$cantStr $unitType"
    }

    /** Color del cambio: verde si es positivo, rojo si es negativo */
    fun isPositivo(): Boolean = quantityChange >= 0
}
