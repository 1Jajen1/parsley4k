package parsley

import parsley.internal.frontend.CharPredicate
import parsley.internal.frontend.ParserF
import parsley.internal.frontend.Predicate
import kotlin.jvm.JvmName

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
fun <I> Parser.Companion.satisfy(expected: Set<ErrorItem<I>> = emptySet(), f: Predicate<I>): Parser<I, Nothing, I> =
    Parser(ParserF.Satisfy(f, expected))

@JvmName("satisfyChar")
fun Parser.Companion.satisfy(expected: Set<ErrorItem<Char>> = emptySet(), f: CharPredicate): Parser<Char, Nothing, Char> =
    Parser(ParserF.Satisfy(f, expected))

fun <I> Parser.Companion.single(i: I): Parser<I, Nothing, I> = Parser(ParserF.Single(i))

/**
 * TODO List:
 * - Split of string compilation from code gen and only replace instructions after the fact
 *  - Investigate if that causes any problems with optimization...
 * - Add Byte parser
 * - Split into modules!
 * - Benchmark Generic vs Byte vs Char
 * - Optimise compilation pipeline:
 *  - Benchmark compilation, profile and improve it.
 *  - But first I probably need to restructure it a bit
 * - Further optimise generated instructions
 *  - Benchmark different simple parsers
 *  - Setup ci and benchmark comparisons
 * - Provide a nicer api
 *  - Investigate dsl like betterParser although I do believe that is over the top
 *  - recursive could maybe be just lazy
 * - Error messages
 *  - AFTER benchmark pipeline is set up to spot regressions early
 * - Streaming parsing
 *  - Investigate what needs to be done...
 */
