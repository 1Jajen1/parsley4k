package parsley

import parsley.frontend.Many
import parsley.frontend.ChunkOf
import parsley.frontend.MatchOf
import parsley.frontend.Satisfy
import parsley.frontend.ToNative

fun Parser.Companion.byte(c: Byte): Parser<Byte, Nothing, Byte> = single(c)

fun Parser.Companion.char(c: Char): Parser<Byte, Nothing, Byte> = byte(c.toByte())

fun Parser.Companion.bytes(str: ByteArray): Parser<Byte, Nothing, ByteArray> =
    str.reversed().fold(Parser.pure(str).unsafe()) { acc, c ->
        Parser.single(c).followedBy(acc)
    }

fun Parser.Companion.string(str: String): Parser<Byte, Nothing, String> =
    bytes(str.encodeToByteArray()).map { it.decodeToString() }

fun <E> Parser<Byte, E, Byte>.many(): Parser<Byte, E, ByteArray> =
    Parser(ToNative(Many(parserF)))

fun <E> Parser<Byte, E, Any?>.chunkOf(): Parser<Byte, E, ByteArray> =
    Parser(ToNative(ChunkOf(parserF)))

fun <E> Parser<Byte, E, Any?>.bytesOf(): Parser<Byte, E, ByteArray> =
    chunkOf()

fun <E, A> Parser<Byte, E, A>.matchOf(): Parser<Byte, E, Pair<ByteArray, A>> =
    Parser(ToNative<Byte, E, ByteArray>(MatchOf(parserF).unsafe()).unsafe())

fun Parser.Companion.satisfy(expected: Set<ErrorItem<Byte>> = emptySet(), p: BytePredicate): Parser<Byte, Nothing, Byte> =
    Parser(Satisfy(p, expected))

fun Parser.Companion.anyByte(): Parser<Byte, Nothing, Byte> = satisfy { true }

fun Parser.Companion.anyByteBut(el: Byte): Parser<Byte, Nothing, Byte> = satisfy { it != el }

fun <E, A> Parser<Byte, E, Byte>.map(f: ByteFunc<A>): Parser<Byte, E, A> = map<Byte, E, Byte, A>(f)

fun Parser.Companion.remaining(): Parser<Byte, Nothing, ByteArray> = anyByte().many().chunkOf()

fun interface BytePredicate : Predicate<Byte> {
    override fun invoke(i: Byte): Boolean = invokeP(i)
    fun invokeP(c: Byte): Boolean
}

fun interface ByteFunc<out A> : Function1<Byte, A> {
    override fun invoke(p1: Byte): A = invokeP(p1)
    fun invokeP(p1: Byte): A
}
