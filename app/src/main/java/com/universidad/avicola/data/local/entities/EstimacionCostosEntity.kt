package com.universidad.avicola.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.universidad.avicola.data.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * EstimacionCostosEntity.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/data/local/entities/
 *
 * Room no soporta listas de objetos directamente.
 * Serializamos FaseAlimentacion, ItemSanitario y CostoOperativo como JSON.
 */
@Entity(tableName = "estimaciones_costos")
@TypeConverters(EstimacionConverters::class)
data class EstimacionCostosEntity(
    @PrimaryKey val id: String,
    val loteId: String,
    val loteNombre: String,
    val tipoAve: String,
    val cantidadAves: Int,
    val diasCrianza: Int,
    val pesoObjetivoKg: Double,
    val fechaCreacion: Long,
    val estado: String,
    val costoAlimentacionTotal: Double,
    val fasesJson: String,          // List<FaseAlimentacion> como JSON
    val costoSanitarioTotal: Double,
    val itemsSanitariosJson: String, // List<ItemSanitario> como JSON
    val costoOperativoTotal: Double,
    val costosOperativosJson: String, // List<CostoOperativo> como JSON
    val porcentajeMortalidad: Double,
    val perdidaMortalidad: Double,
    val precioVentaUnitario: Double,
    val ingresoEstimado: Double,
    val costoTotal: Double,
    val costoPorAve: Double,
    val gananciaNeta: Double,
    val roi: Double,
    val puntoEquilibrioUnidades: Double,
    val costoRealRegistrado: Double,
    val variacionPorcentaje: Double,
    val alertasJson: String,
    val notas: String
) {
    fun toDomain() = EstimacionCostos(
        id = id,
        loteId = loteId,
        loteNombre = loteNombre,
        tipoAve = tipoAve,
        cantidadAves = cantidadAves,
        diasCrianza = diasCrianza,
        pesoObjetivoKg = pesoObjetivoKg,
        fechaCreacion = fechaCreacion,
        estado = estado,
        costoAlimentacionTotal = costoAlimentacionTotal,
        fases = EstimacionConverters.fasesFromJson(fasesJson),
        costoSanitarioTotal = costoSanitarioTotal,
        itemsSanitarios = EstimacionConverters.itemsSanitariosFromJson(itemsSanitariosJson),
        costoOperativoTotal = costoOperativoTotal,
        costosOperativos = EstimacionConverters.costosOperativosFromJson(costosOperativosJson),
        porcentajeMortalidad = porcentajeMortalidad,
        perdidaMortalidad = perdidaMortalidad,
        precioVentaUnitario = precioVentaUnitario,
        ingresoEstimado = ingresoEstimado,
        costoTotal = costoTotal,
        costoPorAve = costoPorAve,
        gananciaNeta = gananciaNeta,
        roi = roi,
        puntoEquilibrioUnidades = puntoEquilibrioUnidades,
        costoRealRegistrado = costoRealRegistrado,
        variacionPorcentaje = variacionPorcentaje,
        alertas = EstimacionConverters.stringListFromJson(alertasJson),
        notas = notas
    )

    companion object {
        fun fromDomain(e: EstimacionCostos) = EstimacionCostosEntity(
            id = e.id,
            loteId = e.loteId,
            loteNombre = e.loteNombre,
            tipoAve = e.tipoAve,
            cantidadAves = e.cantidadAves,
            diasCrianza = e.diasCrianza,
            pesoObjetivoKg = e.pesoObjetivoKg,
            fechaCreacion = e.fechaCreacion,
            estado = e.estado,
            costoAlimentacionTotal = e.costoAlimentacionTotal,
            fasesJson = EstimacionConverters.fasesToJson(e.fases),
            costoSanitarioTotal = e.costoSanitarioTotal,
            itemsSanitariosJson = EstimacionConverters.itemsSanitariosToJson(e.itemsSanitarios),
            costoOperativoTotal = e.costoOperativoTotal,
            costosOperativosJson = EstimacionConverters.costosOperativosToJson(e.costosOperativos),
            porcentajeMortalidad = e.porcentajeMortalidad,
            perdidaMortalidad = e.perdidaMortalidad,
            precioVentaUnitario = e.precioVentaUnitario,
            ingresoEstimado = e.ingresoEstimado,
            costoTotal = e.costoTotal,
            costoPorAve = e.costoPorAve,
            gananciaNeta = e.gananciaNeta,
            roi = e.roi,
            puntoEquilibrioUnidades = e.puntoEquilibrioUnidades,
            costoRealRegistrado = e.costoRealRegistrado,
            variacionPorcentaje = e.variacionPorcentaje,
            alertasJson = EstimacionConverters.stringListToJson(e.alertas),
            notas = e.notas
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Type Converters JSON
// ─────────────────────────────────────────────────────────────────────────────
class EstimacionConverters {
    companion object {

        @TypeConverter
        @JvmStatic
        fun fasesToJson(list: List<FaseAlimentacion>): String {
            val arr = JSONArray()
            list.forEach { f ->
                arr.put(JSONObject().apply {
                    put("nombre", f.nombre)
                    put("diasDuracion", f.diasDuracion)
                    put("consumoDiarioGrPorAve", f.consumoDiarioGrPorAve)
                    put("precioKg", f.precioKg)
                    put("productoInventarioId", f.productoInventarioId)
                    put("productoNombre", f.productoNombre)
                    put("stockDisponible", f.stockDisponible)
                })
            }
            return arr.toString()
        }

        @TypeConverter
        @JvmStatic
        fun fasesFromJson(json: String): List<FaseAlimentacion> {
            if (json.isEmpty() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    FaseAlimentacion(
                        nombre = o.optString("nombre"),
                        diasDuracion = o.optInt("diasDuracion"),
                        consumoDiarioGrPorAve = o.optDouble("consumoDiarioGrPorAve"),
                        precioKg = o.optDouble("precioKg"),
                        productoInventarioId = o.optString("productoInventarioId"),
                        productoNombre = o.optString("productoNombre"),
                        stockDisponible = o.optDouble("stockDisponible")
                    )
                }
            } catch (e: Exception) { emptyList() }
        }

        @TypeConverter
        @JvmStatic
        fun itemsSanitariosToJson(list: List<ItemSanitario>): String {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(JSONObject().apply {
                    put("nombre", s.nombre)
                    put("tipo", s.tipo)
                    put("dosisParaLote", s.dosisParaLote)
                    put("precioUnitario", s.precioUnitario)
                    put("productoInventarioId", s.productoInventarioId)
                    put("stockDisponible", s.stockDisponible)
                })
            }
            return arr.toString()
        }

        @TypeConverter
        @JvmStatic
        fun itemsSanitariosFromJson(json: String): List<ItemSanitario> {
            if (json.isEmpty() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ItemSanitario(
                        nombre = o.optString("nombre"),
                        tipo = o.optString("tipo"),
                        dosisParaLote = o.optDouble("dosisParaLote"),
                        precioUnitario = o.optDouble("precioUnitario"),
                        productoInventarioId = o.optString("productoInventarioId"),
                        stockDisponible = o.optDouble("stockDisponible")
                    )
                }
            } catch (e: Exception) { emptyList() }
        }

        @TypeConverter
        @JvmStatic
        fun costosOperativosToJson(list: List<CostoOperativo>): String {
            val arr = JSONArray()
            list.forEach { c ->
                arr.put(JSONObject().apply {
                    put("nombre", c.nombre)
                    put("tipo", c.tipo)
                    put("montoPorCiclo", c.montoPorCiclo)
                })
            }
            return arr.toString()
        }

        @TypeConverter
        @JvmStatic
        fun costosOperativosFromJson(json: String): List<CostoOperativo> {
            if (json.isEmpty() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    CostoOperativo(
                        nombre = o.optString("nombre"),
                        tipo = o.optString("tipo"),
                        montoPorCiclo = o.optDouble("montoPorCiclo")
                    )
                }
            } catch (e: Exception) { emptyList() }
        }

        @TypeConverter
        @JvmStatic
        fun stringListToJson(list: List<String>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            return arr.toString()
        }

        @TypeConverter
        @JvmStatic
        fun stringListFromJson(json: String): List<String> {
            if (json.isEmpty() || json == "[]") return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }
            } catch (e: Exception) { emptyList() }
        }
    }
}
