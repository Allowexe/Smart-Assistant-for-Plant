package fr.isen.veith.sap.data.repository

import fr.isen.veith.sap.domain.model.User
import kotlinx.coroutines.delay

// ── Résultat générique ─────────────────────────────────────────────
sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

// ── Interface du repository (pour faciliter les tests / injection) ──
interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(username: String, email: String, password: String): AuthResult
    suspend fun logout()
}

// ── Implémentation fictive — à remplacer par Firebase Auth ─────────
class FakeAuthRepository : AuthRepository {

    override suspend fun login(email: String, password: String): AuthResult {
        delay(1200)
        return if (email.isNotBlank() && password.length >= 6) {
            AuthResult.Success(
                User(
                    id       = "uid_001",
                    username = email.substringBefore("@"),
                    email    = email
                )
            )
        } else {
            AuthResult.Error("Email ou mot de passe invalide.")
        }
    }

    override suspend fun register(
        username: String,
        email: String,
        password: String
    ): AuthResult {
        delay(1400)
        return if (username.isNotBlank() && email.contains("@") && password.length >= 6) {
            AuthResult.Success(
                User(id = "uid_new", username = username, email = email)
            )
        } else {
            AuthResult.Error("Veuillez remplir tous les champs correctement.")
        }
    }

    override suspend fun logout() { /* noop */ }
}

// ── Implémentation Firebase (stub — décommenter et compléter) ───────
// class FirebaseAuthRepository(
//     private val auth: FirebaseAuth = FirebaseAuth.getInstance()
// ) : AuthRepository {
//
//     override suspend fun login(email: String, password: String): AuthResult =
//         try {
//             val result = auth.signInWithEmailAndPassword(email, password).await()
//             val fbUser = result.user!!
//             AuthResult.Success(User(fbUser.uid, fbUser.displayName ?: "", fbUser.email ?: ""))
//         } catch (e: Exception) {
//             AuthResult.Error(e.localizedMessage ?: "Erreur de connexion.")
//         }
//
//     override suspend fun register(username: String, email: String, password: String): AuthResult =
//         try {
//             val result = auth.createUserWithEmailAndPassword(email, password).await()
//             val fbUser = result.user!!
//             val profile = userProfileChangeRequest { displayName = username }
//             fbUser.updateProfile(profile).await()
//             AuthResult.Success(User(fbUser.uid, username, email))
//         } catch (e: Exception) {
//             AuthResult.Error(e.localizedMessage ?: "Erreur d'inscription.")
//         }
//
//     override suspend fun logout() = auth.signOut()
// }