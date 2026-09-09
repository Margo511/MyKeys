package app.mykeys.domain.port

import app.mykeys.domain.model.FoundationStatus

interface FoundationRepository {
    fun getStatus(): FoundationStatus
}
