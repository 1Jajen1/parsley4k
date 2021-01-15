package benchmarks.parsers

sealed class Json {
    object JsonNull : Json()
    data class JsonBool(val b: Boolean) : Json()
    data class JsonNumber(val n: Double) : Json()
    data class JsonString(val str: String) : Json()
    data class JsonArray(val arr: List<Json>) : Json()
    data class JsonObject(val map: Map<JsonString, Json>) : Json()
}
