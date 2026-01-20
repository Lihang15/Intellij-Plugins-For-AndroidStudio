package wizard.projectwizard.data

import kotlinx.serialization.Serializable

@Serializable
data class QPWEvent(
    val eventName: String,
    val timestamp: String = "",
)