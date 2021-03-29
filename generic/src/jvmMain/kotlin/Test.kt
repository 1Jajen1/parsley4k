import parsley.Parser
import parsley.attempt
import parsley.chunk
import parsley.alt
import parsley.choice
import parsley.chunkOf
import parsley.filter
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.zip
import parsley.orNull
import parsley.pure
import parsley.void
import parsley.compile
import parsley.recursive
import parsley.satisfy
import parsley.single

fun main() {
    val input = jsonSample1K.toCharArray().toTypedArray()
    compiledJsonParser.parse(input)
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
    fun string(str: String) = chunk(*str.toList().toTypedArray())
    fun char(c: Char) = single(c)

    val jsonNull = string("null").followedBy(pure(Json.JsonNull))
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
    val digit = satisfy<Char> { c: Char -> c in '0'..'9' }
    val digits = digit.many<Char, Nothing, Char>().concatString()
    val sign = char('-').alt(char('+')).orNull()
    val jsonNumber =
        sign
            .followedBy(digit).followedBy(digits)
            .followedBy(char('.').followedBy(digits).orNull())
            .chunkOf<Char, Nothing>()
            .map { Json.JsonNumber(it.toCharArray().concatToString().toDouble()) }
    val unescapedChar = satisfy<Char> { c: Char -> c != '\\' && c != '"' }
    val specialChar = satisfy<Char> { c: Char -> c == '"' || c == '\\' || c == 'n' || c == 'r' || c == 't' || c == 'b' || c == 'f' }
    val unescapedString = unescapedChar.many<Char, Nothing, Char>().concatString()
    val jsonString = char('"')
        .followedBy(
            unescapedString.map { Json.JsonString(it) }
                .followedByDiscard(char('"')).attempt()
                .alt(
                    unescapedString.filter { it.isNotEmpty() }.alt(char('\\').followedBy(specialChar))
                        .many().chunkOf<Char, Nothing>()
                        .map { Json.JsonString(it.toCharArray().concatToString()) }
                        .followedByDiscard(char('"'))
                )
        )
    val whitespace = satisfy<Char> { c: Char -> c == ' ' || c == '\n' || c == '\t' || c == '\r' }
        .many<Char, Nothing, Char>().void()

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
                jsonValueNoWhitespace.zip(
                    char(',').followedBy(jsonValue).many()
                ) { head, tail ->
                    val xs = tail.toMutableList().apply { add(0, head) }
                    Json.JsonArray(xs)
                }.followedByDiscard(char(']'))
            )
        )

    val keyValuePairNoWhitespace =
        jsonString.zip(whitespace.followedBy(char(':')).followedBy(jsonValue)) { k, v -> k to v }
    val keyValuePair = whitespace.followedBy(keyValuePairNoWhitespace)

    jsonObject = char('{')
        .followedBy(whitespace)
        .followedBy(
            choice(
                char('}').followedBy(pure(Json.JsonObject(emptyMap()))),
                keyValuePairNoWhitespace.zip(
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
