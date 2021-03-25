package parsley

import parsley.frontend.Alt
import parsley.frontend.Empty
import parsley.frontend.Many

fun <I, E, A> Parser<I, E, A>.alt(p: Parser<I, E, A>): Parser<I, E, A> = Parser(Alt(parserF, p.parserF))

fun Parser.Companion.empty(): Parser<Nothing, Nothing, Nothing> = Parser(Empty)

fun <I, E, A> Parser<I, E, A>.orElse(p: Parser<I, E, A>): Parser<I, E, A> = Parser(Alt(parserF, p.parserF))

fun <I, E, A> Parser<I, E, A>.many(): Parser<I, E, List<A>> = Parser(Many(parserF))

fun <I, E, A> Parser<I, E, A>.some(): Parser<I, E, Pair<A, List<A>>> = this.zip(this.many())

fun <I, E, A> Parser.Companion.choice(
    p1: Parser<I, E, A>,
    vararg rest: Parser<I, E, A>
): Parser<I, E, A> = rest.fold(p1) { acc, v ->
    acc.alt(v)
}

fun <I, E, A : Any> Parser<I, E, A>.orNull(): Parser<I, E, A?> = alt(Parser.pure(null))
