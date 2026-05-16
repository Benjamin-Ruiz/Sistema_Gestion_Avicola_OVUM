package com.universidad.avicola.data.repository

import com.universidad.avicola.data.model.*

/**
 * CostosCalculator.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/repository/
 *
 * Motor de cálculo financiero del módulo de Estimación de Costos.
 * Toda la lógica de negocio está aquí, desacoplada de la UI y del ViewModel.
 */
object CostosCalculator {

    /**
     * Calcula todos los costos y resultados financieros a partir
     * de los parámetros de una estimación.
     */
    fun calcular(
        cantidadAves: Int,
        diasCrianza: Int,
        fases: List<FaseAlimentacion>,
        itemsSanitarios: List<ItemSanitario>,
        costosOperativos: List<CostoOperativo>,
        porcentajeMortalidad: Double,
        precioVentaUnitario: Double,
        costoAveInicial: Double = 0.0
    ): ResultadoCalculo {

        val alertas = mutableListOf<String>()
        val stockInsuficiente = mutableListOf<String>()

        // ── 1. Costo de alimentación ────────────────────────────────────────
        val costoAlimentacion = fases.sumOf { it.costoFase(cantidadAves) }
        fases.forEach { fase ->
            if (!fase.stockSuficiente(cantidadAves) && fase.productoInventarioId.isNotEmpty()) {
                stockInsuficiente.add("${fase.productoNombre} (Fase: ${fase.nombre})")
            }
        }
        val porcentajeAlimento = if (costoAlimentacion > 0 && cantidadAves > 0) {
            (costoAlimentacion / (cantidadAves * 10.0)) * 100
        } else 0.0
        if (porcentajeAlimento > 70) alertas.add("⚠ Costo de alimentación muy alto (>${String.format("%.0f", porcentajeAlimento)}% del total estimado)")

        // ── 2. Costo sanitario ──────────────────────────────────────────────
        val costoSanitario = itemsSanitarios.sumOf { it.costoTotal() }
        itemsSanitarios.forEach { item ->
            if (!item.stockSuficiente() && item.productoInventarioId.isNotEmpty()) {
                stockInsuficiente.add(item.nombre)
            }
        }

        // ── 3. Costo operativo ──────────────────────────────────────────────
        val costoOperativo = costosOperativos.sumOf { it.montoPorCiclo }

        // ── 4. Costo inicial de aves ────────────────────────────────────────
        val costoAves = costoAveInicial * cantidadAves

        // ── 5. Pérdida por mortalidad ───────────────────────────────────────
        val avesQueMueren = cantidadAves * (porcentajeMortalidad / 100.0)
        val costoBaseAve = if (cantidadAves > 0)
            (costoAlimentacion + costoSanitario + costoOperativo + costoAves) / cantidadAves
        else 0.0
        val perdidaMortalidad = avesQueMueren * costoBaseAve

        if (porcentajeMortalidad > 8.0) alertas.add("⚠ Mortalidad elevada (${String.format("%.1f", porcentajeMortalidad)}%). Revisa condiciones del galpón.")

        // ── 6. Costo total ──────────────────────────────────────────────────
        val costoTotal = costoAlimentacion + costoSanitario + costoOperativo + costoAves + perdidaMortalidad
        val avesSupervivientes = cantidadAves * (1 - porcentajeMortalidad / 100.0)
        val costoPorAve = if (avesSupervivientes > 0) costoTotal / avesSupervivientes else 0.0

        // ── 7. Tiempos ──────────────────────────────────────────────────────
        val costoDiario = if (diasCrianza > 0) costoTotal / diasCrianza else 0.0
        val costoSemanal = costoDiario * 7
        val costoMensual = costoDiario * 30

        // ── 8. Ingresos y rentabilidad ──────────────────────────────────────
        val ingresoEstimado = precioVentaUnitario * avesSupervivientes
        val gananciaNeta = ingresoEstimado - costoTotal
        val roi = if (costoTotal > 0) (gananciaNeta / costoTotal) * 100 else 0.0

        if (gananciaNeta < 0) alertas.add("⚠ Ganancia negativa. Revisa precios de venta o reduce costos.")
        if (roi < 5.0 && ingresoEstimado > 0) alertas.add("⚠ ROI bajo (${String.format("%.1f", roi)}%). Considera optimizar la producción.")
        if (costoPorAve > precioVentaUnitario * 0.85 && precioVentaUnitario > 0)
            alertas.add("⚠ Costo por ave (Q${String.format("%.2f", costoPorAve)}) supera el 85% del precio de venta.")

        // ── 9. Punto de equilibrio ──────────────────────────────────────────
        val margenUnitario = precioVentaUnitario - costoPorAve
        val puntoEquilibrio = if (margenUnitario > 0) costoTotal / margenUnitario else 0.0

        if (stockInsuficiente.isNotEmpty()) {
            alertas.add(0, "⛔ Inventario insuficiente para: ${stockInsuficiente.joinToString(", ")}")
        }

        return ResultadoCalculo(
            costoAlimentacion = costoAlimentacion,
            costoSanitario = costoSanitario,
            costoOperativo = costoOperativo,
            perdidaMortalidad = perdidaMortalidad,
            costoTotal = costoTotal,
            costoPorAve = costoPorAve,
            costoDiario = costoDiario,
            costoSemanal = costoSemanal,
            costoMensual = costoMensual,
            ingresoEstimado = ingresoEstimado,
            gananciaNeta = gananciaNeta,
            roi = roi,
            puntoEquilibrioUnidades = puntoEquilibrio,
            alertas = alertas,
            stockInsuficiente = stockInsuficiente
        )
    }

    /**
     * Fases de alimentación predeterminadas por tipo de ave.
     * El usuario puede editarlas y vincularlas a productos del inventario.
     */
    fun fasesDefault(tipoAve: TipoAveEstimacion): List<FaseAlimentacion> = when (tipoAve) {
        TipoAveEstimacion.ENGORDE -> listOf(
            FaseAlimentacion("Inicio", 14, 45.0, 0.0),
            FaseAlimentacion("Crecimiento", 14, 90.0, 0.0),
            FaseAlimentacion("Engorde", 14, 150.0, 0.0)
        )
        TipoAveEstimacion.PONEDORA -> listOf(
            FaseAlimentacion("Crianza", 42, 50.0, 0.0),
            FaseAlimentacion("Desarrollo", 42, 80.0, 0.0),
            FaseAlimentacion("Producción", 180, 110.0, 0.0)
        )
        TipoAveEstimacion.REPRODUCTORA -> listOf(
            FaseAlimentacion("Cría", 49, 55.0, 0.0),
            FaseAlimentacion("Levante", 70, 85.0, 0.0),
            FaseAlimentacion("Producción", 280, 150.0, 0.0)
        )
    }

    /**
     * Costos operativos predeterminados comunes.
     */
    fun costosOperativosDefault(): List<CostoOperativo> = listOf(
        CostoOperativo("Agua", TipoCostoOperativo.FIJO.name, 0.0),
        CostoOperativo("Electricidad", TipoCostoOperativo.FIJO.name, 0.0),
        CostoOperativo("Mano de Obra", TipoCostoOperativo.FIJO.name, 0.0),
        CostoOperativo("Mantenimiento del Galpón", TipoCostoOperativo.VARIABLE.name, 0.0),
        CostoOperativo("Transporte", TipoCostoOperativo.VARIABLE.name, 0.0),
        CostoOperativo("Limpieza y Desinfección", TipoCostoOperativo.FIJO.name, 0.0)
    )
}
