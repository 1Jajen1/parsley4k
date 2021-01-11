package parsley.internal.frontend

import arrow.Either
import arrow.map
import parsley.CompileSettings
import parsley.Parser
import parsley.attempt
import parsley.combinators.alt
import parsley.combinators.ap
import parsley.combinators.followedBy
import parsley.combinators.followedByDiscard
import parsley.combinators.many
import parsley.combinators.map
import parsley.combinators.pure
import parsley.internal.backend.optimise.isCheap
import parsley.internal.unsafe
import parsley.lookAhead
import parsley.negLookAhead

@OptIn(ExperimentalStdlibApi::class)
fun <I, E, A> Parser<I, E, A>.optimise(settings: CompileSettings): Parser<I, E, Any?> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        when (p) {
            is ParserF.Ap<I, E, *, Any?> -> {
                val pf = callRecursive(p.pF).unsafe<ParserF<I, E, (Any?) -> Any>>()
                val pa = callRecursive(p.pA)
                when {
                    // Pure f <*> Pure a = Pure (f a)
                    pf is ParserF.Pure && pa is ParserF.Pure -> {
                        ParserF.Pure(pf.a(pa.a))
                    }
                    // p <*> Pure a = Pure (\f -> f a) <*> p
                    pa is ParserF.Pure -> {
                        Parser.pure { f: (Any?) -> Any? -> f(pa.a) }
                            .ap(Parser(pf))
                            .parserF.let { callRecursive(it) }
                    }
                    // p <*> (q <*> r) = Pure compose <*> p <*> q <*> r
                    pa is ParserF.Ap<I, E, *, Any?> -> {
                        Parser.pure { f: (Any?) -> Any? -> { g: (Any?) -> Any? -> f.compose(g) } }
                            .ap(Parser(pf))
                            .ap(Parser(pa.pF.unsafe()))
                            .ap(Parser(pa.pA))
                            .parserF.let { callRecursive(it) }
                    }
                    // (p *> q) <*> r == p *> (q <*> r)
                    pf is ParserF.ApR<I, E, *, (Any?) -> Any?> -> {
                        Parser(pf.pA).followedBy(Parser(pf.pB).ap(Parser(pa)))
                            .parserF.let { callRecursive(it) }
                    }
                    // empty <*> x = empty || x <*> empty = empty
                    pf is ParserF.Empty -> pf
                    pa is ParserF.Empty -> pa
                    else -> ParserF.Ap(pf, pa)
                }
            }
            is ParserF.ApL<I, E, Any?, *> -> {
                val l = callRecursive(p.pA)
                val r = callRecursive(p.pB)
                when {
                    // p <* Pure _ = p
                    r is ParserF.Pure -> l
                    // Pure x <* p = p *> Pure x
                    l is ParserF.Pure -> Parser(p.pB).followedBy(Parser(p.pA))
                        .parserF.let { callRecursive(it) }
                    // p <* (q *> Pure _) = p <* q
                    r is ParserF.ApR<I, E, *, *> && r.pB is ParserF.Pure -> {
                        Parser(p.pA).followedByDiscard(Parser(r.pA))
                            .parserF.let { callRecursive(it) }
                    }
                    // p <* (Pure _ <* q) = p <* q
                    r is ParserF.ApL<I, E, *, *> && r.pA is ParserF.Pure -> {
                        Parser(p.pA).followedByDiscard(Parser(r.pB))
                            .parserF.let { callRecursive(it) }
                    }
                    // (p <* q) <* r == p <* (q <* r)
                    l is ParserF.ApL<I, E, *, *> -> {
                        Parser(l.pA).followedByDiscard(Parser(l.pB).followedByDiscard(Parser(p.pB)))
                            .parserF.let { callRecursive(it) }
                    }
                    // empty <* p == p && p <* empty == p
                    l is ParserF.Empty -> l
                    r is ParserF.Empty -> r
                    else -> ParserF.ApL(l, r)
                }
            }
            is ParserF.ApR<I, E, *, Any?> -> {
                val l = callRecursive(p.pA)
                val r = callRecursive(p.pB)
                when {
                    // Pure _ *> p == p
                    l is ParserF.Pure -> r
                    // (p *> Pure _) *> q == p *> q
                    l is ParserF.ApR<I, E, *, *> && l.pB is ParserF.Pure -> {
                        Parser(l.pA).followedBy(Parser(p.pB))
                            .parserF.let { callRecursive(it) }
                    }
                    // (Pure _ <* p) *> q == p *> q
                    l is ParserF.ApL<I, E, *, *> && l.pA is ParserF.Pure -> {
                        Parser(l.pB).followedBy(Parser(p.pB))
                            .parserF.let { callRecursive(it) }
                    }
                    // p *> (q *> r) == (p *> q) *> r
                    r is ParserF.ApR<I, E, *, Any?> -> {
                        Parser(p.pA).followedBy(Parser(r.pA)).followedBy(Parser(r.pB))
                            .parserF.let { callRecursive(it) }
                    }
                    // empty *> p && p *> empty == empty
                    l is ParserF.Empty -> l
                    r is ParserF.Empty -> r
                    else -> ParserF.ApR(l, r)
                }
            }
            is ParserF.Alt -> {
                val l = callRecursive(p.left)
                val r = callRecursive(p.right)
                when {
                    // Pure x <|> p == Pure x
                    l is ParserF.Pure -> l
                    // TODO merge errors or distinguish empty from fail
                    // empty <|> p == p
                    l is ParserF.Empty -> r
                    // p <|> empty == p
                    r is ParserF.Empty -> l
                    // (p <|> q) <|> r = p <|> (q <|> r)
                    l is ParserF.Alt -> {
                        Parser(l.left).alt(Parser(l.right).alt(Parser(p.right)))
                            .parserF.let { callRecursive(it) }
                    }
                    // LookAhead p <|> LookAhead q = LookAhead (Attempt p <|> q)
                    l is ParserF.LookAhead && r is ParserF.LookAhead ->
                        Parser(l.p).attempt().alt(Parser(r.p)).lookAhead()
                            .parserF.let { callRecursive(it) }
                    // NotFollowedBy p <|> NotFollowedBy q = NotFollowedBy (LookAhead p *> LookAhead q)
                    l is ParserF.NegLookAhead && r is ParserF.NegLookAhead ->
                        Parser(l.p).lookAhead().followedBy(Parser(r.p).lookAhead()).negLookAhead()
                            .parserF.let { callRecursive(it) }
                    else -> ParserF.Alt(l, r)
                }
            }
            is ParserF.Select<I, E, *, Any?> -> {
                val l = callRecursive(p.pEither).unsafe<ParserF<I, E, Either<Any?, Any?>>>()
                val r = callRecursive(p.pIfLeft).unsafe<ParserF<I, E, (Any?) -> Any?>>()
                when {
                    // Select (Pure (Left x)) ifL = iFl <*> Pure x
                    l is ParserF.Pure && l.a is Either.Left<Any?> ->
                        Parser(r).ap(Parser.pure(l.a.a))
                            .parserF.let { callRecursive(it) }
                    // Select (Pure (Right x)) _ = Pure x
                    l is ParserF.Pure && l.a is Either.Right<Any?> -> ParserF.Pure(l.a.b)
                    // Select p (Pure f) = (\e -> either f id) <$> p
                    r is ParserF.Pure ->
                        Parser(p.pEither).map { it.map(r.a) }
                            .parserF.let { callRecursive(it) }
                    else -> ParserF.Select(l, r)
                }
            }
            is ParserF.LookAhead -> {
                when (val pInner = callRecursive(p.p)) {
                    // LookAhead (Pure x) == Pure x
                    is ParserF.Pure -> pInner
                    // LookAhead Empty = Empty
                    is ParserF.Empty -> pInner
                    // LookAhead (LookAhead p) == LookAhead(p)
                    is ParserF.LookAhead -> pInner
                    // LookAhead (NotFollowedBy p) = NotFollowedBy p
                    is ParserF.NegLookAhead -> pInner
                    else -> ParserF.LookAhead(pInner)
                }
            }
            is ParserF.NegLookAhead -> {
                when (val pInner = callRecursive(p.p)) {
                    // NotFollowedBy (Pure _) = Empty
                    is ParserF.Pure -> ParserF.Empty
                    // NotFollowedBy Empty = Pure Unit
                    is ParserF.Empty -> ParserF.Pure(Unit)
                    // NotFollowedBy (NotFollowedBy p) == LookAhead p
                    is ParserF.NegLookAhead -> ParserF.LookAhead(pInner.p)
                    // NotFollowedBy (Try p <|> q) == NotFollowedBy p *> NotFollowedBy q
                    is ParserF.Alt -> {
                        if (pInner.left is ParserF.Attempt) {
                            Parser(pInner.left.p)
                                .negLookAhead().followedBy(Parser(pInner.right).negLookAhead())
                                .parserF.let { callRecursive(it) }
                        } else ParserF.NegLookAhead(pInner)
                    }
                    else -> ParserF.NegLookAhead(pInner)
                }
            }
            is ParserF.Attempt -> {
                when (val pInner = callRecursive(p.p)) {
                    is ParserF.Satisfy<*> -> pInner.unsafe()
                    is ParserF.Single<*> -> pInner
                    is ParserF.Pure -> pInner
                    is ParserF.Attempt -> pInner
                    is ParserF.NegLookAhead -> pInner
                    else -> ParserF.Attempt(pInner)
                }
            }
            is ParserF.Many<I, E, Any?> -> {
                val pInner = callRecursive(p.p)
                when {
                    pInner is ParserF.Pure -> throw IllegalStateException("Many never consumes input and thus never finishes")
                    else -> ParserF.Many(pInner)
                }
            }
            is ParserF.ConcatString -> {
                ParserF.ConcatString(callRecursive(p.p.unsafe()).unsafe<ParserF<Char, E, List<Char>>>()).unsafe()
            }
            else -> p
        }
    }.invoke(parserF).let(::Parser)

private fun <A, B, C> ((A) -> B).compose(g: (C) -> A): (C) -> B = { c -> this(g(c)) }
