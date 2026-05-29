package fr.isen.veith.sap.domain.model

/**
 * Conseils de traitement + lien vers la fiche EPPO pour une maladie détectée.
 *
 * L'API maladies PlantNet renvoie un code EPPO + une description lisible.
 * On dérive un conseil par mots-clés (robuste, peu importe le code exact),
 * et on pointe vers la fiche EPPO Global Database pour les détails.
 */
object DiseaseAdvice {

    /** Conseil de traitement déduit du libellé, ou null si inconnu. */
    fun tip(label: String): String? {
        val n = label.lowercase()
        return when {
            "oïdium" in n || "oidium" in n || "powdery mildew" in n ->
                "Oïdium : retire les feuilles atteintes, aère la plante, traite au soufre ou au bicarbonate."
            "mildiou" in n || "downy mildew" in n || "blight" in n ->
                "Mildiou : évite l'eau sur le feuillage, espace les arrosages, traite à la bouillie bordelaise."
            "rouille" in n || "rust" in n ->
                "Rouille : supprime les feuilles tachées, n'arrose pas le feuillage, améliore l'aération."
            "puceron" in n || "aphid" in n ->
                "Pucerons : douche au savon noir dilué, introduis des coccinelles, surveille les jeunes pousses."
            "cochenille" in n || "scale" in n || "mealybug" in n ->
                "Cochenilles : tamponne à l'alcool à 70°, savon noir, isole la plante atteinte."
            "araignée" in n || "spider mite" in n || "acarien" in n ->
                "Acariens : augmente l'humidité ambiante, douche le feuillage, traite au savon noir."
            "botrytis" in n || "grey mould" in n || "gray mold" in n || "pourriture" in n ->
                "Pourriture grise : retire les parties atteintes, réduis l'humidité, aère davantage."
            "tache" in n || "leaf spot" in n || "spot" in n ->
                "Taches foliaires : ôte les feuilles malades, évite l'arrosage par le dessus, désinfecte les outils."
            "chlorose" in n || "chlorosis" in n || "yellow" in n || "jaun" in n ->
                "Chlorose : vérifie l'arrosage et les carences (fer, azote), ajuste l'engrais."
            else -> null
        }
    }

    /** Fiche détaillée sur EPPO Global Database. */
    fun eppoUrl(code: String): String = "https://gd.eppo.int/taxon/$code"
}
