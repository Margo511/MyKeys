package app.mykeys.data.auth

import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureAuthStorageTest {
    @Test
    fun sessionManagerRoundTripsThroughTheSecureStorageBoundary() = runTest {
        val storage = MemorySecureStorage()
        val manager = SecureSessionManager(storage, Json { encodeDefaults = true })
        val session = UserSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 900,
            tokenType = "bearer",
            user = UserInfo(aud = "authenticated", id = "user-a", email = "a@example.com"),
        )

        manager.saveSession(session)

        assertEquals(session, manager.loadSession())
        manager.deleteSession()
        assertNull(storage.read("session.v1"))
    }

    @Test
    fun corruptSessionIsDeletedInsteadOfBeingPartiallyRestored() = runTest {
        val storage = MemorySecureStorage().apply { write("session.v1", "not-json") }
        val manager = SecureSessionManager(storage, Json)

        assertFailsWith<Exception> { manager.loadSession() }
        assertNull(storage.read("session.v1"))
    }

    @Test
    fun configurationAllowsOnlyPublishableKeysAndLocalPlainHttp() {
        assertTrue(SupabaseAuthConfig("https://project.supabase.co", "sb_publishable_public").isValid)
        assertTrue(SupabaseAuthConfig("http://10.0.2.2:54321", "sb_publishable_local").isValid)
        assertFalse(SupabaseAuthConfig("http://example.com", "sb_publishable_public").isValid)
        assertFalse(SupabaseAuthConfig("https://project.supabase.co", "sb_secret_server").isValid)
        assertFalse(SupabaseAuthConfig("https://project.supabase.co", "service_role").isValid)
    }

    private class MemorySecureStorage : SecureAuthStorage {
        private val values = mutableMapOf<String, String>()

        override suspend fun read(key: String): String? = values[key]

        override suspend fun write(key: String, value: String) {
            values[key] = value
        }

        override suspend fun delete(key: String) {
            values.remove(key)
        }
    }
}
