package wizard.projectwizard.gradle.network

import wizard.projectwizard.data.VersionModel
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import wizard.projectwizard.gradle.Versions

/**
 * Mock/fallback version data in case network fetch fails.
 * These versions are kept up-to-date with common library versions.
 */
private val mockVersions = listOf(
    VersionModel("cmp-agp", "8.5.2"),
    VersionModel("cmp-kotlin", "2.1.0"),
    VersionModel("cmp-activity-compose", "1.9.3"),
    VersionModel("cmp-ui-tooling", "1.7.6"),
    VersionModel("cmp-multiplatform", "1.7.0-beta02"),
    VersionModel("cmp-koin", "4.0.0"),
    VersionModel("cmp-ktor", "3.0.1"),
    VersionModel("cmp-navigation", "2.8.0-alpha08"),
    VersionModel("cmp-kotlinx-coroutines", "1.9.0"),
    VersionModel("cmp-coil", "3.0.0-alpha06"),
    VersionModel("cmp-kamel", "0.9.5"),
    VersionModel("cmp-ksp", "2.1.0-1.0.29"),
    VersionModel("cmp-room", "2.7.0-alpha08"),
    VersionModel("cmp-sqlite", "2.5.0-SNAPSHOT"),
    VersionModel("cmp-kotlinx-serialization", "1.7.3"),
    VersionModel("ktorfit", "2.6.4"),
    VersionModel("agp", "8.9.3"),
    VersionModel("kotlin", "2.1.21"),
    VersionModel("core-ktx", "1.16.0"),
    VersionModel("junit", "4.13.2"),
    VersionModel("junit-ext", "1.2.1"),
    VersionModel("espresso-core", "3.6.1"),
    VersionModel("appcompat", "1.7.1"),
    VersionModel("material", "1.12.0"),
    VersionModel("ksp", "2.1.21-2.0.2"),
    VersionModel("lifecycle-runtime-ktx", "2.9.1"),
    VersionModel("activity-compose", "1.10.1"),
    VersionModel("compose-bom", "2025.06.00"),
    VersionModel("activity", "1.10.1"),
    VersionModel("constraintlayout", "2.2.1"),
    VersionModel("coilVersion", "2.7.0"),
    VersionModel("glideVersion", "4.16.0"),
    VersionModel("glideCompose", "1.0.0-beta01"),
    VersionModel("room", "2.7.1"),
    VersionModel("retrofit", "2.11.0"),
    VersionModel("ktor", "3.0.1"),
    VersionModel("hilt", "2.55"),
    VersionModel("hiltNavigationCompose", "1.2.0"),
    VersionModel("navigation", "2.9.0"),
    VersionModel("kotlinXSerialization", "1.8.1"),
    VersionModel("ktlint", "11.3.2"),
    VersionModel("detekt", "1.23.6"),
    VersionModel("googleServices", "4.4.2"),
    VersionModel("firebase", "33.15.0"),
    VersionModel("workManagerVersion", "2.10.1"),
    VersionModel("fragment-ktx", "1.8.8"),
)

/**
 * Loads mock/fallback versions into the version list.
 * Used when network fetch fails or times out.
 */
private fun loadMockVersions() {
    println("Loading mock versions as fallback...")
    mockVersions.forEach {
        Versions.versionList[it.name] = it.value
    }
    println("Loaded ${mockVersions.size} mock versions successfully")
}

suspend fun getVersions() {
    val client = HttpClient(CIO) {
        this.engine {
            requestTimeout = 500 // Increased timeout to 5 seconds
        }
    }
    try {
        println("Fetching versions from remote API...")
        val response: HttpResponse = client.get("https://api.canerture.com/qpwizard/versions")
        
        val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }
        val versions = json.decodeFromString<List<VersionModel>>(response.bodyAsText())
        
        versions.forEach {
            Versions.versionList[it.name] = it.value
        }
        println("Successfully fetched ${versions.size} versions from remote API")
    } catch (e: Exception) {
        println("Failed to fetch versions from remote: ${e.message}")
        println("Using mock/fallback versions instead")
        loadMockVersions()
    } finally {
        client.close()
    }
}
