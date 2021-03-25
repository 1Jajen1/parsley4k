package parsley

import parsley.frontend.Ap
import parsley.frontend.ApL
import parsley.frontend.ApR
import parsley.frontend.Pure

// Core
fun <I, E, A, B> Parser<I, E, (A) -> B>.ap(pA: Parser<I, E, A>): Parser<I, E, B> =
    Parser(Ap(parserF, pA.parserF))

fun <A> Parser.Companion.pure(a: A): Parser<Nothing, Nothing, A> = Parser(Pure(a))

// Combinators
fun <I, E, A, B> Parser<I, E, A>.followedBy(p: Parser<I, E, B>): Parser<I, E, B> =
    Parser(ApR(parserF, p.parserF))

fun <I, E, A, B> Parser<I, E, A>.followedByDiscard(p: Parser<I, E, B>): Parser<I, E, A> =
    Parser(ApL(parserF, p.parserF))

inline fun <I, E, A, B> Parser<I, E, A>.zip(p: Parser<I, E, B>): Parser<I, E, Pair<A, B>> =
    map { a -> { b: B -> a to b } }.ap(p)

inline fun <I, E, A, B, C> Parser<I, E, A>.zip(p: Parser<I, E, B>, crossinline f: (A, B) -> C): Parser<I, E, C> =
    map { a -> { b: B -> f(a, b) } }.ap(p)
