package app.mykeys.presentation.foundation

data class FoundationCapabilityUi(
    val title: String,
    val description: String,
    val isReady: Boolean,
)

data class FoundationUiState(
    val eyebrow: String,
    val title: String,
    val subtitle: String,
    val capabilities: List<FoundationCapabilityUi>,
)
