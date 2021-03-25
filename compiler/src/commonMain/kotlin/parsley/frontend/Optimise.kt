package parsley.frontend

import parsley.CompilerSettings
import parsley.Either
import parsley.FrontendSettings
import parsley.Predicate
import parsley.unsafe
import kotlin.Lazy

@OptIn(ExperimentalStdlibApi::class)
fun <I, E, A> ParserF<I, E, A>.optimise(
    steps: Array<OptimiseStep<I, E>>,
    settings: CompilerSettings<I, E>
): ParserF<I, E, A> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        steps.fold(p) { acc, s -> s.run { step(acc, settings) } }
    }(this).unsafe()

interface OptimiseStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        settings: CompilerSettings<I, E>
    ): ParserF<I, E, Any?>
}

class DefaultOptimiseStep<I, E> : OptimiseStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        settings: CompilerSettings<I, E>
    ): ParserF<I, E, Any?> =
        when (p) {
            is Ap<I, E, *, Any?> -> {
                val pf = callRecursive(p.pF).unsafe<ParserF<I, E, (Any?) -> Any>>()
                val pa = callRecursive(p.pA)
                when {
                    // Pure f <*> Pure a = Pure (f a)
                    pf is Pure && pa is Pure -> {
                        Pure(pf.a(pa.a))
                    }
                    // p <*> Pure a = Pure (\f -> f a) <*> p
                    pa is Pure -> {
                        val ff = Pure { f: (Any?) -> Any? -> f(pa.a) }
                        callRecursive(Ap(ff, pf))
                    }
                    // p <*> (q <*> r) = Pure compose <*> p <*> q <*> r
                    pa is Ap<I, E, *, Any?> -> {
                        val compose = Pure { f: (Any?) -> Any? -> { g: (Any?) -> Any? -> f.compose(g) } }
                        val fff = Ap(compose, pf)
                        val ff = Ap(fff, pa.pF.unsafe())

                        callRecursive(Ap(ff, pa.pA))
                    }
                    // (p *> q) <*> r == p *> (q <*> r)
                    pf is ApR<I, E, *, (Any?) -> Any?> -> {
                        val right = ApR(pf.pB, pa)

                        callRecursive(ApR(pf.pA, right))
                    }
                    // empty <*> x = empty || x <*> empty = empty
                    pf is Empty -> pf
                    pa is Empty -> pa
                    else -> Ap(pf, pa)
                }
            }
            is ApL<I, E, Any?, *> -> {
                val l = callRecursive(p.pA)
                val r = callRecursive(p.pB)
                when {
                    // p <* Pure _ = p
                    r is Pure -> l
                    // Pure x <* p = p *> Pure x
                    l is Pure -> callRecursive(ApR(r, l))
                    // p <* (q *> Pure _) = p <* q
                    r is ApR<I, E, *, *> && r.pB is Pure -> {
                        callRecursive(ApL(l, r.pA))
                    }
                    // p <* (Pure _ <* q) = p <* q
                    r is ApL<I, E, *, *> && r.pA is Pure -> {
                        callRecursive(ApL(l, r.pB))
                    }
                    // (p <* q) <* r == p <* (q <* r)
                    l is ApL<I, E, *, *> -> {
                        val right = ApL(l.pB, r)

                        callRecursive(ApL(l.pA, right))
                    }
                    // empty <* p == p && p <* empty == p
                    l is Empty -> l
                    r is Empty -> r
                    else -> ApL(l, r)
                }
            }
            is ApR<I, E, *, Any?> -> {
                val l = callRecursive(p.pA)
                val r = callRecursive(p.pB)
                when {
                    // Pure _ *> p == p
                    l is Pure -> r
                    // (p *> Pure _) *> q == p *> q
                    l is ApR<I, E, *, *> && l.pB is Pure -> {
                        callRecursive(ApR(l.pA, r))
                    }
                    // (Pure _ <* p) *> q == p *> q
                    l is ApL<I, E, *, *> && l.pA is Pure -> {
                        callRecursive(ApR(l.pB, r))
                    }
                    // p *> (q *> r) == (p *> q) *> r
                    r is ApR<I, E, *, Any?> -> {
                        val left = ApR(l, r.pA)
                        callRecursive(ApR(left, r.pB))
                    }
                    // empty *> p && p *> empty == empty
                    l is Empty -> l
                    r is Empty -> r
                    else -> ApR(l, r)
                }
            }
            is Alt -> {
                val l = callRecursive(p.left)
                val r = callRecursive(p.right)
                when {
                    // Pure x <|> p == Pure x
                    l is Pure -> l
                    // TODO merge errors or distinguish empty from fail
                    // empty <|> p == p
                    l is Empty -> r
                    // p <|> empty == p
                    r is Empty -> l
                    // (p <|> q) <|> r = p <|> (q <|> r)
                    l is Alt -> {
                        val right = Alt(l.right, r)
                        callRecursive(Alt(l.left, right))
                    }
                    // LookAhead p <|> LookAhead q = LookAhead (Attempt p <|> q)
                    l is LookAhead && r is LookAhead -> {
                        val left = Attempt(l.p)
                        val inner = Alt(left, r.p)
                        callRecursive(LookAhead(inner))
                    }
                    // NotFollowedBy p <|> NotFollowedBy q = NotFollowedBy (LookAhead p *> LookAhead q)
                    l is NegLookAhead && r is NegLookAhead -> {
                        val inner = ApR(LookAhead(l.p), LookAhead(r.p))
                        callRecursive(NegLookAhead(inner))
                    }
                    else -> Alt(l, r)
                }
            }
            is Select<I, E, *, Any?> -> {
                val l = callRecursive(p.pEither).unsafe<ParserF<I, E, Either<Any?, Any?>>>()
                val r = callRecursive(p.pIfLeft).unsafe<ParserF<I, E, (Any?) -> Any?>>()
                when {
                    // Select (Pure (Left x)) ifL = iFl <*> Pure x
                    l is Pure && l.a is Either.Left<Any?> -> {
                        callRecursive(Ap(r, Pure(l.a.unsafe<Either.Left<Any?>>().a)))
                    }
                    // Select (Pure (Right x)) _ = Pure x
                    l is Pure && l.a is Either.Right<Any?> -> {
                        Pure(l.a.unsafe<Either.Right<Any?>>().b)
                    }
                    // Select p (Pure f) = (\e -> either f id) <$> p
                    r is Pure -> {
                        callRecursive(Ap(Pure(r.a), p.pEither))
                    }
                    else -> Select(l, r)
                }
            }
            is LookAhead -> {
                when (val pInner = callRecursive(p.p)) {
                    // LookAhead (Pure x) == Pure x
                    is Pure -> pInner
                    // LookAhead Empty = Empty
                    is Empty -> pInner
                    // LookAhead (LookAhead p) == LookAhead(p)
                    is LookAhead -> pInner
                    // LookAhead (NotFollowedBy p) = NotFollowedBy p
                    is NegLookAhead -> pInner
                    else -> LookAhead(pInner)
                }
            }
            is NegLookAhead -> {
                when (val pInner = callRecursive(p.p)) {
                    // NotFollowedBy (Pure _) = Empty
                    is Pure -> Empty
                    // NotFollowedBy Empty = Pure Unit
                    is Empty -> Pure(Unit)
                    // NotFollowedBy (NotFollowedBy p) == LookAhead p
                    is NegLookAhead -> LookAhead(pInner.p)
                    // NotFollowedBy (Try p <|> q) == NotFollowedBy p *> NotFollowedBy q
                    is Alt -> {
                        if (pInner.left is Attempt) {
                            val l = NegLookAhead(pInner.left.unsafe<Attempt<I, E, Any?>>().p)
                            val r = NegLookAhead(pInner.right)
                            callRecursive(ApR(l, r))
                        } else NegLookAhead(pInner)
                    }
                    else -> NegLookAhead(pInner)
                }
            }
            is Attempt -> {
                when (val pInner = callRecursive(p.p)) {
                    is Satisfy<*> -> pInner.unsafe()
                    is Single<*> -> pInner
                    is Pure -> pInner
                    is Attempt -> pInner
                    is NegLookAhead -> pInner
                    else -> Attempt(pInner)
                }
            }
            is Many<I, E, Any?> -> {
                when (val pInner = callRecursive(p.p)) {
                    is Pure -> throw IllegalStateException("Many never consumes input and thus never finishes")
                    is Empty -> Pure(emptyList())
                    else -> Many(pInner)
                }
            }
            is ChunkOf<I, E, Any?> -> {
                when (val pInner = callRecursive(p.p)) {
                    is Empty -> Empty
                    else -> ChunkOf(pInner)
                }
            }
            else -> p
        }
}

private inline fun <A, B, C> ((A) -> B).compose(crossinline g: (C) -> A): (C) -> B = { c -> this(g(c)) }
