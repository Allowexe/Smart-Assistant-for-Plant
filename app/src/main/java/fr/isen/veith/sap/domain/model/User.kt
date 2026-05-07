package fr.isen.veith.sap.domain.model

data class User(
    val id: String,
    val username: String,
    val email: String,
    val photoUrl: String? = null
)