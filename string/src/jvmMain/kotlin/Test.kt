import parsley.Parser
import parsley.attempt
import parsley.chunk
import parsley.alt
import parsley.char
import parsley.choice
import parsley.collections.IntMap
import parsley.filter
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.mapTo
import parsley.orNull
import parsley.pure
import parsley.void
import parsley.compile
import parsley.lookAhead
import parsley.recursive
import parsley.satisfy
import parsley.single
import parsley.string

fun main() {
    // Thread.sleep(5_000)
    compiledJsonParser.parse(jsonSample1K.toCharArray())
        .also {
            println(it)
        }

}

sealed class Json {
    object JsonNull : Json() {
        override fun toString(): String = "JsonNull"
    }

    data class JsonBool(val b: Boolean) : Json() {
        override fun toString(): String = "JsonBool($b)"
    }

    data class JsonNumber(val n: Double) : Json() {
        override fun toString(): String = "JsonNumber($n)"
    }

    data class JsonString(val str: String) : Json() {
        override fun toString(): String = "JsonString(\"$str\")"
    }

    data class JsonArray(val arr: List<Json>) : Json() {
        override fun toString(): String = "JsonArray($arr)"
    }

    data class JsonObject(val map: Map<JsonString, Json>) : Json() {
        override fun toString(): String = "JsonObject($map)"
    }
}

@JvmName("parserConcatString")
private fun <E> Parser<Char, E, List<Char>>.concatString(): Parser<Char, E, String> {
    return map { it.toCharArray().concatToString() }
}

private fun List<Any>.concatString(): String {
    val sb = StringBuilder()
    forEach { sb.append(it) }
    return sb.toString()
}

val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(Json.JsonNull))
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
    val digit: Parser<Char, Nothing, Char> = satisfy { c: Char -> c in '0'..'9' }
    val sign = char('-').orNull()
    val jsonNumber = sign.mapTo(digit.many().filter { it.isNotEmpty() }) { s, d ->
        if (s == null) d
        else "$s$d"
    }.mapTo(char('.').followedBy(digit.many()).orNull()) { str, d ->
        if (d == null) str
        else "$str.$d"
    }.map { Json.JsonNumber(it.toDouble()) }
    val unescapedChar = satisfy { c: Char -> c != '\\' && c != '"' }
    val specialChar = choice(
        char('"').followedBy(pure("\\\"")),
        char('\\').followedBy(pure("\\\\")),
        char('n').followedBy(pure("\\\n")),
        char('r').followedBy(pure("\\\r")),
        char('t').followedBy(pure("\\\t")),
        char('b').followedBy(pure("\\\b")),
        char('f').followedBy(pure("\\\\f")),
    )
    val unescapedString = unescapedChar.many()
    val jsonString = char('"')
        .followedBy(
            unescapedString.map { Json.JsonString(it) }
                .followedByDiscard(char('"')).attempt()
                .alt(
                    unescapedString.filter { it.isNotEmpty() }.alt(char('\\').followedBy(specialChar))
                        .many().map { Json.JsonString(it.concatString()) }
                        .followedByDiscard(char('"'))
                )
        )
    val whitespace = satisfy { c: Char -> c == ' ' || c == '\n' || c == '\t' || c == '\r' }
        .many().void()

    lateinit var jsonArray: Parser<Char, Nothing, Json.JsonArray>
    lateinit var jsonObject: Parser<Char, Nothing, Json.JsonObject>

    val jsonValueNoWhitespace = choice(
        jsonNull,
        jsonBool,
        jsonNumber,
        jsonString,
        recursive { jsonArray },
        recursive { jsonObject }
    )

    val jsonValue = whitespace.followedBy(jsonValueNoWhitespace)
        .followedByDiscard(whitespace)

    jsonArray = char('[')
        .followedBy(whitespace)
        .followedBy(
            choice(
                char(']').followedBy(pure(Json.JsonArray(emptyList()))),
                jsonValueNoWhitespace.mapTo(
                    char(',').followedBy(jsonValue).many()
                ) { head, tail ->
                    val xs = tail.toMutableList().apply { add(0, head) }
                    Json.JsonArray(xs)
                }.followedByDiscard(char(']'))
            )
        )

    val keyValuePairNoWhitespace =
        jsonString.mapTo(whitespace.followedBy(char(':')).followedBy(jsonValue)) { k, v -> k to v }
    val keyValuePair = whitespace.followedBy(keyValuePairNoWhitespace)

    jsonObject = char('{')
        .followedBy(whitespace)
        .followedBy(
            choice(
                char('}').followedBy(pure(Json.JsonObject(emptyMap()))),
                keyValuePairNoWhitespace.mapTo(
                    char(',').followedBy(keyValuePair).many()
                ) { head, tail ->
                    val map = mutableMapOf<Json.JsonString, Json>().apply {
                        put(head.first, head.second)
                        tail.forEach { put(it.first, it.second) }
                    }
                    Json.JsonObject(map)
                }.followedByDiscard(char('}'))
            )
        )

    jsonValueNoWhitespace
}

val compiledJsonParser = jsonRootParser.compile()
