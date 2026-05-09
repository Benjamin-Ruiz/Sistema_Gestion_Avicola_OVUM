package com.universidad.avicola.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.universidad.avicola.data.model.ProductoInventario

@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val cantidad: Double,
    val precioUnitario: Double,
    val minStock: Double,
    val categoria: String,
    val unitType: String,
    val numeroLote: String,
    val fechaVencimientoMs: Long,
    val fechaCreacion: Long,
    val fechaActualizacion: Long,
    
    // Campo de sincronización: true si ya está en Firebase, false si es cambio local pendiente
    val isSynced: Boolean = true
) {
    fun toDomain() = ProductoInventario(
        id = id,
        nombre = nombre,
        cantidad = cantidad,
        precioUnitario = precioUnitario,
        minStock = minStock,
        categoria = categoria,
        unitType = unitType,
        numeroLote = numeroLote,
        fechaVencimientoMs = fechaVencimientoMs,
        fechaCreacion = fechaCreacion,
        fechaActualizacion = fechaActualizacion
    )

    companion object {
        fun fromDomain(p: ProductoInventario, isSynced: Boolean = true) = ProductoEntity(
            id = p.id,
            nombre = p.nombre,
            cantidad = p.cantidad,
            precioUnitario = p.precioUnitario,
            minStock = p.minStock,
            categoria = p.categoria,
            unitType = p.unitType,
            numeroLote = p.numeroLote,
            fechaVencimientoMs = p.fechaVencimientoMs,
            fechaCreacion = p.fechaCreacion,
            fechaActualizacion = p.fechaActualizacion,
            isSynced = isSynced
        )
    }
}
