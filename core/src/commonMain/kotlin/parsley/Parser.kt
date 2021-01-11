package parsley

import parsley.internal.frontend.ParserF

class Parser<out I, out E, out A> internal constructor(internal val parserF: ParserF<I, E, A>) {

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
fun <I, E, A> Parser<I, E, A>.lookAhead(): Parser<I, E, A> = Parser(ParserF.LookAhead(parserF))

fun <I, E, A> Parser<I, E, A>.negLookAhead(): Parser<I, E, Unit> = Parser(ParserF.NegLookAhead(parserF))

// Attempt
fun <I, E, A> Parser<I, E, A>.attempt(): Parser<I, E, A> = Parser(ParserF.Attempt(parserF))

// Recursion
fun <I, E, A> Parser.Companion.recursive(f: () -> Parser<I, E, A>): Parser<I, E, A> =
    Parser(ParserF.Lazy { f().parserF })

// Input
fun <I> Parser.Companion.satisfy(expected: Set<ErrorItem<I>> = emptySet(), f: (I) -> Boolean): Parser<I, Nothing, I> =
    Parser(ParserF.Satisfy(f, expected))

fun <I> Parser.Companion.single(i: I): Parser<I, Nothing, I> = Parser(ParserF.Single(i))
