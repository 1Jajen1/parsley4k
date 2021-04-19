package parsley.backend

import parsley.CompilerSettings
import parsley.Either
import parsley.ErrorItem
import parsley.ParseErrorT
import parsley.Predicate
import parsley.backend.instructions.AlwaysRecover
import parsley.backend.instructions.Apply
import parsley.backend.instructions.Call
import parsley.backend.instructions.Catch
import parsley.backend.instructions.CatchEither
import parsley.backend.instructions.Fail
import parsley.backend.instructions.FailIfLeft
import parsley.backend.instructions.FailIfLeftTop
import parsley.backend.instructions.Flip
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Jump
import parsley.backend.instructions.JumpGood
import parsley.backend.instructions.JumpGoodAttempt
import parsley.backend.instructions.JumpIfRight
import parsley.backend.instructions.JumpTable
import parsley.backend.instructions.Label
import parsley.backend.instructions.Many_
import parsley.backend.instructions.Map
import parsley.backend.instructions.MatchManyN_
import parsley.backend.instructions.MatchMany_
import parsley.backend.instructions.Matcher
import parsley.backend.instructions.MkPair
import parsley.backend.instructions.Pop
import parsley.backend.instructions.Push
import parsley.backend.instructions.PushChunkOf
import parsley.backend.instructions.PushHandler
import parsley.backend.instructions.RecoverAttempt
import parsley.backend.instructions.RecoverAttemptWith
import parsley.backend.instructions.RecoverWith
import parsley.backend.instructions.ResetOffset
import parsley.backend.instructions.ResetOffsetOnFail
import parsley.backend.instructions.ResetOnFailAndFailOnOk
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.backend.instructions.Single_
import parsley.collections.IntMap
import parsley.frontend.Alt
import parsley.frontend.Ap
import parsley.frontend.ApL
import parsley.frontend.ApR
import parsley.frontend.Attempt
import parsley.frontend.Binary
import parsley.frontend.ChunkOf
import parsley.frontend.Empty
import parsley.frontend.Eof
import parsley.frontend.FailTop
import parsley.frontend.Let
import parsley.frontend.LookAhead
import parsley.frontend.Many
import parsley.frontend.MatchOf
import parsley.frontend.NegLookAhead
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Select
import parsley.frontend.Single
import parsley.frontend.ToNative
import parsley.frontend.Unary
import parsley.unsafe

