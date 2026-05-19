package fr.isen.veith.sap.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import fr.isen.veith.sap.domain.model.User
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

interface AuthRepository {
    suspend fun login(email: String, password: String): AuthResult
    suspend fun register(username: String, email: String, password: String): AuthResult
    suspend fun logout()
    fun currentUser(): User?
}

class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    override fun currentUser(): User? = auth.currentUser?.let { fb ->
        User(id = fb.uid, username = fb.displayName ?: "", email = fb.email ?: "")
    }

    override suspend fun login(email: String, password: String): AuthResult =
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val fb = result.user!!
            AuthResult.Success(User(id = fb.uid, username = fb.displayName ?: email.substringBefore("@"), email = fb.email ?: email))
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Erreur de connexion.")
        }

    override suspend fun register(username: String, email: String, password: String): AuthResult =
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val fb = result.user!!
            fb.updateProfile(userProfileChangeRequest { displayName = username }).await()
            AuthResult.Success(User(id = fb.uid, username = username, email = fb.email ?: email))
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Erreur d'inscription.")
        }

    override suspend fun logout() = auth.signOut()
}