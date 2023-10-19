package it.unibo.pulverization

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitObjectResponse
import com.github.kittinunf.fuel.serialization.kotlinxDeserializerOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class CountryCodeResponse(val zoneName: String = "", val countryName: String? = "")

suspend fun main() {
    println(Json.decodeFromString<Map<String, CountryCodeResponse>>(Fuel.get("https://api.electricitymap.org/v3/zones").responseString().third.get()))
//    Fuel.get("https://api.electricitymap.org/v3/zones")
//        .awaitObjectResponse<Map<String, CountryCodeResponse>>(kotlinxDeserializerOf())
//        .third
//        .also { println(it) }

//    println("No")
}
