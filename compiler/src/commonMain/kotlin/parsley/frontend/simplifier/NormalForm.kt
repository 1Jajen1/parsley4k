package parsley.frontend.simplifier

import parsley.frontend.Alt
import parsley.frontend.Ap
import parsley.frontend.ApL
import parsley.frontend.ApR
import parsley.frontend.Pure
import parsley.frontend.TransformStep
import parsley.unsafe

fun <I, E> normalForm(): TransformStep<I, E> {
    val (apNormal) = apNormalForm<I, E>()
    val (apLNormal) = apLNormalForm<I, E>()
    val (apRNormal) = apRNormalForm<I, E>()
    val (altNormal) = altNormalForm<I, E>()
    return TransformStep { p, sub, settings ->
        val p1 = apNormal(p, sub, settings)
        val p2 = apLNormal(p1, sub, settings)
        val p3 = apRNormal(p2, sub, settings)
        val p4 = altNormal(p3, sub, settings)
        p4
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> apNormalForm(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is Ap<I, E, *, Any?>) {
        val first = p.first
        val second = p.second
        when {
            // (p *> q) <*> r == p *> (q <*> r)
            first is ApR<I, E, *, *> ->
                ApR(first.first, callRecursive(Ap(first.second.unsafe(), second)))
            // p <*> Pure a == Pure { f -> f(a) } <*> p
            first !is Pure && second is Pure -> Ap(Pure { f: (Any?) -> Any? -> f(second.a) }, p.first.unsafe())
            // p <*> (q <*> r) == ((Pure compose <*> p) <*> q) <*> r
            second is Ap<I, E, *, Any?> -> {
                val composed = Pure { f: (Any?) -> Any? -> { g: (Any?) -> Any? -> f.compose(g) } }
                Ap(Ap(Ap(composed, p.first.unsafe()), second.first.unsafe()), second.second)
            }
            else -> p
        }
    } else p
}

private inline fun <A, B, C> ((A) -> B).compose(crossinline g: (C) -> A): (C) -> B = { c -> this(g(c)) }

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> apLNormalForm(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is ApL<I, E, Any?, *>) {
        when (val first = p.first) {
            // Pure x <* p == p *> Pure x
            is Pure -> ApR(p.second, first)
            // (p <* q) <* r == p <* (q <* r)
            is ApL<I, E, Any?, *> -> {
                ApL(first.first, callRecursive(ApL(first.second, p.second)))
            }
            else -> p
        }
    } else p
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> apRNormalForm(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is ApR<I, E, *, Any?>) {
        when (val second = p.second) {
            // p *> (q *> r) == (p *> q) *> r
            is ApR<I, E, *, Any?> -> {
                ApR(callRecursive(ApR(p.first, second.first)), second.second)
            }
            else -> p
        }
    } else p
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> altNormalForm(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is Alt) {
        when (val first = p.first) {
            // (p <|> q) <|> r
            is Alt -> callRecursive(Alt(first.first, Alt(first.second, p.second)))
            else -> p
        }
    } else p
}
