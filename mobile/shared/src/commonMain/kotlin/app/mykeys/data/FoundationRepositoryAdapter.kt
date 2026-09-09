package app.mykeys.data

import app.mykeys.domain.model.FoundationStatus
import app.mykeys.domain.port.FoundationRepository

class FoundationRepositoryAdapter : FoundationRepository {
    override fun getStatus() = FoundationStatus(
        clientSideEncryptionReady = true,
        platformKeyStoreReady = true,
        zeroKnowledgeSyncReady = true,
    )
}
