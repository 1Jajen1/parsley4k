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

fun <I, E, A, B> Parser<I, E, A>.zip(p: Parser<I, E, B>): Parser<I, E, Pair<A, B>> =
    map { a -> { b: B -> a to b } }.ap(p)

inline fun <I, E, A, B, C> Parser<I, E, A>.zip(p: Parser<I, E, B>, crossinline f: (A, B) -> C): Parser<I, E, C> =
    map { a -> { b: B -> f(a, b) } }.ap(p)

fun <I, E, A> Parser<I, E, A>.repeat(n: Int): Parser<I, E, List<A>> =
    repeatI(n).map { it?.toList() ?: emptyList() }

fun <I, E> Parser<I, E, Any?>.skip(n: Int): Parser<I, E, Unit> =
    repeat(n).constant(Unit)

private fun <I, E, A> Parser<I, E, A>.repeatI(n: Int): Parser<I, E, LinkedList<A>?> = when {
    n < 0 -> throw IllegalArgumentException("Parser.repeat: Only positive numbers > 0 are allowed")
    n > 0 -> this.zip(repeatI(n - 1)) { a, tail -> LinkedList(a, tail) }
    else -> Parser.pure(null)
}

private class LinkedList<out A>(val head: A, val tail: LinkedList<A>?) {
    fun toList(): List<A> {
        val buf = mutableListOf<A>()
        var curr = this
        while (true) {
            buf.add(head)
            curr = curr.tail ?: break
        }
        return buf
    }
}

