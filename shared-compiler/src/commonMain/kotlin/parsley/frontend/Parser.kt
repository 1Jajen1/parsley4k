package parsley.frontend

import parsley.Either
import parsley.ErrorItem
import parsley.ParseError
import parsley.Predicate

interface ParserF<out I, out E, out A> {
    fun small(): Boolean
}

abstract class Unary<I, E, A, out B>(
    val inner: ParserF<I, E, A>
) : ParserF<I, E, B> {
    override fun small(): Boolean = false
    abstract fun copy(inner: ParserF<I, E, A>): ParserF<I, E, B>
}

abstract class Binary<I, E, A, B, out C>(
    val first: ParserF<I, E, A>,
    val second: ParserF<I, E, B>
) : ParserF<I, E, C> {
    override fun small(): Boolean = false
    abstract fun copy(
        first: ParserF<I, E, A>,
        second: ParserF<I, E, B>
    ): ParserF<I, E, C>
}


// Applicative hierarchy
class Pure<out A>(val a: A) : ParserF<Nothing, Nothing, A> {
    override fun small(): Boolean = true
    override fun toString(): String = "Pure($a)"
}

class Ap<I, E, A, B>(pF: ParserF<I, E, (A) -> B>, pA: ParserF<I, E, A>) :
    Binary<I, E, (A) -> B, A, B>(pF, pA) {
    override fun small(): Boolean = first.small() && second.small()
    override fun copy(first: ParserF<I, E, (A) -> B>, second: ParserF<I, E, A>): ParserF<I, E, B> =
        Ap(first, second)

    override fun toString(): String = "($first <*> $second)"
}

class ApL<I, E, A, B>(pA: ParserF<I, E, A>, pB: ParserF<I, E, B>) :
    Binary<I, E, A, B, A>(pA, pB) {
    override fun small(): Boolean = first.small() && second.small()
    override fun copy(first: ParserF<I, E, A>, second: ParserF<I, E, B>): ParserF<I, E, A> =
        ApL(first, second)

    override fun toString(): String = "($first <* $second)"
}

class ApR<I, E, A, B>(pA: ParserF<I, E, A>, pB: ParserF<I, E, B>) :
    Binary<I, E, A, B, B>(pA, pB) {
    override fun small(): Boolean = first.small() && second.small()
    override fun copy(first: ParserF<I, E, A>, second: ParserF<I, E, B>): ParserF<I, E, B> =
        ApR(first, second)
    override fun toString(): String = "($first *> $second)"
}

// Selective
class Select<I, E, A, B>(
    pEither: ParserF<I, E, Either<A, B>>,
    pIfLeft: ParserF<I, E, (A) -> B>
) : Binary<I, E, Either<A, B>, (A) -> B, B>(pEither, pIfLeft) {
    override fun copy(first: ParserF<I, E, Either<A, B>>, second: ParserF<I, E, (A) -> B>): ParserF<I, E, B> =
        Select(first, second)

    override fun toString(): String = "($first <?* $second)"
}

// Matchers
class Satisfy<I>(
    val match: Predicate<I>,
    val expected: Set<ErrorItem<I>> = emptySet(),
    val accepts: Set<I> = emptySet(),
    val rejects: Set<I> = emptySet()
) : ParserF<I, Nothing, I> {
    override fun small(): Boolean = true
    override fun toString(): String = "Satisfy"
}

class Single<I>(val i: I, val expected: Set<ErrorItem<I>> = emptySet()) : ParserF<I, Nothing, I> {
    override fun small(): Boolean = true
    override fun toString(): String = "Single($i)"
}

// Alternative
object Empty : ParserF<Nothing, Nothing, Nothing> {
    override fun small(): Boolean = true
    override fun toString(): String = "Empty"
}

class Alt<I, E, A>(left: ParserF<I, E, A>, right: ParserF<I, E, A>) :
    Binary<I, E, A, A, A>(left, right) {
    override fun copy(first: ParserF<I, E, A>, second: ParserF<I, E, A>): ParserF<I, E, A> =
        Alt(first, second)

    override fun toString(): String = "($first <|> $second)"
}

// LookAhead
class LookAhead<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, A>(p) {
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, A> =
        LookAhead(inner)

    override fun toString(): String = "LookAhead($inner)"
}

class NegLookAhead<I, E>(p: ParserF<I, E, Any?>) : Unary<I, E, Any?, Unit>(p) {
    override fun copy(inner: ParserF<I, E, Any?>): ParserF<I, E, Unit> =
        NegLookAhead(inner)

    override fun toString(): String = "NegLookAhead($inner)"
}

// Attempt
class Attempt<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, A>(p) {
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, A> =
        Attempt(inner)

    override fun toString(): String = "Attempt($inner)"
}

// Recursion
class Lazy<out I, out E, out A>(f: () -> ParserF<I, E, A>) : ParserF<I, E, A> {
    val p by lazy(f)
    override fun small(): Boolean = false
    override fun toString(): String = "Lazy(...)"
}

class Let(val recursive: Boolean, val sub: Int) : ParserF<Nothing, Nothing, Nothing> {
    override fun small(): Boolean = true
    override fun toString(): String = "Let($recursive, $sub)"
}

// Intrinsics for better performance
class Many<I, E, A>(val p: ParserF<I, E, A>) : Unary<I, E, A, List<A>>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, List<A>> =
        Many(inner)

    override fun toString(): String = "Many($p)"
}

class ChunkOf<I, E>(p: ParserF<I, E, Any?>) : Unary<I, E, Any?, List<I>>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, Any?>): ParserF<I, E, List<I>> =
        ChunkOf(inner)

    override fun toString(): String = "ChunkOf($inner)"
}

class MatchOf<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, Pair<List<I>, A>>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, Pair<List<I>, A>> =
        MatchOf(inner)

    override fun toString(): String = "MatchOf($inner)"
}

object Eof : ParserF<Nothing, Nothing, Nothing> {
    override fun small(): Boolean = true
    override fun toString(): String = "Eof"
}

// Failure
class Label<I, E, A>(val label: String?, p: ParserF<I, E, A>) : Unary<I, E, A, A>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, A> =
        Label(label, inner)

    override fun toString(): String = "Label($label, $inner)"
}

class Catch<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, Either<ParseError<I, E>, A>>(p) {
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, Either<ParseError<I, E>, A>> =
        Catch(inner)

    override fun toString(): String = "Catch($inner)"
}

// Fail is just like Empty with an error however it guarantees a stable offset position and is not aggressively optimized
class Fail<I, E>(val err: ParseError<I, E>) : ParserF<I, E, Nothing> {
    override fun small(): Boolean = true
    override fun toString(): String = "Fail"
}

object FailTop: ParserF<Nothing, Nothing, Nothing> {
    override fun small(): Boolean = true
    override fun toString(): String = "FailTop"
}
