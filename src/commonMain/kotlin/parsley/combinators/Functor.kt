package parsley.combinators

import parsley.Parser

fun <I, E, A, B> Parser<I, E, A>.map(f: (A) -> B): Parser<I, E, B> = Parser.pure(f).ap(this)

fun <I, E, A> Parser<I, E, A>.void(): Parser<I, E, Unit> = constant(Unit)

// Using followedBy instead of map { b } allows for more optimization opportunities
fun <I, E, A, B> Parser<I, E, A>.constant(b: B): Parser<I, E, B> = followedBy(Parser.pure(b))
