package app.mykeys.data.auth

import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface SecureAuthStorage {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)

    suspend fun delete(key: String)
}

internal class SecureSessionManager(
    private val storage: SecureAuthStorage,
    private val json: Json,
) : SessionManager {
    override suspend fun saveSession(session: UserSession) {
        storage.write(SESSION_KEY, json.encodeToString(UserSession.serializer(), session))
    }

    override suspend fun loadSession(): UserSession {
        val encoded = storage.read(SESSION_KEY) ?: error("No stored session")
        return try {
            json.decodeFromString(UserSession.serializer(), encoded)
        } catch (error: SerializationException) {
            storage.delete(SESSION_KEY)
            throw error
        } catch (error: IllegalArgumentException) {
            storage.delete(SESSION_KEY)
            throw error
        }
    }

    override suspend fun deleteSession() {
        storage.delete(SESSION_KEY)
    }

    private companion object {
        const val SESSION_KEY = "session.v1"
    }
}

internal class SecureCodeVerifierCache(
    private val storage: SecureAuthStorage,
) : CodeVerifierCache {
    override suspend fun saveCodeVerifier(codeVerifier: String) {
        storage.write(CODE_VERIFIER_KEY, codeVerifier)
    }

    override suspend fun loadCodeVerifier(): String? = storage.read(CODE_VERIFIER_KEY)

    override suspend fun deleteCodeVerifier() {
        storage.delete(CODE_VERIFIER_KEY)
    }

    private companion object {
        const val CODE_VERIFIER_KEY = "pkce.verifier.v1"
    }
}
