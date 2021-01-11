package parsley.internal.frontend

import arrow.Either
import parsley.ErrorItem

internal sealed class ParserF<out I, out E, out A> {
    // Applicative hierarchy
    data class Pure<out A>(val a: A) : ParserF<Nothing, Nothing, A>() {
        override fun toString(): String = "Pure($a)"
    }

    data class Ap<out I, out E, A, out B>(val pF: ParserF<I, E, (A) -> B>, val pA: ParserF<I, E, A>) :
        ParserF<I, E, B>() {
        override fun toString(): String = "($pF <*> $pA)"
    }

    data class ApL<out I, out E, out A, out B>(val pA: ParserF<I, E, A>, val pB: ParserF<I, E, B>) :
        ParserF<I, E, A>() {
        override fun toString(): String = "($pA *> $pB)"
    }

    data class ApR<out I, out E, out A, out B>(val pA: ParserF<I, E, A>, val pB: ParserF<I, E, B>) :
        ParserF<I, E, B>() {
        override fun toString(): String = "($pA <* $pB)"
    }

    // Selective
    data class Select<out I, out E, A, out B>(
        val pEither: ParserF<I, E, Either<A, B>>,
        val pIfLeft: ParserF<I, E, (A) -> B>
    ) : ParserF<I, E, B>() {
        override fun toString(): String = "($pEither <?* $pIfLeft)"
    }

    // Matchers
    data class Satisfy<I>(val match: Predicate<I>, val expected: Set<ErrorItem<I>> = setOf()) :
        ParserF<I, Nothing, I>() {
        override fun toString(): String = "Satisfy($expected)"
        }

    data class Single<I>(val i: I) : ParserF<I, Nothing, I>() {
        override fun toString(): String = "Single($i)"
    }

    // Alternative
    object Empty : ParserF<Nothing, Nothing, Nothing>() {
        override fun toString(): String = "Empty"
    }

    data class Alt<out I, out E, out A>(val left: ParserF<I, E, A>, val right: ParserF<I, E, A>) :
        ParserF<I, E, A>() {
        override fun toString(): String = "($left <|> $right)"
        }

    // LookAhead
    data class LookAhead<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, A>() {
        override fun toString(): String = "LookAhead($p)"
    }

    data class NegLookAhead<out I, out E>(val p: ParserF<I, E, Any?>) : ParserF<I, E, Unit>() {
        override fun toString(): String = "NegLookAhead($p)"
    }

    // Attempt
    data class Attempt<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, A>()

    // Recursion
    class Lazy<out I, out E, out A>(val f: () -> ParserF<I, E, A>) : ParserF<I, E, A>() {
        override fun toString(): String = "Lazy(...)"
    }

    data class Let(val recursive: Boolean, val sub: Int) : ParserF<Nothing, Nothing, Nothing>() {
        override fun toString(): String = "Let($recursive, $sub)"
    }

    // Intrinsics for better performance
    data class Many<out I, out E, out A>(val p: ParserF<I, E, A>) : ParserF<I, E, List<A>>() {
        override fun toString(): String = "Many($p)"
    }

    data class ConcatString<E>(val p: ParserF<Char, E, List<Char>>) : ParserF<Char, E, String>() {
        override fun toString(): String = "ConcatString($p)"
    }
}

fun interface Predicate<I> {
    operator fun invoke(i: I): Boolean
}
