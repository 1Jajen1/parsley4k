package benchmarks.parsers.generic

import parsley.Either
import parsley.Parser
import parsley.chunk
import parsley.alt
import parsley.attempt
import parsley.choice
import parsley.chunkOf
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

private val genericJsonRootParser = Parser.run {
    fun string(str: String) = chunk(*str.toList().toTypedArray())
    fun char(c: Char) = single(c)

    val jsonNull = string("null").followedBy(pure(null))
    val jsonBool = string("true").followedBy(pure(true))
        .alt(string("false").followedBy(pure(false)))
    val digit = satisfy { c: Char -> c in '0'..'9' }
    val digits = digit.many().concatString()
    val sign = char('-').orNull()
    val jsonNumber =
        sign
            .followedBy(digit).followedBy(digits)
            .followedBy(char('.').followedBy(digits).orNull())
            .chunkOf()
            .map { it.toCharArray().concatToString().toDouble() }
    val unescapedChar = satisfy { c: Char -> c != '\\' && c != '"' }
    val specialChar = satisfy { c: Char -> c == '"' || c == '\\' || c == 'n' || c == 'r' || c == 't' || c == 'b' || c == 'f' }
    val unescapedString = unescapedChar.many().concatString()
    val jsonString = char('"')
        .followedBy(
            unescapedString
                .followedByDiscard(char('"')).attempt()
                .alt(
                    unescapedString.filter { it.isNotEmpty() }.alt(char('\\').followedBy(specialChar))
                        .many().chunkOf<Char, Nothing>()
                        .map { it.toCharArray().concatToString() }
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
        jsonString.zip(whitespace.followedBy(char(':')).followedBy(jsonValue)) { k, v -> k to v }
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

private val genericCompiledJsonParser = genericJsonRootParser.compile()

fun genericParse(inp: Array<Char>): Any = genericCompiledJsonParser.parse(inp)
    .takeIf { it is Either.Right }!!
