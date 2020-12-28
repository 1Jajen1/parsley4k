package parsley

import arrow.Either
import parsley.internal.frontend.ParserF
import parsley.internal.frontend.show

// Higherkinds, useful for recursion schemes used during compilation.
// ^ This is never exposed to a user, maybe later I'll move this to a core module and add ways for others to extend this
internal class ForParser private constructor()

internal interface Kind<out F, out A>
internal typealias ParserOf<I, E> = Kind<Kind<ForParser, I>, E>

internal fun <I, E, A> Kind<ParserOf<I, E>, A>.fix(): Parser<I, E, A> = this as Parser<I, E, A>

class Parser<out I, out E, out A> internal constructor(internal val parserF: ParserF<I, E, ParserOf<I, E>, A>) :
    Kind<ParserOf<I, E>, A> {

    override fun toString(): String = show()

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is Parser<*, *, *>) return false
        return parserF == other.parserF
    }

    override fun hashCode(): Int = parserF.hashCode()

    companion object
}

// LookAhead
fun <I, E, A> Parser<I, E, A>.lookAhead(): Parser<I, E, A> = Parser(ParserF.LookAhead(this))

fun <I, E, A> Parser<I, E, A>.negLookAhead(): Parser<I, E, Unit> = Parser(ParserF.NegLookAhead(this))

// Attempt
fun <I, E, A> Parser<I, E, A>.attempt(): Parser<I, E, A> = Parser(ParserF.Attempt(this))

// Recursion
fun <I, E, A> Parser.Companion.recursive(f: () -> Parser<I, E, A>): Parser<I, E, A> = Parser(ParserF.Lazy(f))

// Input
fun <I> Parser.Companion.satisfy(expected: Set<ErrorItem<I>> = emptySet(), f: (I) -> Boolean): Parser<I, Nothing, I> =
    Parser(ParserF.Satisfy(f, expected))

fun <I> Parser.Companion.single(i: I): Parser<I, Nothing, I> = Parser(ParserF.Single(i))
