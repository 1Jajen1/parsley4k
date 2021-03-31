package benchmarks.parsers.byte

import parsley.ParseResult
import parsley.Parser
import parsley.attempt
import parsley.alt
import parsley.bytesOf
import parsley.char
import parsley.choice
import parsley.filter
import parsley.followedBy
import parsley.followedByDiscard
import parsley.many
import parsley.map
import parsley.pure
import parsley.string
import parsley.void
import parsley.compile
import parsley.orNull
import parsley.recursive
import parsley.satisfy
import parsley.zip

private val jsonRootParser = Parser.run {
    val jsonNull = string("null").followedBy(pure(null))
    val jsonBool = string("true").followedBy(pure(true))
        .alt(string("false").followedBy(pure(false)))
    val digit = satisfy { c: Byte -> c in '0'.toByte()..'9'.toByte() }
    val sign = char('-').alt(char('+')).orNull()
    val jsonNumber =
        sign
            .followedBy(digit).followedBy(digit.many())
            .followedBy(char('.').followedBy(digit.many()).orNull())
            .bytesOf()
            .map { it.decodeToString().toDouble() }
    val unescapedChar = satisfy { c: Byte -> c != '\\'.toByte() && c != '"'.toByte() }
    val specialChar = satisfy { c: Byte -> c == '"'.toByte() || c == '\\'.toByte() || c == 'n'.toByte() || c == 'r'.toByte() || c == 't'.toByte() || c == 'b'.toByte() || c == 'f'.toByte() }
    val unescapedString = unescapedChar.many().map { it.decodeToString() }
    val jsonString = char('"')
        .followedBy(
            unescapedString
                .followedByDiscard(char('"')).attempt()
                .alt(
                    unescapedString.filter { it.isNotEmpty() }.alt(char('\\').followedBy(specialChar))
                        .many()
                        .bytesOf().map { it.decodeToString() }
                        .followedByDiscard(char('"'))
                )
        )
    val whitespace = satisfy { c: Byte -> c == ' '.toByte() || c == '\n'.toByte() || c == '\t'.toByte() || c == '\r'.toByte() }
        .many().void()

    lateinit var jsonArray: Parser<Byte, Nothing, List<Any?>>
    lateinit var jsonObject: Parser<Byte, Nothing, Map<String, Any?>>

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

private val byteCompiledJsonParser = jsonRootParser.compile()

fun byteParse(inp: ByteArray): Any = byteCompiledJsonParser.parse(inp)
    .takeIf { it is ParseResult.Done }!!