class DefaultCodeGenStep<I, E> : CodeGenStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.step(
        p: ParserF<I, E, Any?>,
        ctx: CodeGenContext<I, E>,
        settings: CompilerSettings<I, E>
    ): Boolean {
        when (p) {
            is Pure -> {
                if (!ctx.discard) ctx += Push(p.a)
            }
            is Ap<I, E, *, Any?> -> {
                if (p.first is Pure) {
                    callRecursive(p.second)
                    if (!ctx.discard) ctx += Map(p.first.unsafe<Pure<(Any?) -> Any?>>().a)
                } else {
                    callRecursive(p.first)
                    callRecursive(p.second)
                    if (!ctx.discard) ctx += Apply()
                }
            }
            is ApL<I, E, Any?, *> -> {
                callRecursive(p.first)
                ctx.withDiscard {
                    callRecursive(p.second)
                }
            }
            is ApR<I, E, *, Any?> -> {
                ctx.withDiscard {
                    callRecursive(p.first)
                }
                callRecursive(p.second)
            }
            is Let -> {
                if (ctx.discard) {
                    val l = if (p.sub in ctx.discardedSubParsers) {
                        ctx.discardedSubParsers[p.sub]
                    } else {
                        val label = ctx.mkLabel()
                        ctx.discardedSubParsers[p.sub] = label
                        label
                    }
                    ctx += Call(p.recursive, l)
                } else {
                    ctx += Call(p.recursive, p.sub)
                }
            }
            is Attempt -> {
                val l = ctx.mkLabel()
                ctx += InputCheck(l)
                callRecursive(p.inner)
                ctx += Label(l)
                ctx += ResetOffsetOnFail()
            }
            is Alt -> genAlternative(p, ctx, settings)
            is Empty -> {
                ctx += Fail()
            }
            is Satisfy<*> -> {
                if (ctx.discard) {
                    ctx += Satisfy_(p.match.unsafe(), p.expected.unsafe())
                } else {
                    ctx += parsley.backend.instructions.Satisfy(p.match.unsafe(), p.expected.unsafe())
                }
            }
            is Single<*> -> {
                if (ctx.discard) {
                    ctx += Single_(p.i.unsafe(), p.expected.unsafe())
                } else {
                    ctx += parsley.backend.instructions.Single(p.i.unsafe(), p.expected.unsafe())
                }
            }
            is LookAhead -> {
                val l = ctx.mkLabel()
                ctx += InputCheck(l)
                callRecursive(p.inner)
                ctx += Label(l)
                ctx += ResetOffset()
            }
            is NegLookAhead -> {
                val badLabel = ctx.mkLabel()
                ctx += InputCheck(badLabel)
                ctx.withDiscard {
                    callRecursive(p.inner)
                }
                ctx += Label(badLabel)
                ctx += ResetOnFailAndFailOnOk()
                if (!ctx.discard) ctx += Push(Unit)
            }
            is Select<I, E, *, Any?> -> {
                ctx.withDiscard(false) {
                    callRecursive(p.first)
                }
                when (val left = p.second) {
                    Empty -> {
                        ctx += FailIfLeft()
                        if (ctx.discard) ctx += Pop() // TODO
                    }
                    is parsley.frontend.Fail -> {
                        ctx += FailIfLeft(ParseErrorT.fromFinal(left.err))
                        if (ctx.discard) ctx += Pop() // TODO
                    }
                    FailTop -> {
                        ctx += FailIfLeftTop()
                        if (ctx.discard) ctx += Pop()
                    }
                    else -> {
                        val rightLabel = ctx.mkLabel()
                        ctx += JumpIfRight(rightLabel)
                        callRecursive(left)
                        if (!ctx.discard) {
                            ctx += Flip()
                            ctx += Apply()
                        }
                        ctx += Label(rightLabel)
                    }
                }
            }
            is Many<I, E, *> -> {
                when (val inner = p.inner) {
                    is Single<*> -> {
                        if (!ctx.discard) ctx += SingleMany(inner.i.unsafe())
                        else ctx += SingleMany_(inner.i.unsafe())
                        return true
                    }
                    is Satisfy<*> -> {
                        if (!ctx.discard) ctx += SatisfyMany(inner.match.unsafe())
                        else ctx += SatisfyMany_(inner.match.unsafe())
                        return true
                    }
                }
                if (ctx.discard) {
                    /* TODO This is not worth it over MatchManyN_(paths) even with tail call elimination
                    if (p.inner is Alt) {
                        val alt = p.inner as Alt
                        val path = mkPath(alt.first, ctx.subs)
                        if (path != null && path.size == 1) {
                            val lbl = ctx.mkLabel()
                            val let = Let(true, lbl)
                            val parser = ApR(Many(alt.first), Alt(ApR(alt.second, let), Pure(Unit)))
                            ctx.subs[lbl] = parser
                            callRecursive(let)
                            return true
                        }
                    }
                     */

                    val paths = mkPaths(p.inner, ctx.subs)
                    if (paths != null && paths.size > 1) {
                        ctx += MatchManyN_(paths)
                        return true
                    }
                }

                val handler = ctx.mkLabel()
                val jumpL = ctx.mkLabel()
                ctx += InputCheck(handler)
                ctx += Label(jumpL)
                callRecursive(p.inner)
                ctx += Label(handler)
                if (ctx.discard) {
                    ctx += Many_(jumpL)
                } else {
                    ctx += parsley.backend.instructions.Many(jumpL)
                }
            }
            is ChunkOf -> {
                if (ctx.discard) {
                    callRecursive(p.inner)
                } else {
                    val handler = ctx.mkLabel()
                    ctx += InputCheck(handler)
                    ctx.withDiscard {
                        callRecursive(p.inner)
                    }
                    ctx += Label(handler)
                    ctx += PushChunkOf()
                }
            }
            is MatchOf<I, E, *> -> {
                if (ctx.discard) {
                    callRecursive(p.inner)
                } else {
                    val handler = ctx.mkLabel()
                    ctx += InputCheck(handler)
                    callRecursive(p.inner)
                    ctx += Label(handler)
                    ctx += PushChunkOf()
                    ctx += MkPair()
                }
            }
            // TODO
            is parsley.frontend.Label -> {
                println("Generated label")
                callRecursive(p.inner)
            }
            is parsley.frontend.Catch<I, E, *> -> {
                val hdl = ctx.mkLabel()
                ctx += PushHandler(hdl)
                callRecursive(p.inner)
                ctx += Label(hdl)
                if (ctx.discard) {
                    ctx += AlwaysRecover()
                } else {
                    ctx += CatchEither()
                }
            }
            is parsley.frontend.Fail -> {
                ctx += Fail(ParseErrorT.fromFinal(p.err))
            }
            is Eof -> {
                ctx += parsley.backend.instructions.Eof()
                if (!ctx.discard) ctx += Push(Unit)
            }
            is ToNative -> {
                callRecursive(p.inner)
                if (!ctx.discard) ctx += parsley.backend.instructions.ToNative()
            }
            else -> return false
        }
        return true
    }
}

