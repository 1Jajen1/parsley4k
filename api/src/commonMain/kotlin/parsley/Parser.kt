package parsley

import parsley.frontend.Attempt
import parsley.frontend.Lazy
import parsley.frontend.LookAhead
import parsley.frontend.NegLookAhead
import parsley.frontend.ParserF
import parsley.frontend.ChunkOf
import parsley.frontend.Satisfy
import parsley.frontend.Single

class Parser<out I, out E, out A>(val parserF: ParserF<I, E, A>) {

    override fun toString(): String = parserF.toString()

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Parser<*, *, *>) return false
        return parserF == other.parserF
    }

    override fun hashCode(): Int = parserF.hashCode()

    companion object
}

// LookAhead
fun <I, E, A> Parser<I, E, A>.lookAhead(): Parser<I, E, A> = Parser(LookAhead(parserF))

fun <I, E, A> Parser<I, E, A>.negLookAhead(): Parser<I, E, Unit> = Parser(NegLookAhead(parserF))

// Attempt
fun <I, E, A> Parser<I, E, A>.attempt(): Parser<I, E, A> = Parser(Attempt(parserF))

// Recursion
fun <I, E, A> Parser.Companion.recursive(f: () -> Parser<I, E, A>): Parser<I, E, A> =
    Parser(Lazy { f().parserF })

// Input
fun <I> Parser.Companion.satisfy(f: (I) -> Boolean): Parser<I, Nothing, I> =
    Parser(Satisfy(f))

fun <I> Parser.Companion.single(i: I): Parser<I, Nothing, I> = Parser(Single(i))

inline fun <reified I> Parser.Companion.chunk(vararg els: I): Parser<I, Nothing, Array<I>> =
    els.reversed().fold(Parser.pure(emptyArray<I>()).unsafe<Parser<I, Nothing, Array<I>>>()) { acc, c ->
        Parser.single(c).followedBy(acc)
    }.followedBy(Parser.pure(els).unsafe())

fun <I, E> Parser<I, E, Any?>.chunkOf(): Parser<I, E, List<I>> = Parser(ChunkOf(parserF))
