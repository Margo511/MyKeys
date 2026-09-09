package app.mykeys.domain.usecase

import app.mykeys.domain.model.FoundationStatus
import app.mykeys.domain.port.FoundationRepository

class GetFoundationStatus(
    private val repository: FoundationRepository,
) {
    operator fun invoke(): FoundationStatus = repository.getStatus()
}