private fun <I, E> mkPath(p: ParserF<I, E, Any?>, subs: IntMap<ParserF<I, E, Any?>>): Array<Matcher<I>>? {
    val buf = mutableListOf<Matcher<I>>()
    var curr = p
    l@ while (true) {
        when (curr) {
            is Satisfy<*> -> {
                buf.add(Matcher.Sat(curr.match.unsafe(), curr.expected.unsafe()))
                break@l
            }
            is Single<*> -> {
                buf.add(Matcher.El(curr.i.unsafe(), curr.expected.unsafe()))
                break@l
            }
            is Pure -> break@l
            is ApR<I, E, *, Any?> -> {
                var l = curr.first
                val lbuf = mutableListOf<Matcher<I>>()
                i@ while (true) {
                    when (l) {
                        is Satisfy<*> -> {
                            lbuf.add(Matcher.Sat(l.match.unsafe(), l.expected.unsafe()))
                            break@i
                        }
                        is Single<*> -> {
                            lbuf.add(Matcher.El(l.i.unsafe(), l.expected.unsafe()))
                            break@i
                        }
                        Eof -> lbuf.add(Matcher.Eof())
                        is ApR<I, E, *, Any?> -> {
                            when (val el = l.second) {
                                is Satisfy<*> -> {
                                    lbuf.add(Matcher.Sat(el.match.unsafe(), el.expected.unsafe()))
                                }
                                is Single<*> -> {
                                    lbuf.add(Matcher.El(el.i.unsafe(), el.expected.unsafe()))
                                }
                                else -> return null
                            }
                            l = l.first
                        }
                        is parsley.frontend.Label -> l = l.inner
                        else -> return null
                    }
                }
                buf.addAll(lbuf.reversed())
                curr = curr.second
            }
            is parsley.frontend.Label -> curr = curr.inner
            is Let -> if (!curr.recursive) curr = subs[curr.sub] else return null
            else -> return null
        }
    }
    return buf.toTypedArray()
}

private fun <I, E> mkPaths(p: ParserF<I, E, Any?>, subs: IntMap<ParserF<I, E, Any?>>): Array<Array<Matcher<I>>>? {
    val buf = mutableListOf<Array<Matcher<I>>>()
    var curr = p
    while (curr is Alt) {
        mkPath(curr.first, subs)?.let(buf::add) ?: return null
        curr = curr.second
    }
    mkPath(curr, subs)?.let(buf::add) ?: return null
    return buf.toTypedArray()
}

