package wizard.projectwizard.gradle.network

import wizard.projectwizard.data.VersionModel
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import wizard.projectwizard.gradle.Versions

suspend fun getVersions() {
    val client = HttpClient(CIO) {
        this.engine {
            requestTimeout = 5000
        }
    }
    try {
        val response: HttpResponse = client.get("https://api.canerture.com/qpwizard/versions")
        val versions = Json.decodeFromString<List<VersionModel>>(response.bodyAsText())
        versions.forEach {
            Versions.versionList[it.name] = it.value
        }
    } catch (e: Exception) {
        println("Failed to fetch versions: ${e.message}")
    } finally {
        client.close()
    }
}