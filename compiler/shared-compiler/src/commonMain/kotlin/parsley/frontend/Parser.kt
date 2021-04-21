package parsley.frontend

import parsley.Either
import parsley.ErrorItem
import parsley.ParseError
import parsley.Predicate
import pretty.Doc
import pretty.align
import pretty.doc
import pretty.encloseSep
import pretty.fillBreak
import pretty.flatAlt
import pretty.group
import pretty.hCat
import pretty.line
import pretty.list
import pretty.nest
import pretty.plus
import pretty.pretty
import pretty.punctuate
import pretty.softLine
import pretty.spaced
import pretty.symbols.comma
import pretty.symbols.lBrace
import pretty.symbols.rBrace
import pretty.symbols.sQuotes
import pretty.symbols.space
import pretty.text

abstract class ParserF<out I, out E, out A> {
    abstract fun small(): Boolean
    abstract fun pprint(): Doc<Nothing>
    override fun toString(): String = pprint().pretty(maxWidth = 100, ribbonWidth = 0.5F)
}

abstract class Unary<I, E, A, out B>(
    val inner: ParserF<I, E, A>
) : ParserF<I, E, B>() {
    override fun small(): Boolean = false
    abstract fun copy(inner: ParserF<I, E, A>): ParserF<I, E, B>
}

abstract class Binary<I, E, A, B, out C>(
    val first: ParserF<I, E, A>,
    val second: ParserF<I, E, B>
) : ParserF<I, E, C>() {
    override fun small(): Boolean = false
    abstract fun copy(
        first: ParserF<I, E, A>,
        second: ParserF<I, E, B>
    ): ParserF<I, E, C>
}

fun <I, E, A, B> infix(left: ParserF<I, E, A>, right: ParserF<I, E, B>, op: String): Doc<Nothing> =
    (left.pprint() line op.text() spaced right.pprint().nest(2)).align()

fun <I, E, A> cons(inner: ParserF<I, E, A>, cons: String): Doc<Nothing> =
    (cons.text() line inner.pprint()).nest(2).group().align()


// Applicative hierarchy
class Pure<out A>(val a: A) : ParserF<Nothing, Nothing, A>() {
    override fun small(): Boolean = true
    override fun pprint(): Doc<Nothing> =
        ("Pure".text() softLine a.toString().doc())
            .group()
            .nest(2)
}

class Ap<I, E, A, B>(pF: ParserF<I, E, (A) -> B>, pA: ParserF<I, E, A>) :
    Binary<I, E, (A) -> B, A, B>(pF, pA) {
    override fun small(): Boolean = first.small() && second.small()
    override fun copy(first: ParserF<I, E, (A) -> B>, second: ParserF<I, E, A>): ParserF<I, E, B> =
        Ap(first, second)

    override fun pprint(): Doc<Nothing> = infix(first, second, "<*>")
}

class ApL<I, E, A, B>(pA: ParserF<I, E, A>, pB: ParserF<I, E, B>) :
    Binary<I, E, A, B, A>(pA, pB) {
    override fun small(): Boolean = first.small() && second.small()
    override fun copy(first: ParserF<I, E, A>, second: ParserF<I, E, B>): ParserF<I, E, A> =
        ApL(first, second)

    override fun pprint(): Doc<Nothing> = infix(first, second, "<*")
}

class ApR<I, E, A, B>(pA: ParserF<I, E, A>, pB: ParserF<I, E, B>) :
    Binary<I, E, A, B, B>(pA, pB) {
    override fun small(): Boolean = first.small() && second.small()
    override fun copy(first: ParserF<I, E, A>, second: ParserF<I, E, B>): ParserF<I, E, B> =
        ApR(first, second)

    override fun pprint(): Doc<Nothing> = infix(first, second, "*>")
}

// Selective
class Select<I, E, A, B>(
    pEither: ParserF<I, E, Either<A, B>>,
    pIfLeft: ParserF<I, E, (A) -> B>
) : Binary<I, E, Either<A, B>, (A) -> B, B>(pEither, pIfLeft) {
    override fun copy(first: ParserF<I, E, Either<A, B>>, second: ParserF<I, E, (A) -> B>): ParserF<I, E, B> =
        Select(first, second)

    override fun pprint(): Doc<Nothing> = infix(first, second, "<?*")
}

