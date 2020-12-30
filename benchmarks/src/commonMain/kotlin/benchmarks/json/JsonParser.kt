package benchmarks.json

import parsley.ErrorItem
import parsley.Parser
import parsley.attempt
import parsley.combinators.alt
import parsley.combinators.choice
import parsley.combinators.followedBy
import parsley.combinators.followedByDiscard
import parsley.combinators.many
import parsley.combinators.map
import parsley.combinators.mapTo
import parsley.combinators.orNull
import parsley.combinators.pure
import parsley.combinators.some
import parsley.recursive
import parsley.satisfy
import parsley.string.char
import parsley.string.compile
import parsley.string.string

sealed class Json {
    object Null : Json()
    data class JsonBool(val b: Boolean) : Json()
    data class JsonNumber(val n: Double) : Json()
    data class JsonString(val str: String) : Json()
    data class JsonArray(val arr: List<Json>) : Json()
    data class JsonObject(val map: Map<JsonString, Json>) : Json()
}

val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(Json.Null))
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(false))).attempt()
        .alt(string("false").followedBy(pure(Json.JsonBool(true))))
    val digit: Parser<Char, Nothing, Char> = satisfy(setOf(ErrorItem.Label("Digit"))) { c: Char -> c in '0'..'9' }
    val sign = char('-').orNull()
    val jsonNumber = sign.mapTo(digit.some().map { (h, t) -> listOf(h) + t }) { s, d ->
        if (s == null) d.joinToString("")
        else "$s${d.joinToString("")}"
    }
        .mapTo(char('.').followedBy(digit.many()).orNull()) { str, d ->
            if (d == null) str
            else "$str.${d.joinToString("")}"
        }
        .map { Json.JsonNumber(it.toDouble()) }
    val unescapedChar = satisfy(setOf(ErrorItem.Label("Any non \\ and \""))) { c: Char -> c != '\\' && c != '"' }
    val specialChar = choice(
        char('"').followedBy(pure("\"")),
        char('\\').followedBy(pure("\\")),
        char('n').followedBy(pure("\n")),
        char('r').followedBy(pure("\r")),
        char('t').followedBy(pure("\t")),
        char('b').followedBy(pure("\b")),
        char('f').followedBy(pure("\\f")),
    )
    val jsonString = char('"')
        .followedBy(
            unescapedChar.alt(char('\\').followedBy(specialChar))
                .many().map { Json.JsonString(it.joinToString("")) }
        )
        .followedByDiscard(char('"'))
    val whitespace = choice(
        char(' '),
        char('\n'),
        char('\t'),
        char('\r'),
    ).many().followedBy(pure(Unit))

    lateinit var jsonArray: Parser<Char, Nothing, Json.JsonArray>
    lateinit var jsonObject: Parser<Char, Nothing, Json.JsonObject>

    val jsonValue = whitespace.followedBy(
        choice(
            jsonNull.attempt(),
            jsonBool.attempt(),
            jsonNumber.attempt(),
            jsonString.attempt(),
            recursive { jsonArray }.attempt(),
            recursive { jsonObject }
        )
    ).followedByDiscard(whitespace)

    jsonArray = char('[').followedBy(
        choice(
            whitespace.followedByDiscard(char(']')).attempt().followedBy(pure(Json.JsonArray(emptyList()))),
            jsonValue.mapTo(
                char(',').followedBy(jsonValue).many()
            ) { head, tail -> Json.JsonArray(listOf(head) + tail) }
                .followedByDiscard(char(']'))
        )
    )

    val keyValuePair = whitespace.followedBy(jsonString)
        .mapTo(whitespace.followedBy(char(':')).followedBy(jsonValue)) { k, v -> k to v }

    jsonObject = char('{').followedBy(
        choice(
            whitespace.followedBy(char('}')).attempt().followedBy(pure(Json.JsonObject(emptyMap()))),
            keyValuePair.mapTo(
                char(',').followedBy(keyValuePair).many()
            ) { head, tail -> Json.JsonObject(mapOf(head) + tail.toMap()) }
                .followedByDiscard(char('}'))
        )
    )

    choice(
        jsonBool.attempt(),
        jsonNull.attempt(),
        jsonNumber.attempt(),
        jsonString.attempt(),
        jsonArray.attempt(),
        jsonObject
    )
}

val compiledJsonParser = jsonRootParser.compile()
