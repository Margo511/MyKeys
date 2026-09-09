package app.mykeys.domain.model

data class FoundationStatus(
    val clientSideEncryptionReady: Boolean,
    val platformKeyStoreReady: Boolean,
    val zeroKnowledgeSyncReady: Boolean,
)
