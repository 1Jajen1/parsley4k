package parsley.internal.frontend

import arrow.Either
import arrow.map
import parsley.Parser
import parsley.ParserOf
import parsley.attempt
import parsley.combinators.alt
import parsley.combinators.ap
import parsley.combinators.followedBy
import parsley.combinators.followedByDiscard
import parsley.combinators.map
import parsley.combinators.pure
import parsley.fix
import parsley.lookAhead
import parsley.negLookAhead

fun <I, E, A> Parser<I, E, A>.optimise(): Parser<I, E, A> =
    cata<I, E, A, ParserOf<I, E>> { p ->
        when (p) {
            is ParserF.Ap<ParserOf<I, E>, *, A> -> {
                val pf = p.pF.fix().parserF as ParserF<I, E, ParserOf<I, E>, (Any?) -> A>
                val pa = p.pA.fix().parserF
                when {
                    // Pure f <*> Pure a = Pure (f a)
                    pf is ParserF.Pure && pa is ParserF.Pure -> {
                        ParserF.Pure(pf.a(pa.a))
                    }
                    // p <*> Pure a = Pure (\f -> f a) <*> p
                    pa is ParserF.Pure -> {
                        Parser.pure { f: (Any?) -> Any? -> f(pa.a) }
                            .ap(Parser(pf))
                            .optimise().parserF
                    }
                    // p <*> (q <*> r) = Pure compose <*> p <*> q <*> r
                    pa is ParserF.Ap<ParserOf<I, E>, *, Any?> -> {
                        Parser.pure { f: (Any?) -> Any? -> { g: (Any?) -> Any? -> f.compose(g) } }
                            .ap(Parser(pf))
                            .ap(pa.pF.fix() as Parser<I, E, (Any?) -> Any?>)
                            .ap(pa.pA.fix())
                            .optimise().parserF
                    }
                    // (p *> q) <*> r == p *> (q <*> r)
                    pf is ParserF.ApR<ParserOf<I, E>, *, (Any?) -> A> -> {
                        pf.pA.fix().followedBy(pf.pB.fix().ap(Parser(pa)))
                            .optimise().parserF
                    }
                    // empty <*> x = empty || x <*> empty = empty
                    pf is ParserF.Empty -> pf
                    pa is ParserF.Empty -> pa
                    else -> p
                }
            }
            is ParserF.ApL<ParserOf<I, E>, A, *> -> {
                val l = p.pA.fix().parserF
                val r = p.pB.fix().parserF
                when {
                    // p <* Pure _ = p
                    r is ParserF.Pure -> l
                    // Pure x <* p = p *> Pure x
                    l is ParserF.Pure -> p.pB.fix().followedBy(p.pA.fix()).optimise().parserF
                    // p <* (q *> Pure _) = p <* q
                    r is ParserF.ApR<ParserOf<I, E>, *, *> && r.pB.fix().parserF is ParserF.Pure -> {
                        p.pA.fix().followedByDiscard(r.pA.fix()).optimise().parserF
                    }
                    // p <* (Pure _ <* q) = p <* q
                    r is ParserF.ApL<ParserOf<I, E>, *, *> && r.pA.fix().parserF is ParserF.Pure -> {
                        p.pA.fix().followedByDiscard(r.pB.fix()).optimise().parserF
                    }
                    // (p <* q) <* r == p <* (q <* r)
                    l is ParserF.ApL<ParserOf<I, E>, *, *> -> {
                        l.pA.fix().followedByDiscard(l.pB.fix().followedByDiscard(p.pB.fix()))
                            .optimise().parserF
                    }
                    // empty <* p == p && p <* empty == p
                    l is ParserF.Empty -> l
                    r is ParserF.Empty -> r
                    else -> p
                }
            }
            is ParserF.ApR<ParserOf<I, E>, *, A> -> {
                val l = p.pA.fix().parserF
                val r = p.pB.fix().parserF
                when {
                    // Pure _ *> p == p
                    l is ParserF.Pure -> r
                    // (p *> Pure _) *> q == p *> q
                    l is ParserF.ApR<ParserOf<I, E>, *, *> && l.pB.fix().parserF is ParserF.Pure -> {
                        l.pA.fix().followedBy(p.pB.fix())
                            .optimise().parserF
                    }
                    // (Pure _ <* p) *> q == p *> q
                    l is ParserF.ApL<ParserOf<I, E>, *, *> && l.pA.fix().parserF is ParserF.Pure -> {
                        l.pB.fix().followedBy(p.pB.fix())
                            .optimise().parserF
                    }
                    // p *> (q *> r) == (p *> q) *> r
                    r is ParserF.ApR<ParserOf<I, E>, *, A> -> {
                        p.pA.fix().followedBy(r.pA.fix()).followedBy(r.pB.fix())
                            .optimise().parserF
                    }
                    // empty *> p && p *> empty == empty
                    l is ParserF.Empty -> l
                    r is ParserF.Empty -> r
                    else -> p
                }
            }
            is ParserF.Alt -> {
                val l = p.left.fix().parserF
                val r = p.right.fix().parserF
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
                        l.left.fix().alt(l.right.fix().alt(p.right.fix()))
                            .optimise().parserF
                    }
                    // LookAhead p <|> LookAhead q = LookAhead (Attempt p <|> q)
                    l is ParserF.LookAhead && r is ParserF.LookAhead ->
                        l.p.fix().attempt().alt(r.p.fix()).lookAhead()
                            .optimise().parserF
                    // NotFollowedBy p <|> NotFollowedBy q = NotFollowedBy (LookAhead p *> LookAhead q)
                    l is ParserF.NegLookAhead && r is ParserF.NegLookAhead ->
                        l.p.fix().lookAhead().followedBy(r.p.fix().lookAhead()).negLookAhead()
                            .optimise().parserF
                    else -> p
                }
            }
            is ParserF.Select<ParserOf<I, E>, *, A> -> {
                val l = p.pEither.fix().parserF
                val r = p.pIfLeft.fix().parserF
                when {
                    // Select (Pure (Left x)) ifL = iFl <*> Pure x
                    l is ParserF.Pure && l.a is Either.Left ->
                        (p.pIfLeft.fix() as Parser<I, E, (Any?) -> A>).ap(Parser.pure(l.a.a))
                            .optimise().parserF
                    // Select (Pure (Right x)) _ = Pure x
                    l is ParserF.Pure && l.a is Either.Right -> ParserF.Pure(l.a.b)
                    // Select p (Pure f) = (\e -> either f id) <$> p
                    r is ParserF.Pure ->
                        p.pEither.fix().map { it.map(r.a as (Any?) -> A) }
                            .optimise().parserF
                    else -> p
                }
            }
            is ParserF.LookAhead -> {
                when (val pInner = p.p.fix().parserF) {
                    // LookAhead (Pure x) == Pure x
                    is ParserF.Pure -> pInner
                    // LookAhead Empty = Empty
                    is ParserF.Empty -> pInner
                    // LookAhead (LookAhead p) == LookAhead(p)
                    is ParserF.LookAhead -> pInner
                    // LookAhead (NotFollowedBy p) = NotFollowedBy p
                    is ParserF.NegLookAhead -> pInner
                    else -> p
                }
            }
            is ParserF.NegLookAhead -> {
                when (val pInner = p.p.fix().parserF) {
                    // NotFollowedBy (Pure _) = Empty
                    is ParserF.Pure -> ParserF.Empty<I, E>()
                    // NotFollowedBy Empty = Pure Unit
                    is ParserF.Empty -> ParserF.Pure(Unit)
                    // NotFollowedBy (NotFollowedBy p) == LookAhead p
                    is ParserF.NegLookAhead -> ParserF.LookAhead(pInner.p)
                    // NotFollowedBy (Try p <|> q) == NotFollowedBy p *> NotFollowedBy q
                    is ParserF.Alt -> {
                        if (pInner.left.fix().parserF is ParserF.Attempt) {
                            (pInner.left.fix().parserF as ParserF.Attempt<ParserOf<I, E>, Any?>).p.fix()
                                .negLookAhead().followedBy(pInner.right.fix().negLookAhead())
                                .optimise().parserF
                        } else p
                    }
                    else -> p
                }
            }
            is ParserF.Attempt -> {
                when (val pInner = p.p.fix().parserF) {
                    is ParserF.Satisfy<*> -> pInner
                    is ParserF.Single<*> -> pInner
                    is ParserF.Pure -> pInner
                    is ParserF.Attempt -> pInner
                    is ParserF.NegLookAhead -> pInner
                    else -> p
                }
            }
            is ParserF.Many<ParserOf<I, E>, *> -> {
                when (val pInner = p.p.fix().parserF) {
                    is ParserF.Pure -> throw IllegalStateException("Many never consumes input and thus never finishes")
                    else -> p
                }
            }
            else -> p
        }.let(::Parser) as Parser<I, E, A>
    }.fix()

private fun <A, B, C> ((A) -> B).compose(g: (C) -> A): (C) -> B = { c -> this(g(c)) }
