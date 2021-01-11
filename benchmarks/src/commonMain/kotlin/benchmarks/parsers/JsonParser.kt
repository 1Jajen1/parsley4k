package benchmarks.parsers

import parsley.ErrorItem
import parsley.Parser
import parsley.attempt
import parsley.combinators.alt
import parsley.combinators.choice
import parsley.combinators.filter
import parsley.combinators.followedBy
import parsley.combinators.followedByDiscard
import parsley.combinators.many
import parsley.combinators.map
import parsley.combinators.mapTo
import parsley.combinators.orNull
import parsley.combinators.pure
import parsley.combinators.void
import parsley.recursive
import parsley.satisfy
import parsley.string.char
import parsley.string.compile
import parsley.string.concatString
import parsley.string.string
import kotlin.jvm.JvmName

sealed class Json {
    object JsonNull : Json()
    data class JsonBool(val b: Boolean) : Json()
    data class JsonNumber(val n: Double) : Json()
    data class JsonString(val str: String) : Json()
    data class JsonArray(val arr: List<Json>) : Json()
    data class JsonObject(val map: Map<JsonString, Json>) : Json()
}

@JvmName("concatStringString")
private fun List<Any>.concatString(): String {
    val sb = StringBuilder()
    forEach { sb.append(it) }
    return sb.toString()
}

val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(Json.JsonNull))
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
    val digit: Parser<Char, Nothing, Char> = satisfy(setOf(ErrorItem.Label("Digit"))) { c: Char -> c in '0'..'9' }
    val sign = char('-').orNull()
    val jsonNumber = sign.mapTo(digit.many().concatString().filter { it.isNotEmpty() }) { s, d ->
        if (s == null) d
        else "$s$d"
    }.mapTo(char('.').followedBy(digit.many().concatString()).orNull()) { str, d ->
        if (d == null) str
        else "$str.$d"
    }.map { Json.JsonNumber(it.toDouble()) }
    val unescapedChar: Parser<Char, Nothing, Char> = satisfy(setOf(ErrorItem.Label("Any non \\ and \""))) { c: Char -> c != '\\' && c != '"' }
    val specialChar = choice(
        char('"').followedBy(pure("\\\"")),
        char('\\').followedBy(pure("\\\\")),
        char('n').followedBy(pure("\\\n")),
        char('r').followedBy(pure("\\\r")),
        char('t').followedBy(pure("\\\t")),
        char('b').followedBy(pure("\\\b")),
        char('f').followedBy(pure("\\\\f")),
    )
    val unescapedString = unescapedChar.many().concatString()
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
    val whitespace = choice(
        char(' '),
        char('\n'),
        char('\t'),
        char('\r'),
    ).many().void()

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
                jsonValueNoWhitespace.followedByDiscard(whitespace).mapTo(
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
        .followedByDiscard(whitespace)

    jsonObject = char('{')
        .followedBy(whitespace)
        .followedBy(
            choice(
                char('}').followedBy(pure(Json.JsonObject(emptyMap()))),
                keyValuePairNoWhitespace.followedByDiscard(whitespace).mapTo(
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