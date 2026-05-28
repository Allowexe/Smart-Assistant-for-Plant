package fr.isen.veith.sap.data.influxdb

import fr.isen.veith.sap.domain.model.HistoryPoint
import java.time.Instant

object InfluxCsvParser {

    private data class Row(val time: String, val field: String, val value: Float)

    private fun parseRows(csv: String): List<Row> {
        val rows = mutableListOf<Row>()
        var timeIdx = -1; var fieldIdx = -1; var valueIdx = -1
        for (rawLine in csv.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#") -> Unit
                line.isBlank() -> { timeIdx = -1; fieldIdx = -1; valueIdx = -1 }
                line.contains("_field") && line.contains("_value") -> {
                    val cols = line.split(",")
                    timeIdx  = cols.indexOf("_time")
                    fieldIdx = cols.indexOf("_field")
                    valueIdx = cols.indexOf("_value")
                }
                else -> {
                    if (fieldIdx < 0 || valueIdx < 0) continue
                    val cols  = line.split(",")
                    if (cols.size <= maxOf(fieldIdx, valueIdx)) continue
                    val field = cols[fieldIdx].takeIf { it.isNotBlank() } ?: continue
                    val value = cols[valueIdx].toFloatOrNull() ?: continue
                    val time  = if (timeIdx in 0 until cols.size) cols[timeIdx] else ""
                    rows.add(Row(time, field, value))
                }
            }
        }
        return rows
    }

    fun parseLastValues(csv: String): Map<String, Float> =
        parseRows(csv).associate { it.field to it.value }

    fun parseHistory(csv: String): List<HistoryPoint> =
        parseRows(csv)
            .groupBy { it.time }
            .mapNotNull { (time, fieldRows) ->
                val timeMs = runCatching { Instant.parse(time).toEpochMilli() }.getOrNull()
                    ?: return@mapNotNull null
                val map = fieldRows.associate { it.field to it.value }
                HistoryPoint(
                    timeMs     = timeMs,
                    soil1      = map["soil_1"]      ?: 0f,
                    soil2      = map["soil_2"]      ?: 0f,
                    tempShtc3  = map["temp_shtc3"]  ?: 0f,
                    tempBmp180 = map["temp_bmp180"] ?: 0f
                )
            }
            .sortedBy { it.timeMs }

    // Parses the response of a distinct(column:"plant_id") query.
    // InfluxDB returns distinct values in the _value column.
    fun parsePlantIds(csv: String): List<String> {
        var valueIdx = -1
        val ids = mutableListOf<String>()
        for (rawLine in csv.lines()) {
            val line = rawLine.trim()
            when {
                line.startsWith("#") || line.isBlank() -> { valueIdx = -1 }
                else -> {
                    val cols = line.split(",")
                    val vi   = cols.indexOf("_value")
                    if (vi >= 0) { valueIdx = vi; continue }
                    if (valueIdx in 0 until cols.size) {
                        val id = cols[valueIdx].trim()
                        if (id.isNotBlank() && !id.startsWith("_") && id != "result")
                            ids.add(id)
                    }
                }
            }
        }
        return ids.distinct()
    }
}