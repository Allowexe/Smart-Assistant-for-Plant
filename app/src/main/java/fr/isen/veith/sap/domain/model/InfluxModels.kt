package fr.isen.veith.sap.domain.model

data class EnvironmentData(
    val soil1: Float = 0f,
    val soil2: Float = 0f,
    val tempShtc3: Float = 0f,
    val tempBmp180: Float = 0f
)

data class LightSpectrum(
    val violet: Float = 0f,
    val indigo: Float = 0f,
    val blue: Float = 0f,
    val cyan: Float = 0f,
    val green: Float = 0f,
    val yellow: Float = 0f,
    val orange: Float = 0f,
    val red: Float = 0f,
    val luminosity: Float = 0f
)

/**
 * Nature de la source lumineuse, déduite de la signature spectrale de l'AS734X.
 *
 * Heuristique (seuils ajustables) :
 *  - ARTIFICIAL : creux dans le cyan (~490 nm) typique des LED/fluo blanches,
 *    OU forte dominance du rouge sur le bleu (incandescent).
 *  - SUNLIGHT   : spectre continu/lisse avec bleu et rouge équilibrés.
 *  - UNKNOWN    : trop sombre pour conclure.
 */
enum class LightSource {
    SUNLIGHT, ARTIFICIAL, UNKNOWN;

    companion object {
        fun from(s: LightSpectrum): LightSource {
            val channels = listOf(
                s.violet, s.indigo, s.blue, s.cyan,
                s.green, s.yellow, s.orange, s.red
            )
            val total = channels.sum()
            if (total <= 0f || s.luminosity < 50f) return UNKNOWN

            // Creux cyan (~490 nm) : signature LED/fluo blanche
            val cyanRef = (s.blue + s.green) / 2f
            val cyanDip = if (cyanRef > 0f) s.cyan / cyanRef else 1f
            if (cyanDip < 0.7f) return ARTIFICIAL

            // Incandescent : rouge >> bleu
            val redBlue = if (s.blue > 0f) s.red / s.blue else Float.MAX_VALUE
            if (redBlue > 2.5f) return ARTIFICIAL

            // Soleil : spectre lisse (faible variation entre canaux voisins) + équilibre bleu/rouge
            val mean = total / channels.size
            val raggedness =
                channels.zipWithNext { a, b -> kotlin.math.abs(a - b) }.sum() / (mean * channels.size)
            return if (raggedness < 0.6f && redBlue in 0.5f..2.0f) SUNLIGHT else ARTIFICIAL
        }
    }
}

data class HistoryPoint(
    val timeMs: Long,
    val soil1: Float,
    val soil2: Float,
    val tempShtc3: Float,
    val tempBmp180: Float
)