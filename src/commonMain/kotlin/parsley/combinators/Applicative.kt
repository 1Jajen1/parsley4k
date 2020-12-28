package parsley.combinators

import parsley.Parser
import parsley.internal.frontend.ParserF

// Core
fun <I, E, A, B> Parser<I, E, (A) -> B>.ap(pA: Parser<I, E, A>): Parser<I, E, B> = Parser(ParserF.Ap(this, pA))

fun <A> Parser.Companion.pure(a: A): Parser<Nothing, Nothing, A> = Parser(ParserF.Pure(a))

// Combinators
fun <I, E, A, B> Parser<I, E, A>.followedBy(p: Parser<I, E, B>): Parser<I, E, B> = Parser(ParserF.ApR(this, p))

fun <I, E, A, B> Parser<I, E, A>.followedByDiscard(p: Parser<I, E, B>): Parser<I, E, A> = Parser(ParserF.ApL(this, p))

fun <I, E, A, B, C> Parser<I, E, A>.mapTo(p: Parser<I, E, B>, f: (A, B) -> C): Parser<I, E, C> =
    map { a -> { b: B -> f(a, b) } }.ap(p)