@OptIn(ExperimentalStdlibApi::class)
private suspend fun <I, E> DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.genAlternative(
    p: Alt<I, E, Any?>,
    ctx: CodeGenContext<I, E>,
    settings: CompilerSettings<I, E>
): Unit {
    val alternatives = toAltList(p)

    val singles = mutableListOf<Single<I>>()
    val satisfies = mutableListOf<Satisfy<I>>()
    alternatives.forEach { p ->
        if (p is Single<*>) singles.add(p.unsafe())
        else if (p is Satisfy<*>) satisfies.add(p.unsafe())
    }
    if (alternatives.size > 1 && singles.size + satisfies.size == alternatives.size) {
        val func = settings.optimise.rebuildPredicate.f(singles.toTypedArray(), satisfies.map { it.match }.toTypedArray())
        val s = Satisfy(func)
        callRecursive(s)
        return
    } else if (alternatives.size > 2 && alternatives.last() is Pure && singles.size + satisfies.size == alternatives.size - 1) {
        val func = settings.optimise.rebuildPredicate.f(singles.toTypedArray(), satisfies.map { it.match }.toTypedArray())
        val s = Alt(Satisfy(func), alternatives.last())
        callRecursive(s)
        return
    }

    val (table, fallback, expected, overlaps) = toJumpTable(alternatives, ctx.subs)

    suspend fun <A> DeepRecursiveScope<ParserF<I, E, A>, Unit>.asChoice(xs: List<ParserF<I, E, A>>): Unit =
        when (xs.size) {
            1 -> callRecursive(xs.first())
            else -> {
                val fst = xs[0]
                val snd = xs[1]
                if (fst is Attempt) {
                    if (snd is Pure) {
                        val badLabel = ctx.mkLabel()
                        ctx += InputCheck(badLabel)
                        callRecursive(fst)
                        // TODO Do I not need JumpGood here?
                        ctx += Label(badLabel)
                        if (ctx.discard) ctx += RecoverAttempt()
                        else ctx += RecoverAttemptWith(snd.a)
                    } else {
                        val skip = ctx.mkLabel()
                        val badLabel = ctx.mkLabel()
                        ctx += InputCheck(badLabel)
                        callRecursive(fst)
                        ctx += Label(badLabel)
                        ctx += JumpGoodAttempt(skip)
                        asChoice(xs.drop(1))
                        ctx += Label(skip)
                    }
                } else {
                    if (snd is Pure) {
                        val skip = ctx.mkLabel()
                        val badLabel = ctx.mkLabel()
                        ctx += InputCheck(badLabel)
                        callRecursive(fst)
                        ctx += JumpGood(skip)
                        ctx += Label(badLabel)
                        if (ctx.discard) ctx += Catch()
                        else ctx += RecoverWith(snd.a)
                        ctx += Label(skip)
                    } else {
                        val skip = ctx.mkLabel()
                        val badLabel = ctx.mkLabel()
                        ctx += InputCheck(badLabel)
                        callRecursive(fst)
                        ctx += JumpGood(skip)
                        ctx += Label(badLabel)
                        ctx += Catch()
                        asChoice(xs.drop(1))
                        ctx += Label(skip)
                    }
                }
            }
        }

    when {
        table.size <= 1 -> asChoice(listOf(p.first, p.second))
        else -> {
            val end = ctx.mkLabel()
            val labels = table.mapValues { ctx.mkLabel() }
            val fL = ctx.mkLabel()
            if (fallback.isNotEmpty() && overlaps) {
                ctx += InputCheck(fL)
            }
            if (fallback.isNotEmpty() && !overlaps) ctx += JumpTable(labels, expected, fL)
            else ctx += JumpTable(labels, expected)
            table.forEach { (i, xs) ->
                ctx += Label(labels[i]!!)
                // TODO Is this even good? Also it's bugged
                if (xs.first() is Attempt) {
                    val l = ctx.mkLabel()
                    ctx += InputCheck(l)
                    asChoice(listOf(xs.first().unsafe<Attempt<I, E, Any?>>().inner) + xs.drop(1))
                    ctx += JumpGoodAttempt(end)
                } else {
                    asChoice(xs)
                    if (fallback.isNotEmpty() && overlaps) {
                        ctx += JumpGood(end)
                    } else {
                        ctx += Jump(end)
                    }
                }
            }
            if (fallback.isNotEmpty()) {
                ctx += Label(fL)
                if (overlaps) ctx += Catch()
                asChoice(fallback)
            }
            ctx += Label(end)
        }
    }
}

