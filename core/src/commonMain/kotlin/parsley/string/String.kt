package parsley.string

import parsley.ErrorItem
import parsley.FancyError
import parsley.ParseError
import parsley.Parser
import parsley.combinators.followedBy
import parsley.combinators.pure
import parsley.internal.frontend.ParserF
import parsley.internal.frontend.Predicate
import parsley.single
import kotlin.math.max

fun Parser.Companion.char(c: Char): Parser<Char, Nothing, Char> = Parser.single(c)

fun Parser.Companion.string(str: String): Parser<Char, Nothing, String> =
    str.reversed().fold(Parser.pure("") as Parser<Char, Nothing, String>) { acc, c ->
        char(c).followedBy(acc)
    }.followedBy(Parser.pure(str))

fun <E> Parser<Char, E, List<Char>>.concatString(): Parser<Char, E, String> = Parser(ParserF.ConcatString(parserF))

// TODO Some parts of error printing should be moved to generic
// Showing errors produced by Char parsers
// Render errors
internal fun ErrorItem<Char>.show(): String = when (this) {
    is ErrorItem.Tokens -> {
        val tok = prettyChar(head)?.let { "<$it>" } ?: "$head"
        if (tail.isEmpty()) "'$tok'"
        else {
            val xs = tail.joinToString("") { prettyChar(it)?.let { "<$it>" } ?: "$it" }
            "\"$tok$xs\""
        }
    }
    is ErrorItem.Label -> label
    ErrorItem.EndOfInput -> "end of input"
}

interface ShowErrorComponent {
    fun errorLength(): Int
    fun showPretty(): String
}

fun <E : ShowErrorComponent> ParseError<Char, E>.showPretty(
    input: String,
    tabSize: Int = 4
): String = showPretty(input.toCharArray(), tabSize)

fun <E : ShowErrorComponent> ParseError<Char, E>.showPretty(
    input: CharArray,
    tabSize: Int = 4
): String {
    // reach offset
    var lineNr = 1
    var columnNr = 1
    var curr = 0
    var lastLineOff = 0
    for (c in input) {
        if (curr == getErrorOffset()) break
        when (c) {
            '\n' -> {
                lineNr++
                columnNr = 1
                lastLineOff = curr + 1
            }
            '\t' -> {
                columnNr += tabSize
            }
            else -> {
                columnNr++
            }
        }
        curr++
    }

    val padding = "$lineNr".length + 1
    val rPadding = columnNr - 1
    val eLen = when (this) {
        is ParseError.Trivial -> when (val it = unexpected) {
            null -> 1
            is ErrorItem.EndOfInput -> 1
            is ErrorItem.Label -> 1
            is ErrorItem.Tokens -> 1 + it.tail.size
        }
        is ParseError.Fancy -> errors.fold(1) { acc, err ->
            val l = when (err) {
                is FancyError.ErrorCustom -> err.err.errorLength()
                else -> 1
            }
            max(acc, l)
        }
    }
    // TODO reenable char pretty printing
    val line = input
        .drop(lastLineOff)
        .take(getErrorOffset() + 1 - lastLineOff)
        .joinToString("") /* { prettyChar(it)?.let { "<$it>" } ?: "$it" } */ +
            input.drop(getErrorOffset() + 1)
                .takeWhile { it != '\n' }
                .joinToString("") /* { prettyChar(it)?.let { "<$it>" } ?: "$it" } */

    fun StringBuilder.repeat(n: Int, c: Char): StringBuilder =
        if (n == 0) this
        else {
            for (i in 1..n) append(c)
            this
        }

    val builder = StringBuilder()
        .append(lineNr).append(":").append(columnNr).append(":").appendLine()
        .repeat(padding, ' ').append("| ").appendLine()
        .append(lineNr).append(" ").append("| ").append(line).appendLine()
        .repeat(padding, ' ').append("| ").repeat(rPadding, ' ').repeat(eLen, '^').appendLine()

    when (this) {
        is ParseError.Fancy -> {
            if (errors.isEmpty()) builder.append("unknown fancy parse error").appendLine()
            else {
                errors.joinToString("\n") {
                    when (it) {
                        is FancyError.Message -> it.msg
                        is FancyError.ErrorCustom -> it.err.showPretty()
                    }
                }
            }
        }
        is ParseError.Trivial -> {
            if (unexpected != null || expected.isNotEmpty()) {
                if (unexpected != null)
                    builder.append("unexpected: ").append(unexpected.show()).appendLine()
                if (expected.isNotEmpty())
                    builder.append("expected: ")
                        .append(expected.toList().joinToString("") { it.show() + " or " }.dropLast(4))
            } else {
                builder.append("unknown parse error").appendLine()
            }
        }
    }
    return builder.toString()
}

internal fun prettyChar(c: Char): String? = when (c) {
    ' ' -> "space"
    '\u0000' -> "null"
    '\u0001' -> "start of heading"
    '\u0002' -> "start of text"
    '\u0003' -> "end of text"
    '\u0004' -> "end of transmission"
    '\u0005' -> "enquiry"
    '\u0006' -> "acknowledge"
    '\u0007' -> "bell"
    '\b' -> "backspace"
    '\t' -> "tab"
    '\n' -> "newline"
    '\u000B' -> "vertical tab"
    '\u000C' -> "form feed"
    '\r' -> "carriage return"
    '\u000E' -> "shift out"
    '\u000F' -> "shift in"
    '\u0010' -> "data link escape"
    '\u0011' -> "device control 1"
    '\u0012' -> "device control 2"
    '\u0013' -> "device control 3"
    '\u0014' -> "device control 4"
    '\u0015' -> "negative acknowledge"
    '\u0016' -> "synchronous idle"
    '\u0017' -> "end of transmission block"
    '\u0018' -> "cancel"
    '\u0019' -> "end of medium"
    '\u001A' -> "substitute"
    '\u001B' -> "escape"
    '\u001C' -> "file separator"
    '\u001D' -> "group separator"
    '\u001E' -> "record separator"
    '\u001F' -> "unit separator"
    '\u007F' -> "delete"
    '\u00A0' -> "non-breaking space"
    else -> null
}
