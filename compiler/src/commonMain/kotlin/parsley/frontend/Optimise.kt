package parsley.frontend

import parsley.CompilerSettings
import parsley.Either
import parsley.ErrorItem
import parsley.collections.IntMap
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
fun <I, E, A> ParserF<I, E, A>.optimise(
    subs: IntMap<ParserF<I, E, Any?>>,
    steps: Array<OptimiseStep<I, E>>,
    settings: CompilerSettings<I, E>
): ParserF<I, E, A> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        steps.fold(p) { acc, s -> s.run { step(acc, subs, settings) } }
    }(this).unsafe()

interface OptimiseStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        subs: IntMap<ParserF<I, E, Any?>>,
        settings: CompilerSettings<I, E>
    ): ParserF<I, E, Any?>
}

class DefaultOptimiseStep<I, E> : OptimiseStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        subs: IntMap<ParserF<I, E, Any?>>,
        settings: CompilerSettings<I, E>
    ): ParserF<I, E, Any?> =
        when (p) {
            is Ap<I, E, *, Any?> -> {
                val pf = callRecursive(p.first).unsafe<ParserF<I, E, (Any?) -> Any>>()
                val pa = callRecursive(p.second)
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
                    // p <*> Fail = p *> Fail
                    pa is Fail -> {
                        callRecursive(ApR(pf, pa))
                    }
                    // p <*> (q <*> r) = Pure compose <*> p <*> q <*> r
                    pa is Ap<I, E, *, Any?> -> {
                        val compose = Pure { f: (Any?) -> Any? -> { g: (Any?) -> Any? -> f.compose(g) } }
                        val fff = Ap(compose, pf)
                        val ff = Ap(fff, pa.first.unsafe())

                        callRecursive(Ap(ff, pa.second))
                    }
                    // (p *> q) <*> r == p *> (q <*> r)
                    pf is ApR<I, E, *, *> -> {
                        val right = ApR(pf.second, pa)
                        callRecursive(ApR(pf.first, right))
                    }
                    // empty <*> x = empty || x <*> empty = empty
                    pf is Empty -> pf
                    pa is Empty -> pa
                    // Pure f <*> (p <|> q) = (Pure f <*> p) <|> (Pure f <*> q)
                    pf is Pure && pa is Alt -> {
                        callRecursive(Alt(Ap(pf, pa.first), Ap(pf, pa.second)))
                    }
                    else -> Ap(pf, pa)
                }
            }
            is ApL<I, E, Any?, *> -> {
                val l = callRecursive(p.first)
                val r = callRecursive(p.second)
                when {
                    // p <* Pure _ = p
                    r is Pure -> l
                    // Pure x <* p = p *> Pure x
                    l is Pure -> callRecursive(ApR(r, l))
                    // p <* (q *> Pure _) = p <* q
                    r is ApR<I, E, *, *> && r.second is Pure -> {
                        callRecursive(ApL(l, r.first))
                    }
                    // p <* (Pure _ <* q) = p <* q
                    r is ApL<I, E, *, *> && r.first is Pure -> {
                        callRecursive(ApL(l, r.second))
                    }
                    // (p <* q) <* r == p <* (q <* r)
                    l is ApL<I, E, *, *> -> {
                        val right = ApL(l.second, r)

                        callRecursive(ApL(l.first, right))
                    }
                    // empty <* p == p && p <* empty == p
                    l is Empty -> l
                    r is Empty -> r
                    else -> ApL(l, r)
                }
            }
            is ApR<I, E, *, Any?> -> {
                val l = callRecursive(p.first)
                val r = callRecursive(p.second)
                when {
                    // Pure _ *> p == p
                    l is Pure -> r
                    // (p *> Pure _) *> q == p *> q
                    l is ApR<I, E, *, *> && l.second is Pure -> {
                        callRecursive(ApR(l.first, r))
                    }
                    // (Pure _ <* p) *> q == p *> q
                    l is ApL<I, E, *, *> && l.first is Pure -> {
                        callRecursive(ApR(l.second, r))
                    }
                    // p *> (q *> r) == (p *> q) *> r
                    r is ApR<I, E, *, Any?> -> {
                        val left = ApR(l, r.first)
                        callRecursive(ApR(left, r.second))
                    }
                    // empty *> p && p *> empty == empty
                    l is Empty -> l
                    r is Empty -> r
                    else -> ApR(l, r)
                }
            }
            is Alt -> {
                val l = callRecursive(p.first)
                val r = callRecursive(p.second)
                when {
                    // p <|> p = p
                    l == r -> l
                    // Pure x <|> p == Pure x
                    l is Pure -> l
                    // empty <|> p == p
                    l is Empty -> r
                    // p <|> empty == p
                    r is Empty -> l
                    // (p <|> q) <|> r = p <|> (q <|> r)
                    l is Alt -> {
                        val right = Alt(l.second, r)
                        callRecursive(Alt(l.first, right))
                    }
                    // LookAhead p <|> LookAhead q = LookAhead (Attempt p <|> q)
                    l is LookAhead && r is LookAhead -> {
                        val left = Attempt(l.inner)
                        val inner = Alt(left, r.inner)
                        callRecursive(LookAhead(inner))
                    }
                    // NegLookAhead p <|> NegLookAhead q = NegLookAhead (LookAhead p *> LookAhead q)
                    l is NegLookAhead && r is NegLookAhead -> {
                        val inner = ApR(LookAhead(l.inner), LookAhead(r.inner))
                        callRecursive(NegLookAhead(inner))
                    }
                    else -> Alt(l, r)
                }
            }
            is Select<I, E, *, Any?> -> {
                val l = callRecursive(p.first).unsafe<ParserF<I, E, Either<Any?, Any?>>>()
                val r = callRecursive(p.second).unsafe<ParserF<I, E, (Any?) -> Any?>>()
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
                        callRecursive(Ap(Pure(r.a), p.first))
                    }
                    else -> Select(l, r)
                }
            }
            is LookAhead -> {
                when (val pInner = callRecursive(p.inner)) {
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
                when (val pInner = callRecursive(p.inner)) {
                    // NotFollowedBy (Pure _) = Empty
                    is Pure -> Empty
                    // NotFollowedBy Empty = Pure Unit
                    is Empty -> Pure(Unit)
                    // NotFollowedBy (NotFollowedBy p) == LookAhead p
                    is NegLookAhead -> LookAhead(pInner.inner)
                    // NotFollowedBy (Try p <|> q) == NotFollowedBy p *> NotFollowedBy q
                    is Alt -> {
                        if (pInner.first is Attempt) {
                            val l = NegLookAhead(pInner.first.unsafe<Attempt<I, E, Any?>>().inner)
                            val r = NegLookAhead(pInner.second)
                            callRecursive(ApR(l, r))
                        } else NegLookAhead(pInner)
                    }
                    else -> NegLookAhead(pInner)
                }
            }
            is Attempt -> {
                when (val pInner = callRecursive(p.inner)) {
                    is Satisfy<*> -> pInner.unsafe()
                    is Single<*> -> pInner
                    is Pure -> pInner
                    is Attempt -> pInner
                    is NegLookAhead -> pInner
                    else -> Attempt(pInner)
                }
            }
            is Many<I, E, *> -> {
                when (val pInner = callRecursive(p.inner)) {
                    is Pure -> throw IllegalStateException("Many never consumes input and thus never finishes")
                    // is Eof ???? What would this imply? This either diverges into an infinite loop or is Pure [Unit]...
                    is Empty -> Pure(emptyList())
                    is Many<I, E, *> -> Ap(Pure { listOf(it) }, pInner)
                    else -> Many(pInner)
                }
            }
            is ChunkOf -> {
                when (val pInner = callRecursive(p.inner)) {
                    Empty -> Empty
                    is MatchOf<I, E, *> -> ChunkOf(callRecursive(pInner.inner))
                    is ChunkOf -> pInner
                    is Alt -> Alt(ChunkOf(pInner), ChunkOf(pInner))
                    else -> ChunkOf(pInner)
                }
            }
            is MatchOf<I, E, *> -> {
                when (val pInner = callRecursive(p.inner)) {
                    Empty -> Empty
                    // TODO This breaks the CharListToString pattern ...
                    // is ChunkOf -> Ap(Pure { a -> a to a }, pInner)
                    is MatchOf<I, E, *> -> pInner
                    else -> MatchOf(pInner)
                }
            }
            is Label -> {
                val inner = callRecursive(p.inner)
                val res = inner.relabel(settings, p.label, subs)
                callRecursive(res)
            }
            is Catch<I, E, *> -> {
                when (val pInner = callRecursive(p.inner)) {
                    Empty -> Catch(Empty) // TODO
                    is Pure -> pInner
                    else -> Catch(pInner)
                }
            }
            is Unary<I, E, *, Any?> -> {
                val inner = callRecursive(p.inner)
                p.copy(inner.unsafe())
            }
            is Binary<I, E, *, *, Any?> -> {
                p.copy(
                    callRecursive(p.first).unsafe(),
                    callRecursive(p.second).unsafe()
                )
            }
            is Let -> {
                // TODO Inline rules!
                p
            }
            else -> p
        }
}

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> ParserF<I, E, Any?>.relabel(
    settings: CompilerSettings<I, E>,
    label: String?,
    subs: IntMap<ParserF<I, E, Any?>>
): ParserF<I, E, Any?> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { pI ->
        settings.frontend.relabeSteps.fold(null as ParserF<I, E, Any?>?) { acc, s ->
            acc ?: s.run { step(pI, label, subs) }
        } ?: throw IllegalStateException("No step could relabel")
    }.invoke(this)

