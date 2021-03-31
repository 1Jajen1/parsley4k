import parsley.Parser
import parsley.alt
import parsley.bytesOf
import parsley.char
import parsley.choice
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.zip
import parsley.orNull
import parsley.pure
import parsley.void
import parsley.compile
import parsley.hide
import parsley.label
import parsley.orElse
import parsley.pretty
import parsley.recursive
import parsley.satisfy
import parsley.some
import parsley.string

fun main() {
    val p = Parser.run {
        char('a').many()
    }.compile()

    val chunks = listOf("aaaa", "aaa", "")
    val init = p.parseStreaming("a".encodeToByteArray())
    val res = chunks.fold(init) { acc, s -> acc.pushChunk(s.encodeToByteArray()) }

    val inp = jsonSample1K.encodeToByteArray()
    compiledJsonParser.parse(inp)
        .fold({ res, rem ->
            println(res)
        }, {
            throw IllegalStateException("Why exactly is this not done?")
        }, { err, rem ->
            println(err.pretty(inp))
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
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
    val digit: Parser<Byte, Nothing, Byte> = satisfy { c: Byte -> c in '0'.toByte()..'9'.toByte() }
    val nonZeroDigit: Parser<Byte, Nothing, Byte> = satisfy { c: Byte -> c in '1'.toByte()..'9'.toByte() }
    val sign = char('-').alt(char('+')).orNull().label("sign")
    val jsonNumber =
        sign
            .followedBy(
                nonZeroDigit.followedBy(digit.many())
                    .orElse(char('0'))
                    .label("number"))
            .followedBy(char('.').followedBy(digit.some()).orNull())
            .bytesOf()
            .map { Json.JsonNumber(it.decodeToString().toDouble()) }
    val unescapedChar = satisfy { c: Byte -> c != '\\'.toByte() && c != '"'.toByte() }
    val specialChar = satisfy { c: Byte -> c == '"'.toByte() || c == '\\'.toByte() || c == 'n'.toByte() || c == 'r'.toByte() || c == 't'.toByte() || c == 'b'.toByte() || c == 'f'.toByte() }

    val jsonString = char('"')
        .followedBy(
            unescapedChar.orElse(char('\\').followedBy(specialChar)).many()
                .bytesOf()
                .map { Json.JsonString(it.decodeToString()) }
        )
        .followedByDiscard(char('"'))
        .label("string")
    val whitespace = satisfy { c: Byte -> c == ' '.toByte() || c == '\n'.toByte() || c == '\t'.toByte() || c == '\r'.toByte() }
        .many().void()
        .hide()

    lateinit var jsonArray: Parser<Byte, Nothing, Json.JsonArray>
    lateinit var jsonObject: Parser<Byte, Nothing, Json.JsonObject>

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
