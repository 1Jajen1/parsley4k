package parsley

import pretty.Doc
import pretty.PageWidth
import pretty.doc
import pretty.encloseSep
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
import pretty.vCat
import kotlin.math.max

class ByteTokensT(var head: Byte, var all: ByteArray): ErrorItemT<Byte> {
    override fun toFinal(): ErrorItem<Byte> =
        if (all.isNotEmpty()) ErrorItem.Tokens(all[0], all.drop(1))
        else ErrorItem.Tokens(head, emptyList())

    override fun length(): Int = max(1, all.size)
}

fun <E> ParseError<Byte, E>.prettyDoc(
    input: ByteArray,
    fromE: (E) -> Doc<Nothing>,
    sizeE: (E) -> Int
): Doc<Nothing> {
    fun ByteArray.back(off: Int): Triple<Int, Int, Int> {
        var sz = 0
        var lNr = 1
        var l = 0
        var curr = 0
        while (curr < off) {
            when (this[curr++]) {
                '\n'.toByte() -> {
                    l = curr
                    sz = 0
                    lNr++
                }
                '\t'.toByte() -> sz += 4
                else -> sz++
            }
        }
        return Triple(lNr, l, sz)
    }
    fun ByteArray.forward(off: Int): Int {
        var curr = off - 1
        while (curr < size - 1) {
            when (this[++curr]) {
                '\n'.toByte() -> return curr
            }
        }
        return curr + 1
    }
    val (b, a) = input.back(offset) to input.forward(offset)
    val (lNr, bI, bSz) = b
    val line = input.copyOfRange(bI, a)

    val lnRSz = "$lNr".length
    val padding = lnRSz + 1
    val indicatorOff = bSz + 1

    val pos = lNr.doc() + colon() + bSz.doc() + colon()
    val indicatorSz = unexpected?.size() ?: errors.fold(1) { acc, err ->
        when (err) {
            is FancyError.Message -> acc
            is FancyError.Custom -> max(acc, sizeE(err.error))
        }
    }
    val lineD = spaces(padding).text() + pipe() + hardLine() +
            lNr.doc() spaced pipe() spaced line.decodeToString().text() + hardLine() +
            spaces(padding).text() + pipe() + spaces(indicatorOff).text() + indicator(indicatorSz).text()

    val topD = if (unexpected != null || expected.isNotEmpty()) {
        var res = nil()
        unexpected?.pretty()?.also { res = res + hardLine() + "unexpected:".text() spaced it }
        val exp = expected.map { it.pretty() }
            .punctuate(",".text()).fillSep()
        if (expected.isNotEmpty()) res = res + hardLine() + "expected:".text() spaced exp
        pos + hardLine() + lineD + res
    } else lineD
    val errorD = errors.map { e ->
        when (e) {
            is FancyError.Message -> e.msg.doc()
            is FancyError.Custom -> fromE(e.error)
        }
    }.encloseSep(nil(), nil(), hardLine())
    return topD + hardLine() + errorD
}

private fun ErrorItem<Byte>.size(): Int = when (this) {
    is ErrorItem.Tokens -> 1 + tail.size
    else -> 1
}

private fun ErrorItem<Byte>.pretty(): Doc<Nothing> = when (this) {
    is ErrorItem.Tokens -> dQuote() + byteArrayOf(head, *tail.toByteArray()).decodeToString().text() + dQuote()
    is ErrorItem.Label -> label.text()
    ErrorItem.EndOfInput -> "End of input".text()
}

private fun indicator(i: Int): String = generateSequence { "^" }.take(i).joinToString("")
private fun spaces(i: Int): String = generateSequence { " " }.take(i).joinToString("")

fun ParseError<Byte, Nothing>.prettyDoc(
    input: ByteArray
): Doc<Nothing> = prettyDoc(input, { nil() }, { 1 })

inline fun <E> ParseError<Byte, E>.pretty(
    input: ByteArray,
    crossinline fromE: (E) -> String = { it.toString() }
): String =
    prettyDoc(input, { fromE(it).doc() }, { 1 }).pretty(80, 1F)

fun ParseError<Byte, Nothing>.pretty(
    input: ByteArray
): String = pretty(input) { "" }

