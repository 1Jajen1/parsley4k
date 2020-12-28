package parsley.internal.frontend

import arrow.Either
import parsley.ErrorItem
import parsley.Kind
import parsley.ParseError
import parsley.Parser
import parsley.fix

internal class ForParserF private constructor()
internal typealias ParserFOf<I, E, F> = Kind<Kind<Kind<ForParserF, I>, E>, F>
internal fun <I, E, F, A> Kind<ParserFOf<I, E, F>, A>.fix(): ParserF<I, E, F, A> = this as ParserF<I, E, F, A>

internal sealed class ParserF<out I, out E, out F, out A>: Kind<ParserFOf<I, E, F>, A> {
    // Applicative hierarchy
    data class Pure<out A>(val a: A) : ParserF<Nothing, Nothing, Nothing, A>()

    data class Ap<out F, A, out B>(val pF: Kind<F, (A) -> B>, val pA: Kind<F, A>) : ParserF<Nothing, Nothing, F, B>()

    data class ApL<out F, out A, out B>(val pA: Kind<F, A>, val pB: Kind<F, B>) : ParserF<Nothing, Nothing, F, A>()

    data class ApR<out F, out A, out B>(val pA: Kind<F, A>, val pB: Kind<F, B>) : ParserF<Nothing, Nothing, F, B>()

    // Selective
    data class Select<out F, A, out B>(val pEither: Kind<F, Either<A, B>>, val pIfLeft: Kind<F, (A) -> B>) : ParserF<Nothing, Nothing, F, B>()

    // Matchers
    data class Satisfy<I>(val match: (I) -> Boolean, val expected: Set<ErrorItem<I>> = setOf()) : ParserF<I, Nothing, Nothing, I>()

    data class Single<I>(val i: I, val expected: Set<ErrorItem<I>> = setOf(ErrorItem.Tokens(i))) : ParserF<I, Nothing, Nothing, I>()

    // Alternative
    class Empty<out I, out E>(val error: ParseError<I, E> = ParseError.Trivial(offset = -1)) : ParserF<I, E, Nothing, Nothing>()

    data class Alt<out F, out A>(val left: Kind<F, A>, val right: Kind<F, A>) : ParserF<Nothing, Nothing, F, A>()

    // LookAhead
    data class LookAhead<out F, out A>(val p: Kind<F, A>) : ParserF<Nothing, Nothing, F, A>()

    data class NegLookAhead<out F>(val p: Kind<F, Any?>) : ParserF<Nothing, Nothing, F, Unit>()

    // Attempt
    data class Attempt<out F, out A>(val p: Kind<F, A>) : ParserF<Nothing, Nothing, F, A>()

    // Recursion
    data class Lazy<out F, out A>(val f: () -> Kind<F, A>) : ParserF<Nothing, Nothing, F, A>()

    data class Let(val recursive: Boolean, val sub: Int) : ParserF<Nothing, Nothing, Nothing, Nothing>()
}

@Suppress("UNCHECKED_CAST")
internal inline fun <I, E, F, G, A> ParserF<I, E, F, A>.imap(trans: (Kind<F, Any?>) -> Kind<G, Any?>): ParserF<I, E, G, A> =
    when (this) {
        is ParserF.Pure -> ParserF.Pure(a)
        is ParserF.Ap<F, *, A> -> {
            val ff = trans(pF) as Kind<G, (Any?) -> A>
            val fa = trans(pA)
            ParserF.Ap(ff, fa)
        }
        is ParserF.ApL<F, A, *> -> ParserF.ApL(trans(pA) as Kind<G, A>, trans(pB))
        is ParserF.ApR<F, *, A> -> ParserF.ApR(trans(pA), trans(pB) as Kind<G, A>)
        is ParserF.Alt -> ParserF.Alt(trans(left) as Kind<G, A>, trans(right) as Kind<G, A>)
        is ParserF.Empty -> ParserF.Empty(error)
        is ParserF.Select<F, *, A> -> {
            val fEither = trans(pEither)
            val fIfLeft = trans(pIfLeft)
            ParserF.Select(fEither as Kind<G, Either<Any?, A>>, fIfLeft as Kind<G, (Any?) -> A>)
        }
        is ParserF.Attempt -> ParserF.Attempt(trans(p) as Kind<G, A>)
        is ParserF.LookAhead -> ParserF.LookAhead(trans(p) as Kind<G, A>)
        is ParserF.NegLookAhead -> ParserF.NegLookAhead(trans(p)) as ParserF<I, E, G, A>
        is ParserF.Satisfy<*> -> ParserF.Satisfy(match as (I) -> Boolean, expected as Set<ErrorItem<I>>) as ParserF<I, E, G, A>
        is ParserF.Single<*> -> ParserF.Single(i as I,  expected as Set<ErrorItem<I>>) as ParserF<I, E, G, A>
        is ParserF.Let -> ParserF.Let(recursive, sub)
        is ParserF.Lazy -> TODO("no please don't call cata on lazy parsers, evaluate and place let bindings first ")
    }

internal typealias Algebra<I, E, G, A> = (ParserF<I, E, G, A>) -> Kind<G, A>

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A, G> Parser<I, E, A>.cata(f: (ParserF<I, E, G, A>) -> Kind<G, A>): Kind<G, A> {
    return DeepRecursiveFunction<Parser<I, E, A>, Kind<G, A>> { p ->
        // The cast is only on the last generic and that is turned to any so cannot be used anyways
        p.parserF.imap { callRecursive(it.fix() as Parser<I, E, A>) }.let(f)
    }(this)
}
