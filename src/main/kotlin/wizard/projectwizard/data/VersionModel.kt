package wizard.projectwizard.data

import kotlinx.serialization.Serializable

@Serializable
data class VersionModel(
    val name: String,
    val value: String
)
