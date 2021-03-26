package parsley

import pretty.Doc
import pretty.PageWidth
import pretty.doc
import pretty.fillSep
import pretty.hardLine
import pretty.nil
import pretty.plus
import pretty.pretty
import pretty.punctuate
import pretty.spaced
import pretty.symbols.b9quote
import pretty.symbols.colon
import pretty.symbols.dQuote
import pretty.symbols.pipe
import pretty.text

class CharTokensT(var head: Char, val all: CharArray): ErrorItemT<Char> {
    override fun toFinal(): ErrorItem<Char> =
        ErrorItem.Tokens(head, all.drop(1))
}

fun <E> ParseError<Char, E>.prettyDoc(
    input: CharArray,
    fromE: (E) -> Doc<Nothing>
): Doc<Nothing> {
    fun CharArray.back(off: Int): Triple<Int, Int, Int> {
        var sz = 0
        var lNr = 1
        var l = 0
        var curr = 0
        while (curr < off) {
            when (this[curr++]) {
                '\n' -> {
                    l = curr
                    sz = 0
                    lNr++
                }
                '\t' -> sz += 4
                else -> sz++
            }
        }
        return Triple(lNr, l, sz)
    }
    fun CharArray.forward(off: Int): Int {
        var curr = off - 1
        while (curr < size - 1) {
            when (this[++curr]) {
                '\n' -> return curr
            }
        }
        return curr + 1
    }
    val (b, a) = input.back(offset) to input.forward(offset)
    val (lNr, bI, bSz) = b
    val line = input.slice(bI, a)

    val lnRSz = "$lNr".length
    val padding = lnRSz + 1
    val indicatorOff = bSz + 1

    val pos = lNr.doc() + colon() + bSz.doc() + colon()
    val indicatorSz = when (this) {
        is ParseError.Trivial -> unexpected?.size() ?: 1
        is ParseError.Fancy -> 1 // TODO
    }
    val lineD = spaces(padding).text() + pipe() + hardLine() +
            lNr.doc() spaced pipe() spaced line.concatToString().text() + hardLine() +
            spaces(padding).text() + pipe() + spaces(indicatorOff).text() + indicator(indicatorSz).text()

    val finalD = when (this) {
        is ParseError.Trivial -> {
            if (unexpected != null || expected.isNotEmpty()) {
                var res = nil()
                unexpected?.pretty()?.also { res = res + hardLine() + "unexpected:".text() spaced it }
                val exp = expected.map { it.pretty() }
                    .punctuate(",".text()).fillSep()
                if (expected.isNotEmpty()) res = res + hardLine() + "expected:".text() spaced exp
                pos + hardLine() + lineD + res
            } else lineD
        }
        is ParseError.Fancy -> TODO("")
    }
    return finalD
}

private fun ErrorItem<Char>.size(): Int = when (this) {
    is ErrorItem.Tokens -> 1 + tail.size
    else -> 1
}

private fun ErrorItem<Char>.pretty(): Doc<Nothing> = when (this) {
    is ErrorItem.Tokens -> dQuote() + "$head${tail.joinToString("")}".text() + dQuote()
    is ErrorItem.Label -> label.text()
    ErrorItem.EndOfInput -> "End of input".text()
}

private fun indicator(i: Int): String = generateSequence { "^" }.take(i).joinToString("")
private fun spaces(i: Int): String = generateSequence { " " }.take(i).joinToString("")

fun ParseError<Char, Nothing>.prettyDoc(
    input: CharArray
): Doc<Nothing> = prettyDoc(input) { nil() }

inline fun <E> ParseError<Char, E>.pretty(
    input: CharArray,
    crossinline fromE: (E) -> String = { it.toString() }
): String =
    prettyDoc(input) { fromE(it).doc() }.pretty(80, 1F)

fun ParseError<Char, Nothing>.pretty(
    input: CharArray
): String = pretty(input) { "" }

