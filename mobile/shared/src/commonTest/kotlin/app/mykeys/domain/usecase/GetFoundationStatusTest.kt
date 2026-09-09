package app.mykeys.domain.usecase

import app.mykeys.domain.model.FoundationStatus
import app.mykeys.domain.port.FoundationRepository
import kotlin.test.Test
import kotlin.test.assertTrue

class GetFoundationStatusTest {
    @Test
    fun returnsRepositoryStatusWithoutPlatformDependencies() {
        val useCase = GetFoundationStatus(
            repository = object : FoundationRepository {
                override fun getStatus() = FoundationStatus(
                    clientSideEncryptionReady = true,
                    platformKeyStoreReady = true,
                    zeroKnowledgeSyncReady = true,
                )
            },
        )

        val result = useCase()

        assertTrue(result.clientSideEncryptionReady)
        assertTrue(result.platformKeyStoreReady)
        assertTrue(result.zeroKnowledgeSyncReady)
    }
}
