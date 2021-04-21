package parsley.frontend.simplifier

import parsley.frontend.Alt
import parsley.frontend.ApR
import parsley.frontend.Attempt
import parsley.frontend.LookAhead
import parsley.frontend.NegLookAhead
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.frontend.TransformStep
import parsley.unsafe

fun <I, E> rewrite(): TransformStep<I, E> {
    val (rewriteAlt) = rewriteAlt<I, E>()
    val (rewriteLookAhead) = rewriteLookAhead<I, E>()
    val (rewriteNegLookAhead) = rewriteNegLookAhead<I, E>()
    val (rewriteAttempt) = rewriteAttempt<I, E>()
    return TransformStep { p, sub, settings ->
        val p1 = rewriteAlt(p, sub, settings)
        val p2 = rewriteLookAhead(p1, sub, settings)
        val p3 = rewriteNegLookAhead(p2, sub, settings)
        val p4 = rewriteAttempt(p3, sub, settings)
        p4
    }
}

fun <I, E> rewriteAlt(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is Alt) {
        val l = p.first
        val r = p.second
        when {
            // p <|> p == p
            l == r -> l
            // LookAhead p <|> LookAhead q = LookAhead (Attempt p <|> q)
            l is LookAhead && r is LookAhead -> LookAhead(Alt(Attempt(l.inner), r.inner))
            // NegLookAhead p <|> NegLookAhead q = NegLookAhead (LookAhead p *> LookAhead q)
            l is NegLookAhead && r is NegLookAhead -> NegLookAhead(ApR(LookAhead(l.inner), LookAhead(r.inner)))
            else -> p
        }
    } else p
}

fun <I, E> rewriteLookAhead(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is LookAhead) {
        val inner = p.inner
        when {
            // LookAhead (LookAhead p) == LookAhead p
            inner is LookAhead -> inner
            // LookAhead (NegLookAhead p) = NegLookAhead p
            inner is NegLookAhead -> inner
            else -> p
        }
    } else p
}


fun <I, E> rewriteNegLookAhead(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is NegLookAhead) {
        val inner = p.inner
        when {
            // NegLookAhead (NegLookAhead p) == LookAhead p *> Pure Unit
            inner is NegLookAhead -> ApR(LookAhead(inner.inner), Pure(Unit))
            // NegLookAhead (Attempt p <|> q) == NegLookAhead p *> NegLookAhead q
            inner is Alt && inner.first is Attempt ->
                ApR(NegLookAhead(inner.first.unsafe<Attempt<I, E, Any?>>().inner), NegLookAhead(inner.second))
            else -> p
        }
    } else p
}

fun <I, E> rewriteAttempt(): TransformStep<I, E> = TransformStep { p, _, _ ->
    if (p is Attempt) {
        val inner = p.inner
        when {
            // Attempt (Satisfy f) == Satisfy f
            inner is Satisfy<*> -> inner
            // Attempt (Single i) == Single i
            inner is Single<*> -> inner
            // Attempt (NegLookAhead p) == NegLookAhead p
            inner is NegLookAhead -> inner
            // Attempt (Attempt p) == Attempt p
            inner is Attempt -> inner
            else -> p
        }
    } else p
}