private fun <I, E> toAltList(
    p: Alt<I, E, Any?>
): List<ParserF<I, E, Any?>> {
    val buf = mutableListOf<ParserF<I, E, Any?>>()
    var curr: ParserF<I, E, Any?> = p
    while (curr is Alt) {
        buf.add(curr.first)
        curr = curr.second
    }
    buf.add(curr)
    return buf
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private fun <I, E> toJumpTable(
    alternatives: List<ParserF<I, E, Any?>>,
    subs: IntMap<ParserF<I, E, Any?>>
): Tuple4<MutableMap<I, MutableList<ParserF<I, E, Any?>>>, List<ParserF<I, E, Any?>>, Set<ErrorItem<I>>, Boolean> {
    val table = mutableMapOf<I, MutableList<ParserF<I, E, Any?>>>()
    val fallback = mutableListOf<Pair<Predicate<I>, ParserF<I, E, Any?>>>()
    val expectedBuf = mutableSetOf<ErrorItem<I>>()

    alternatives.forEach {
        it.findLeading(subs).fold({ pred ->
            fallback.add(pred to it)
        }, { (leading, expected) ->
            if (leading in table) table[leading]!!.add(it)
            else table[leading] = mutableListOf(it)
            expectedBuf.addAll(getExpected(it, subs) ?: expected)
        })
    }

    val overlaps = fallback.any { (f, _) -> table.keys.any { f(it) } }

    return Tuple4(table, fallback.map { it.second }, expectedBuf, overlaps)
}

fun <I, E> getExpected(p: ParserF<I, E, Any?>, subs: IntMap<ParserF<I, E, Any?>>): Set<ErrorItem<I>>? {
    val expected = mkPath(p, subs)?.map {
        when (it) {
            is Matcher.Eof -> setOf(ErrorItem.EndOfInput)
            is Matcher.El -> it.expected
            is Matcher.Sat -> it.expected
        }
    } ?: emptyList()
    if (expected.isNotEmpty()) {
        if (expected.all { it.size == 1 && it.first() is ErrorItem.Tokens<*> }) {
            val buf = mutableListOf<I>()
            expected.forEach {
                when (val i = it.first()) {
                    is ErrorItem.Tokens -> if (i.tail.isEmpty()) buf.add(i.head) else return expected.first()
                    else -> return expected.first()
                }
            }
            return setOf(ErrorItem.Tokens(buf.first(), buf.drop(1)))
        } else {
            return expected.first()
        }
    } else return null
}

@OptIn(ExperimentalStdlibApi::class)
private fun <I, E> ParserF<I, E, Any?>.findLeading(subs: IntMap<ParserF<I, E, Any?>>): Either<Predicate<I>, Pair<I, Set<ErrorItem<I>>>> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, Either<Predicate<I>, Pair<I, Set<ErrorItem<I>>>>> { p ->
        when (p) {
            is Pure -> Either.Left(Predicate { false })
            is Satisfy<*> -> Either.left(p.match.unsafe())
            is Single<*> -> Either.right(p.i.unsafe<I>() to p.expected.unsafe())
            is Ap<I, E, *, Any?> -> {
                if (p.first is Pure) p.second.findLeading(subs)
                else p.first.findLeading(subs)
            }
            is Alt -> {
                val l = callRecursive(p.first)
                val r = callRecursive(p.second)
                when {
                    l is Either.Left && r is Either.Left -> {
                        Either.Left(Predicate {
                            l.a(it) || r.a(it)
                        })
                    }
                    l is Either.Right && r is Either.Right -> {
                        Either.Left(Predicate {
                            it == l.b.first || it == r.b.first
                        })
                    }
                    l is Either.Left && r is Either.Right -> {
                        Either.Left(Predicate {
                            l.a(it) || it == r.b.first
                        })
                    }
                    l is Either.Right && r is Either.Left -> {
                        Either.Left(Predicate {
                            it == l.b.first || r.a(it)
                        })
                    }
                    else -> TODO("Impossible")
                }
            }
            is Unary<I, E, *, Any?> -> p.inner.findLeading(subs)
            is Binary<I, E, *, *, Any?> -> p.first.findLeading(subs)
            is Let -> subs[p.sub].findLeading(subs)
            else -> Either.left(Predicate { true })
        }
    }.invoke(this)

