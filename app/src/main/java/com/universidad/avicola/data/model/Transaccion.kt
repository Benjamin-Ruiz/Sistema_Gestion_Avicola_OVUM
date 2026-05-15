package com.universidad.avicola.data.model

data class Transaccion(
    val id: String = "",
    val tipo: String = TipoTransaccion.GASTO.name,
    val categoria: String = CategoriaGasto.ALIMENTO.name,
    val descripcion: String = "",
    val monto: Double = 0.0,
    val estado: String = EstadoPago.PAGADO.name,
    val fechaMs: Long = System.currentTimeMillis(),
    val fechaVencimientoMs: Long = 0L,
    val montoPagado: Double = 0.0,
    val contacto: String = "",
    val loteId: String = "",
    val fotoUrl: String = "",
    val notas: String = "",
    val productoInventarioId: String = "",
    val galponId: String = "",
    val userId: String = ""        // ← agregado al final
) {
    constructor() : this(
        "", TipoTransaccion.GASTO.name, CategoriaGasto.ALIMENTO.name,
        "", 0.0, EstadoPago.PAGADO.name, 0L, 0L, 0.0, "", "", "", "", "", "", ""
    )

    fun isIngreso(): Boolean = tipo == TipoTransaccion.INGRESO.name
    fun isGasto(): Boolean = tipo == TipoTransaccion.GASTO.name
    fun isPendiente(): Boolean = estado == EstadoPago.PENDIENTE.name
    fun isParcial(): Boolean = estado == EstadoPago.PARCIAL.name
    fun montoPendiente(): Double = monto - montoPagado

    fun categoriaDisplay(): String = when (tipo) {
        TipoTransaccion.INGRESO.name ->
            CategoriaIngreso.values().firstOrNull { it.name == categoria }?.displayName ?: categoria
        else ->
            CategoriaGasto.values().firstOrNull { it.name == categoria }?.displayName ?: categoria
    }
}
enum class TipoTransaccion(val displayName: String) {
    INGRESO("Ingreso"),
    GASTO("Gasto")
}

enum class CategoriaIngreso(val displayName: String) {
    VENTA_CARNE("Venta de Carne"),
    VENTA_HUEVO("Venta de Huevo"),
    VENTA_GALLINAZA("Venta de Gallinaza / Abono"),
    VENTA_DESCARTE("Venta de Aves de Descarte"),
    OTRO_INGRESO("Otro Ingreso")
}

enum class CategoriaGasto(val displayName: String) {
    ALIMENTO("Alimento"),
    MEDICINAS("Medicinas / Vacunas"),
    POLLITOS("Pollitos de un Día"),
    SERVICIOS("Servicios (Agua, Luz)"),
    NOMINA("Nómina"),
    MANTENIMIENTO("Mantenimiento del Galpón"),
    OTRO_GASTO("Otro Gasto")
}

enum class EstadoPago(val displayName: String) {
    PAGADO("Pagado"),
    PENDIENTE("Pendiente"),
    PARCIAL("Pago Parcial")
}

data class ResumenFinanciero(
    val ingresoTotal: Double = 0.0,
    val gastoTotal: Double = 0.0,
    val beneficioNeto: Double = 0.0,
    val roi: Double = 0.0,
    val costoPorAve: Double = 0.0,
    val totalPorCobrar: Double = 0.0,
    val totalPorPagar: Double = 0.0,
    val alertaGastosAltos: Boolean = false,
    val limiteGastosMensual: Double = 0.0
)

data class PuntoEquilibrio(
    val gastosFijos: Double = 0.0,
    val precioVentaUnitario: Double = 0.0,
    val costoVariableUnitario: Double = 0.0,
    val unidadesNecesarias: Double = 0.0,
    val tipoUnidad: String = "aves",
    val ingresoNecesario: Double = 0.0
)
