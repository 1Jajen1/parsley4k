package parsley

import parsley.frontend.CharListToString
import parsley.frontend.Many
import parsley.frontend.ChunkOf
import parsley.frontend.Satisfy

fun Parser.Companion.char(c: Char): Parser<Char, Nothing, Char> = single(c)

fun Parser.Companion.string(str: String): Parser<Char, Nothing, String> =
    str.reversed().fold(Parser.pure("").unsafe<Parser<Char, Nothing, String>>()) { acc, c ->
        Parser.single(c).followedBy(acc)
    }.followedBy(Parser.pure(str))

fun <E> Parser<Char, E, Char>.many(): Parser<Char, E, String> =
    Parser(CharListToString(Many(parserF)))

fun <E> Parser<Char, E, Any?>.chunkOf(): Parser<Char, E, String> =
    Parser(CharListToString(ChunkOf(parserF)))

fun <E> Parser<Char, E, Any?>.stringOf(): Parser<Char, E, String> =
    Parser(CharListToString(ChunkOf(parserF)))

fun Parser.Companion.satisfy(p: CharPredicate): Parser<Char, Nothing, Char> = Parser(Satisfy(p))

fun <E, A> Parser<Char, E, Char>.map(f: CharFunc<A>): Parser<Char, E, A> = map<Char, E, Char, A>(f)

fun interface CharPredicate : Predicate<Char> {
    override fun invoke(i: Char): Boolean = invokeP(i)
    fun invokeP(c: Char): Boolean
}

fun interface CharFunc<out A> : (Char) -> A {
    override fun invoke(p1: Char): A = invokeP(p1)
    fun invokeP(p1: Char): A
}
