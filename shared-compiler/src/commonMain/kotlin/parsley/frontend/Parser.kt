package parsley.frontend

import parsley.Either
import parsley.ErrorItem
import parsley.ParseError
import parsley.Predicate

interface ParserF<out I, out E, out A>

// Applicative hierarchy
class Pure<out A>(val a: A) : ParserF<Nothing, Nothing, A> {
    override fun toString(): String = "Pure($a)"
}

class Ap<out I, out E, A, out B>(val pF: ParserF<I, E, (A) -> B>, val pA: ParserF<I, E, A>) :
    ParserF<I, E, B> {
    override fun toString(): String = "($pF <*> $pA)"
}

class ApL<out I, out E, out A, out B>(val pA: ParserF<I, E, A>, val pB: ParserF<I, E, B>) :
    ParserF<I, E, A> {
    override fun toString(): String = "($pA <* $pB)"
}

class ApR<out I, out E, out A, out B>(val pA: ParserF<I, E, A>, val pB: ParserF<I, E, B>) :
    ParserF<I, E, B> {
    override fun toString(): String = "($pA *> $pB)"
}

// Selective
class Select<out I, out E, A, out B>(
    val pEither: ParserF<I, E, Either<A, B>>,
    val pIfLeft: ParserF<I, E, (A) -> B>
) : ParserF<I, E, B> {
    override fun toString(): String = "($pEither <?* $pIfLeft)"
}

// Matchers
class Satisfy<I>(
    val match: Predicate<I>,
    val expected: Set<ErrorItem<I>> = emptySet(),
    val accepts: Set<I> = emptySet(),
    val rejects: Set<I> = emptySet()
) : ParserF<I, Nothing, I> {
    override fun toString(): String = "Satisfy"
}

class Single<I>(val i: I, val expected: Set<ErrorItem<I>> = emptySet()) : ParserF<I, Nothing, I> {
    override fun toString(): String = "Single($i)"
}

// Alternative
object Empty : ParserF<Nothing, Nothing, Nothing> {
    override fun toString(): String = "Empty"
}

class Alt<out I, out E, out A>(val left: ParserF<I, E, A>, val right: ParserF<I, E, A>) :
    ParserF<I, E, A> {
    override fun toString(): String = "($left <|> $right)"
}

// LookAhead
class LookAhead<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, A> {
    override fun toString(): String = "LookAhead($p)"
}

class NegLookAhead<out I, out E>(val p: ParserF<I, E, Any?>) : ParserF<I, E, Unit> {
    override fun toString(): String = "NegLookAhead($p)"
}

// Attempt
class Attempt<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, A> {
    override fun toString(): String = "Attempt($p)"
}

// Recursion
class Lazy<out I, out E, out A>(val f: () -> ParserF<I, E, A>) : ParserF<I, E, A> {
    override fun toString(): String = "Lazy(...)"
}

class Let(val recursive: Boolean, val sub: Int) : ParserF<Nothing, Nothing, Nothing> {
    override fun toString(): String = "Let($recursive, $sub)"
}

// Intrinsics for better performance
class Many<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, List<A>> {
    override fun toString(): String = "Many($p)"
}

class ChunkOf<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, List<I>> {
    override fun toString(): String = "ChunkOf($p)"
}

// Failure
class Label<out I, out E, out A>(val label: String, val p: ParserF<I, E, A>) : ParserF<I, E, A> {
    override fun toString(): String = "Label($label, $p)"
}

class Hide<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, A> {
    override fun toString(): String = "Hide($p)"
}
