package parsley.combinators

import arrow.NonEmptyList
import parsley.Parser
import parsley.internal.frontend.ParserF
import parsley.recursive

fun <I, E, A> Parser<I, E, A>.alt(p: Parser<I, E, A>): Parser<I, E, A> = Parser(ParserF.Alt(this, p))

fun Parser.Companion.empty(): Parser<Nothing, Nothing, Nothing> = Parser(ParserF.Empty())

fun <I, E, A> Parser<I, E, A>.orElse(p: Parser<I, E, A>): Parser<I, E, A> = Parser(ParserF.Alt(this, p))

// TODO explore optimization ideas.
fun <I, E, A> Parser<I, E, A>.many(): Parser<I, E, List<A>> {
    lateinit var p: Parser<I, E, IList<A>>
    p = this.map { a -> { xs: IList<A> -> IList.Cons(a, xs) } }
        .ap(Parser.recursive { p })
        .alt(Parser.pure(IList.Nil))
    return p.map { it.toList() }
}

sealed class IList<out A> {
    object Nil : IList<Nothing>()
    class Cons<out A>(val a: A, val tail: IList<A>): IList<A>()

    fun toList(): List<A> {
        val mut = mutableListOf<A>()
        var curr = this
        while (curr !== Nil) {
            val cons = curr as Cons
            mut.add(cons.a)
            curr = cons.tail
        }
        return mut
    }
}

fun <I, E, A> Parser<I, E, A>.some(): Parser<I, E, NonEmptyList<A>> =
    many().filterMap { xs -> if (xs.isEmpty()) null else NonEmptyList(xs.first(), xs.drop(1)) }

fun <I, E, A> Parser.Companion.choice(
    p1: Parser<I, E, A>,
    vararg reset: Parser<I, E, A>
): Parser<I, E, A> = reset.fold(p1) { acc, v ->
    acc.alt(v)
}

fun <I, E, A : Any> Parser<I, E, A>.orNull(): Parser<I, E, A?> = alt(Parser.pure(null))
