package fr.isen.veith.sap.data.plant

import android.content.Context
import fr.isen.veith.sap.domain.model.Plant
import org.json.JSONArray
import java.text.Normalizer

object PlantDatabase {

    // Raw nom string kept for full-text matching; Plant holds the parsed thresholds
    private data class Entry(val nom: String, val plant: Plant)

    private var entries: List<Entry> = emptyList()

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        runCatching {
            val json = context.assets.open("plants.json").bufferedReader().readText()
            val arr  = JSONArray(json)
            entries  = (0 until arr.length()).mapNotNull { i ->
                runCatching { parseEntry(arr.getJSONObject(i)) }.getOrNull()
            }
        }
    }

    private fun parseEntry(obj: org.json.JSONObject): Entry? {
        val nom    = obj.getString("nom")
        val tSol   = obj.getJSONObject("t_sol")
        val humSol = obj.getJSONObject("hum_sol")
        val lux    = obj.getJSONObject("lux")

        // Skip soil-less plants (t_sol.max == 0 → Tillandsia)
        if (tSol.getDouble("max") == 0.0) return null

        val (scientific, common) = splitNom(nom)
        val plant = Plant(
            id             = scientific.replace(" ", "_").lowercase(),
            commonName     = common,
            scientificName = scientific,
            emoji          = emojiFor(scientific),
            humidityMin    = humSol.getDouble("min").toFloat(),
            humidityMax    = humSol.getDouble("max").toFloat(),
            luxMin         = lux.getDouble("min").toFloat(),
            luxMax         = lux.getDouble("max").toFloat(),
            tempMin        = tSol.getDouble("min").toFloat(),
            tempMax        = tSol.getDouble("max").toFloat()
        )
        return Entry(nom, plant)
    }

    // Returns (scientificName, commonName).
    // Format "Scientific name (Nom commun)" covers 90 % of entries.
    // Edge case "Olivier (Olea europaea)": before has 1 word + inside has 2+ → swap.
    private fun splitNom(nom: String): Pair<String, String> {
        val p = nom.indexOf('(')
        if (p < 1) return nom.trim() to nom.trim()
        val before = nom.substring(0, p).trim()
        val inside = nom.substring(p + 1).trimEnd(')').trim()
        return if (before.split(" ").size == 1 && inside.split(" ").size >= 2) {
            inside to before   // "Olivier (Olea europaea)" → scientific=inside, common=before
        } else {
            before to inside   // standard case
        }
    }

    /**
     * Returns the best-matching Plant (with thresholds) for a PlantNet result.
     * Keeps the caller's commonName/scientificName for display; caller should .copy() those back.
     */
    fun findBestMatch(scientificName: String, commonName: String = ""): Plant? {
        if (entries.isEmpty()) return null

        val qFull  = norm(scientificName)
        val qGenus = qFull.substringBefore(" ")

        // 1. Exact match on full scientific name anywhere in nom
        entries.find { norm(it.nom).contains(qFull) }?.let { return it.plant }

        // 2. Common name match
        if (commonName.isNotBlank()) {
            val qCommon = norm(commonName)
            entries.find { norm(it.nom).contains(qCommon) }?.let { return it.plant }
        }

        // 3. Genus match — if unambiguous, return it; otherwise pick most specific
        val byGenus = entries.filter { norm(it.nom).startsWith(qGenus) ||
                                       norm(it.plant.scientificName).startsWith(qGenus) }
        if (byGenus.size == 1) return byGenus.first().plant
        if (byGenus.isNotEmpty())
            return byGenus.maxByOrNull { countCommonWords(qFull, norm(it.nom)) }?.plant

        return null
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}"), "")

    private fun countCommonWords(a: String, b: String): Int =
        (a.split(" ").toSet() intersect b.split(" ").toSet()).size

    private fun emojiFor(name: String): String {
        val n = name.lowercase()
        return when {
            "cactus" in n || "mammillaria" in n || "echinocactus" in n ||
            "opuntia" in n || "cereus" in n || "astrophytum" in n ||
            "gymnocalycium" in n || "rhipsalis" in n || "schlumbergera" in n ||
            "hatiora" in n || "epiphyllum" in n -> "🌵"
            "orchid" in n || "phalaenopsis" in n || "macodes" in n || "ludisia" in n -> "🌸"
            "lavande" in n || "lavandula" in n -> "💜"
            "olivier" in n || "olea" in n -> "🫒"
            "basilic" in n -> "🌿"
            "menthe" in n || "mentha" in n -> "🍃"
            "fougère" in n || "asplenium" in n || "pellaea" in n ||
            "adiantum" in n || "nephrolepis" in n || "phlebodium" in n -> "🌿"
            "lithops" in n -> "🪨"
            "dionaea" in n || "drosera" in n || "pinguicula" in n -> "🪲"
            else -> "🌱"
        }
    }
}
