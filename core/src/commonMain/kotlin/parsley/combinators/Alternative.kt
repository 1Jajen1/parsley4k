package parsley.combinators

import arrow.NonEmptyList
import parsley.Parser
import parsley.internal.frontend.ParserF
import parsley.recursive

fun <I, E, A> Parser<I, E, A>.alt(p: Parser<I, E, A>): Parser<I, E, A> = Parser(ParserF.Alt(parserF, p.parserF))

fun Parser.Companion.empty(): Parser<Nothing, Nothing, Nothing> = Parser(ParserF.Empty)

fun <I, E, A> Parser<I, E, A>.orElse(p: Parser<I, E, A>): Parser<I, E, A> = Parser(ParserF.Alt(parserF, p.parserF))

fun <I, E, A> Parser<I, E, A>.many(): Parser<I, E, List<A>> = Parser(ParserF.Many(parserF))

fun <I, E, A> Parser<I, E, A>.some(): Parser<I, E, NonEmptyList<A>> =
    mapTo(many()) { head, xs -> NonEmptyList(head, xs) }

fun <I, E, A> Parser.Companion.choice(
    p1: Parser<I, E, A>,
    vararg reset: Parser<I, E, A>
): Parser<I, E, A> = reset.fold(p1) { acc, v ->
    acc.alt(v)
}

fun <I, E, A : Any> Parser<I, E, A>.orNull(): Parser<I, E, A?> = alt(Parser.pure(null))
