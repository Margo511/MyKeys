package app.mykeys.presentation.foundation

import app.mykeys.domain.usecase.GetFoundationStatus

class FoundationPresenter(
    private val getFoundationStatus: GetFoundationStatus,
) {
    fun present(): FoundationUiState {
        val status = getFoundationStatus()
        return FoundationUiState(
            eyebrow = "FASE 1 · BASE NATIVA",
            title = "Tus secretos, solo tuyos.",
            subtitle = "Una base segura y multiplataforma para construir My Keys sin entregar las claves al servidor.",
            capabilities = listOf(
                FoundationCapabilityUi(
                    title = "Cifrado en el dispositivo",
                    description = "El dominio exige cifrar antes de persistir o sincronizar.",
                    isReady = status.clientSideEncryptionReady,
                ),
                FoundationCapabilityUi(
                    title = "Claves protegidas por la plataforma",
                    description = "Los adaptadores enlazarán Android Keystore y Apple Keychain.",
                    isReady = status.platformKeyStoreReady,
                ),
                FoundationCapabilityUi(
                    title = "Sincronización zero-knowledge",
                    description = "La nube solo recibirá datos cifrados y metadatos mínimos.",
                    isReady = status.zeroKnowledgeSyncReady,
                ),
            ),
        )
    }
}
