import parsley.Parser
import parsley.attempt
import parsley.alt
import parsley.char
import parsley.choice
import parsley.filter
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.zip
import parsley.stringOf
import parsley.orNull
import parsley.pure
import parsley.void
import parsley.compile
import parsley.label
import parsley.orElse
import parsley.pretty
import parsley.recursive
import parsley.satisfy
import parsley.string

fun main() {
    val inp = jsonSample1K.toCharArray()
    compiledJsonParser.parse(inp)
        .fold({
            println(it.pretty(inp))
        }, {
            println(it)
        })
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

val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(Json.JsonNull))
        .label("null")
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
        .label("bool")
    val digit: Parser<Char, Nothing, Char> = satisfy { c: Char -> c in '0'..'9' }
        .label("digit")
    val nonZeroDigit: Parser<Char, Nothing, Char> = satisfy { c: Char -> c in '1'..'9' }
        .label("non-zero digit")
    val sign = char('-').alt(char('+')).orNull().label("sign")
    val jsonNumber =
        sign
            .followedBy(nonZeroDigit.followedBy(digit.many()).orElse(digit))
            .followedBy(char('.').followedBy(digit.many()).orNull())
            .stringOf()
            .map { Json.JsonNumber(it.toDouble()) }
            .label("number")
    val unescapedChar = satisfy { c: Char -> c != '\\' && c != '"' }
    val specialChar = satisfy { c: Char -> c == '"' || c == '\\' || c == 'n' || c == 'r' || c == 't' || c == 'b' || c == 'f' }

    val jsonString = char('"')
        .followedBy(
            unescapedChar.orElse(char('\\').followedBy(specialChar)).many()
                .stringOf().map { Json.JsonString(it) }
        )
        .followedByDiscard(char('"'))
        .label("string")
    val whitespace = satisfy { c: Char -> c == ' ' || c == '\n' || c == '\t' || c == '\r' }
        .many().void()
        .label("whitespace")

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
        .label("array")

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
        .label("object")

    jsonValueNoWhitespace
}

val compiledJsonParser = jsonRootParser.compile()