// Matchers
class Satisfy<I>(
    val match: Predicate<I>,
    val expected: Set<ErrorItem<I>> = emptySet(),
    val accepts: Set<I> = emptySet(),
    val rejects: Set<I> = emptySet()
) : ParserF<I, Nothing, I>() {
    override fun small(): Boolean = true
    override fun pprint(): Doc<Nothing> = "Satisfy".text()
}

class Single<I>(val i: I, val expected: Set<ErrorItem<I>> = emptySet()) : ParserF<I, Nothing, I>() {
    override fun small(): Boolean = true
    override fun pprint(): Doc<Nothing> = "Single".text() spaced i.toString().doc().sQuotes()
}

// Alternative
object Empty : ParserF<Nothing, Nothing, Nothing>() {
    override fun small(): Boolean = true
    override fun pprint(): Doc<Nothing> = "Empty".text()
}

class Alt<I, E, A>(left: ParserF<I, E, A>, right: ParserF<I, E, A>) :
    Binary<I, E, A, A, A>(left, right) {
    override fun copy(first: ParserF<I, E, A>, second: ParserF<I, E, A>): ParserF<I, E, A> =
        Alt(first, second)

    override fun pprint(): Doc<Nothing> =
        (first.pprint() line "<|>".text() spaced second.pprint())
}

// LookAhead
class LookAhead<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, A>(p) {
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, A> =
        LookAhead(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "LookAhead")
}

class NegLookAhead<I, E>(p: ParserF<I, E, Any?>) : Unary<I, E, Any?, Unit>(p) {
    override fun copy(inner: ParserF<I, E, Any?>): ParserF<I, E, Unit> =
        NegLookAhead(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "NegLookAhead")
}

// Attempt
class Attempt<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, A>(p) {
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, A> =
        Attempt(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "Attempt")
}

// Recursion
class Lazy<out I, out E, out A>(f: () -> ParserF<I, E, A>) : ParserF<I, E, A>() {
    val p by lazy(f)
    override fun small(): Boolean = false
    override fun pprint(): Doc<Nothing> = "Lazy (...)".text()
}

class Let(val recursive: Boolean, val sub: Int) : ParserF<Nothing, Nothing, Nothing>() {
    override fun small(): Boolean = true
    override fun pprint(): Doc<Nothing> =
        "Let".text() spaced listOf("recursive" to recursive, "label" to sub).map { (k, v) ->
            k.text().fillBreak(9).flatAlt(k.text()) spaced "=".text() spaced v.toString().doc()
        }.encloseSep(
            lBrace() + space(),
            line() + rBrace(),
            comma() + space()
        ).group().align()
}

// Intrinsics for better performance
class Many<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, List<A>>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, List<A>> =
        Many(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "Many")
}

class ChunkOf<I, E>(p: ParserF<I, E, Any?>) : Unary<I, E, Any?, List<I>>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, Any?>): ParserF<I, E, List<I>> =
        ChunkOf(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "ChunkOf")
}

class MatchOf<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, Pair<List<I>, A>>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, Pair<List<I>, A>> =
        MatchOf(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "MatchOf")
}

class ToNative<I, E, Nat>(p: ParserF<I, E, List<I>>) : Unary<I, E, List<I>, Nat>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, List<I>>): ParserF<I, E, Nat> =
        ToNative(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "ToNative")
}

object Eof : ParserF<Nothing, Nothing, Nothing>() {
    override fun small(): Boolean = true
    override fun pprint(): Doc<Nothing> = "Eof".text()
}

// Failure
class Label<I, E, A>(val label: String?, p: ParserF<I, E, A>) : Unary<I, E, A, A>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, A> =
        Label(label, inner)

    override fun pprint(): Doc<Nothing> = when {
        label == null || label.isEmpty() -> cons(inner, "Hide")
        else -> (label.doc() spaced "?".text() softLine inner.pprint()).group().align()
    }
}

class Catch<I, E, A>(p: ParserF<I, E, A>) : Unary<I, E, A, Either<ParseError<I, E>, A>>(p) {
    override fun copy(inner: ParserF<I, E, A>): ParserF<I, E, Either<ParseError<I, E>, A>> =
        Catch(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "Catch")
}

// Fail is just like Empty with an error however it guarantees a stable offset position and is not aggressively optimized
class Fail<I, E>(val err: ParseError<I, E>) : ParserF<I, E, Nothing>() {
    override fun small(): Boolean = true

    override fun pprint(): Doc<Nothing> = "Fail".text()
}

object FailTop : ParserF<Nothing, Nothing, Nothing>() {
    override fun small(): Boolean = true

    override fun pprint(): Doc<Nothing> = "FailTop".text()
}
