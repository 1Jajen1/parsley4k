package benchmarks.parsers.generic

import benchmarks.parsers.Json
import parsley.Parser
import parsley.chunk
import parsley.alt
import parsley.attempt
import parsley.choice
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.orNull
import parsley.pure
import parsley.void
import parsley.compile
import parsley.filter
import parsley.recursive
import parsley.satisfy
import parsley.single
import parsley.zip
import kotlin.jvm.JvmName

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
    val sign = char('-').orNull()
    val jsonNumber = sign.zip(digit.many<Char, Nothing, Char>().concatString().filter { it.isNotEmpty() }) { s, d ->
        if (s == null) d
        else "$s$d"
    }.zip(char('.').followedBy(digit.many<Char, Nothing, Char>().concatString()).orNull()) { str, d ->
        if (d == null) str
        else "$str.$d"
    }.map { Json.JsonNumber(it.toDouble()) }
    val unescapedChar = satisfy<Char> { c: Char -> c != '\\' && c != '"' }
    val specialChar = choice(
        char('"').followedBy(pure("\\\"")),
        char('\\').followedBy(pure("\\\\")),
        char('n').followedBy(pure("\\\n")),
        char('r').followedBy(pure("\\\r")),
        char('t').followedBy(pure("\\\t")),
        char('b').followedBy(pure("\\\b")),
        char('f').followedBy(pure("\\\\f")),
    )
    val unescapedString = unescapedChar.many<Char, Nothing, Char>().concatString()
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

val compiledJsonParser = jsonRootParser.compile<Char, Nothing, Json>()
