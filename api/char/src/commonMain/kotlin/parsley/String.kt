package parsley

import parsley.frontend.Many
import parsley.frontend.ChunkOf
import parsley.frontend.MatchOf
import parsley.frontend.Satisfy
import parsley.frontend.ToNative

fun Parser.Companion.char(c: Char): Parser<Char, Nothing, Char> = single(c)

fun Parser.Companion.string(str: String): Parser<Char, Nothing, String> =
    str.reversed().fold(Parser.pure(str).unsafe()) { acc, c ->
        Parser.single(c).followedBy(acc)
    }

fun <E> Parser<Char, E, Char>.many(): Parser<Char, E, String> =
    Parser(ToNative(Many(parserF)))

fun <E> Parser<Char, E, Any?>.chunkOf(): Parser<Char, E, String> =
    Parser(ToNative(ChunkOf(parserF)))

fun <E> Parser<Char, E, Any?>.stringOf(): Parser<Char, E, String> =
    chunkOf()

fun <E, A> Parser<Char, E, A>.matchOf(): Parser<Char, E, Pair<String, A>> =
    Parser(ToNative<Char, E, String>(MatchOf(parserF).unsafe()).unsafe())

fun Parser.Companion.satisfy(expected: Set<ErrorItem<Char>> = emptySet(), p: CharPredicate): Parser<Char, Nothing, Char> =
    Parser(Satisfy(p, expected))

fun Parser.Companion.anyChar(): Parser<Char, Nothing, Char> = satisfy { true }

fun Parser.Companion.anyCharBut(el: Char): Parser<Char, Nothing, Char> = satisfy { it != el }

fun <E, A> Parser<Char, E, Char>.map(f: CharFunc<A>): Parser<Char, E, A> = map<Char, E, Char, A>(f)

fun Parser.Companion.remaining(): Parser<Char, Nothing, String> = anyChar().many().chunkOf()

fun interface CharPredicate : Predicate<Char> {
    override fun invoke(i: Char): Boolean = invokeP(i)
    fun invokeP(c: Char): Boolean
}

fun interface CharFunc<out A> : Function1<Char, A> {
    override fun invoke(p1: Char): A = invokeP(p1)
    fun invokeP(p1: Char): A
}
