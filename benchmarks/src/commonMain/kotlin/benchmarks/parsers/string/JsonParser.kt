package benchmarks.parsers.string

import benchmarks.parsers.Json
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
import parsley.orNull
import parsley.pure
import parsley.string
import parsley.void
import parsley.compile
import parsley.stringOf
import parsley.recursive
import parsley.satisfy
import parsley.zip

val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(Json.JsonNull))
    val jsonBool = string("true").followedBy(pure(Json.JsonBool(true)))
        .alt(string("false").followedBy(pure(Json.JsonBool(false))))
    val digit: Parser<Char, Nothing, Char> = satisfy { c: Char -> c in '0'..'9' }
    val sign = char('-').alt(char('+')).orNull()
    val jsonNumber =
        sign.followedBy(digit.many().filter { it.isNotEmpty() }).followedBy(char('.').followedBy(digit.many()).orNull())
            .stringOf()
            .map { Json.JsonNumber(it.toDouble()) }
    val unescapedChar = satisfy { c: Char -> c != '\\' && c != '"' }
    val specialChar = satisfy { c: Char -> c == '"' || c == '\\' || c == 'n' || c == 'r' || c == 't' || c == 'b' || c == 'f' }
    val unescapedString = unescapedChar.many()
    val jsonString = char('"')
        .followedBy(
            unescapedString.map { Json.JsonString(it) }
                .followedByDiscard(char('"')).attempt()
                .alt(
                    unescapedString.filter { it.isNotEmpty() }.alt(char('\\').followedBy(specialChar))
                        .many().stringOf().map { Json.JsonString(it) }
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