interface RelabelStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        lbl: String?,
        subs: IntMap<ParserF<I, E, Any?>>
    ): ParserF<I, E, Any?>?
}

class DefaultRelabelStep<I, E>: RelabelStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        lbl: String?,
        subs: IntMap<ParserF<I, E, Any?>>
    ): ParserF<I, E, Any?>? {
        return when (p) {
            is Alt -> Alt(callRecursive(p.first), callRecursive(p.second))
            is Satisfy<*> -> Satisfy<I>(
                p.match.unsafe(),
                lbl?.let { setOf(ErrorItem.Label(it)) } ?: emptySet(),
                p.accepts.unsafe(), p.rejects.unsafe()
            )
            is Single<*> -> Single<I>(
                p.i.unsafe(),
                lbl?.let { setOf(ErrorItem.Label(it)) } ?: emptySet()
            )
            is Ap<I, E, *, Any?> ->
                if (p.first is Pure) Ap(p.first, callRecursive(p.second).unsafe())
                else Ap(callRecursive(p.first).unsafe(), p.second)
            is Many<I, E, *>, is Pure, is Fail, is FailTop ->  p
            // TODO double check if I need more exceptions here
            is Unary<I, E, *, Any?> -> p.copy(callRecursive(p.inner).unsafe())
            is Binary<I, E, *, *, Any?> -> p.copy(callRecursive(p.first).unsafe(), p.second.unsafe())
            // Why not relabel Let by relabelling the referenced parser?
            // Well the problem is multiple labeled parsers may use the same let bound one, hence we can't
            // guarantee our label remains the only one and we'd overwrite labels.
            // is Let -> ???
            else -> null.also { println(p) }
        }
    }

}

private inline fun <A, B, C> ((A) -> B).compose(crossinline g: (C) -> A): (C) -> B = { c -> this(g(c)) }
