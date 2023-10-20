@file:Suppress("MatchingDeclarationName")

package it.unibo.pulverization

import arrow.fx.coroutines.parMap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.jetbrains.kotlinx.dataframe.io.writeCSV
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path

@Serializable
data class CountryCodeResponse(val zoneName: String = "", val countryName: String? = "")
typealias CountryCode = Map<String, CountryCodeResponse>

suspend fun main() {
    val destinationFolderPath = Path("build/electricityMaps")
    val year = "2022"
    val europeCountryCodes = listOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE",
        "IT", "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB",
    )

    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    val zonesCodes = client.get("https://api.electricitymap.org/v3/zones").body<CountryCode>()

    val destinationFolder = withContext(Dispatchers.IO) {
        Files.createDirectories(destinationFolderPath)
    }

    val concatenatedDataframe = zonesCodes.keys
        .filter { c -> europeCountryCodes.any { c.startsWith(it) } }
        .map { c -> c to getDownloadUrlByCountryCode(c, year) }
        .parMap { (code, url) -> download(client, url, code, year, destinationFolder) }
        .filterNotNull()
        .map { DataFrame.readCSV(it.absolutePath) }
        .concat()

    concatenatedDataframe.writeCSV(File(destinationFolderPath.toFile(), "aggregated.csv"))
}

fun getDownloadUrlByCountryCode(code: String, year: String): String =
    "https://data.electricitymaps.com/${code}_${year}_hourly.csv"

suspend fun download(
    client: HttpClient,
    url: String,
    code: String,
    year: String,
    destinationFolder: Path,
): File? {
    return with(client.get(url)) {
        if (status == HttpStatusCode.OK) {
            val filename = File(destinationFolder.toFile(), "$code-${year}_hourly.csv")
            bodyAsChannel().copyTo(filename.writeChannel())
            filename
        } else { null }
    }
}
