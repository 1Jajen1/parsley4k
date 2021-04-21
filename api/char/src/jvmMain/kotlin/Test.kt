import parsley.Parser
import parsley.alt
import parsley.attempt
import parsley.char
import parsley.choice
import parsley.compile
import parsley.filter
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.orNull
import parsley.pretty
import parsley.pure
import parsley.recursive
import parsley.satisfy
import parsley.string
import parsley.stringOf
import parsley.void
import parsley.zip

fun main() {
    /*
    val p = Parser.run {
        char('a').many()
    }.compile()

    val chunks = listOf("aaaa", "aaa")
    val init = p.parseStreaming("a".toCharArray())
    val res = chunks.fold(init) { acc, s -> acc.pushChunk(s.toCharArray()) }
        .pushEndOfInput()
     */

    val inp = jsonSample1K.toCharArray()
    compiledJsonParser.parse(inp)
        .fold({ res, _ ->
            println(res)
        }, {
            throw IllegalStateException("Why exactly is this not done?")
        }, { err, _ ->
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

private val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(null))
    val jsonBool = string("true").followedBy(pure(true))
        .alt(string("false").followedBy(pure(false)))
    val digit = satisfy { c: Char -> c in '0'..'9' }
    val sign = char('-').alt(char('+')).orNull()
    val jsonNumber =
        sign
            .followedBy(digit).followedBy(digit.many())
            .followedBy(char('.').followedBy(digit.many()).orNull())
            .stringOf()
            .map { it.toDouble() }
    val unescapedChar = satisfy { c: Char -> c != '\\' && c != '"' }
    val specialChar = satisfy { c: Char -> c == '"' || c == '\\' || c == 'n' || c == 'r' || c == 't' || c == 'b' || c == 'f' }
    val unescapedString = unescapedChar.many()
    val jsonString = char('"')
        .followedBy(
            unescapedString
                .followedByDiscard(char('"')).attempt()
                .alt(
                    unescapedString.filter { it.isNotEmpty() }.alt(char('\\').followedBy(specialChar))
                        .many().stringOf()
                        .followedByDiscard(char('"'))
                )
        )
    val whitespace = satisfy { c: Char -> c == ' ' || c == '\n' || c == '\t' || c == '\r' }
        .many().void()

    lateinit var jsonArray: Parser<Char, Nothing, List<Any?>>
    lateinit var jsonObject: Parser<Char, Nothing, Map<String, Any?>>

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
                char(']').followedBy(pure(emptyList())),
                jsonValueNoWhitespace.zip(
                    char(',').followedBy(jsonValue).many()
                ) { head, tail ->
                    tail.toMutableList().apply { add(0, head) }
                }.followedByDiscard(char(']'))
            )
        )

    val keyValuePairNoWhitespace =
        jsonString.zip(whitespace.followedBy(char(':')).followedBy(jsonValue))
    val keyValuePair = whitespace.followedBy(keyValuePairNoWhitespace)

    jsonObject = char('{')
        .followedBy(whitespace)
        .followedBy(
            choice(
                char('}').followedBy(pure(emptyMap())),
                keyValuePairNoWhitespace.zip(
                    char(',').followedBy(keyValuePair).many()
                ) { head, tail ->
                    mutableMapOf<String, Any?>().apply {
                        put(head.first, head.second)
                        tail.forEach { put(it.first, it.second) }
                    }
                }.followedByDiscard(char('}'))
            )
        )

    jsonValueNoWhitespace
}

private val compiledJsonParser = jsonRootParser.compile()


/*
val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(Json.JsonNull))
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
    val digit: Parser<Char, Nothing, Char> = satisfy { c: Char -> c in '0'..'9' }
    val nonZeroDigit: Parser<Char, Nothing, Char> = satisfy { c: Char -> c in '1'..'9' }
    val sign = char('-').alt(char('+')).orNull().label("sign")
    val jsonNumber =
        sign
            .followedBy(
                nonZeroDigit.followedBy(digit.many())
                    .orElse(char('0'))
                    .label("number"))
            .followedBy(char('.').followedBy(digit.some()).orNull())
            .stringOf()
            .map { Json.JsonNumber(it.toDouble()) }
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
        .hide()

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
 */
