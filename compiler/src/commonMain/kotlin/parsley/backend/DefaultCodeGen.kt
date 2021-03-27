package parsley.backend

import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.ParseErrorT
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
import parsley.backend.instructions.Recover
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
import parsley.frontend.ChunkOf
import parsley.frontend.Empty
import parsley.frontend.Eof
import parsley.frontend.FailTop
import parsley.frontend.Hide
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
import parsley.toTemplate
import parsley.unsafe

class DefaultCodeGenStep<I, E> : CodeGenStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.step(
        p: ParserF<I, E, Any?>,
        ctx: CodeGenContext<I, E>
    ): Boolean {
        when (p) {
            is Pure -> {
                if (!ctx.discard) ctx += Push(p.a)
            }
            is Ap<I, E, *, Any?> -> {
                if (p.pF is Pure) {
                    callRecursive(p.pA)
                    if (!ctx.discard) ctx += Map(p.pF.unsafe<Pure<(Any?) -> Any?>>().a)
                } else {
                    callRecursive(p.pF)
                    callRecursive(p.pA)
                    if (!ctx.discard) ctx += Apply()
                }
            }
            is ApL<I, E, Any?, *> -> {
                callRecursive(p.pA)
                ctx.withDiscard {
                    callRecursive(p.pB)
                }
            }
            is ApR<I, E, *, Any?> -> {
                ctx.withDiscard {
                    callRecursive(p.pA)
                }
                callRecursive(p.pB)
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
                callRecursive(p.p)
                ctx += Label(l)
                ctx += ResetOffsetOnFail()
            }
            is Alt -> genAlternative(p, ctx)
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
                callRecursive(p.p)
                ctx += Label(l)
                ctx += ResetOffset()
            }
            is NegLookAhead -> {
                val badLabel = ctx.mkLabel()
                ctx += InputCheck(badLabel)
                ctx.withDiscard {
                    callRecursive(p.p)
                }
                ctx += Label(badLabel)
                ctx += ResetOnFailAndFailOnOk()
                if (!ctx.discard) ctx += Push(Unit)
            }
            is Select<I, E, *, Any?> -> {
                ctx.withDiscard(false) {
                    callRecursive(p.pEither)
                }
                when (val left = p.pIfLeft) {
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
            is Many<I, E, Any?> -> {
                val path = mkPath(p.p, ctx.subs)
                val paths = mkPaths(p.p, ctx.subs)
                if (path != null) {
                    if (path.size == 1) {
                        when (val el = path[0]) {
                            is Matcher.Sat -> {
                                if (ctx.discard) ctx += SatisfyMany_(el.f)
                                else ctx += SatisfyMany(el.f)
                            }
                            is Matcher.El -> {
                                if (ctx.discard) ctx += SingleMany_(el.el)
                                else ctx += SingleMany(el.el)
                            }
                        }
                    } else {
                        ctx += MatchMany_(path)
                    }
                } else if (paths != null) {
                    ctx += MatchManyN_(paths)
                } else {
                    val handler = ctx.mkLabel()
                    val jumpL = ctx.mkLabel()
                    ctx += InputCheck(handler)
                    ctx += Label(jumpL)
                    callRecursive(p.p)
                    ctx += Label(handler)
                    if (ctx.discard) {
                        ctx += Many_(jumpL)
                    } else {
                        ctx += parsley.backend.instructions.Many(jumpL)
                    }
                }
            }
            is ChunkOf -> {
                if (ctx.discard) {
                    callRecursive(p.p)
                } else {
                    val handler = ctx.mkLabel()
                    ctx += InputCheck(handler)
                    ctx.withDiscard {
                        callRecursive(p.p)
                    }
                    ctx += Label(handler)
                    ctx += PushChunkOf()
                }
            }
            is MatchOf<I, E, Any?> -> {
                if (ctx.discard) {
                    callRecursive(p.p)
                } else {
                    val handler = ctx.mkLabel()
                    ctx += InputCheck(handler)
                    callRecursive(p.p)
                    ctx += Label(handler)
                    ctx += PushChunkOf()
                    ctx += MkPair()
                }
            }
            // TODO
            is parsley.frontend.Label -> {
                println("Generated label")
                callRecursive(p.p)
            }
            is Hide -> {
                println("Generated hide")
                callRecursive(p.p)
            }
            is parsley.frontend.Catch<I, E, Any?> -> {
                val hdl = ctx.mkLabel()
                ctx += PushHandler(hdl)
                callRecursive(p.p)
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
                var l = curr.pA
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
                            when (val el = l.pB) {
                                is Satisfy<*> -> {
                                    lbuf.add(Matcher.Sat(el.match.unsafe(), el.expected.unsafe()))
                                }
                                is Single<*> -> {
                                    lbuf.add(Matcher.El(el.i.unsafe(), el.expected.unsafe()))
                                }
                                else -> return null
                            }
                            l = l.pA
                        }
                        is parsley.frontend.Label -> l = l.p
                        else -> return null
                    }
                }
                buf.addAll(lbuf.reversed())
                curr = curr.pB
            }
            is parsley.frontend.Label -> curr = curr.p
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
        mkPath(curr.left, subs)?.let(buf::add) ?: return null
        curr = curr.right
    }
    mkPath(curr, subs)?.let(buf::add) ?: return null
    return buf.toTypedArray()
}

@OptIn(ExperimentalStdlibApi::class)
private suspend fun <I, E> DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.genAlternative(
    p: Alt<I, E, Any?>,
    ctx: CodeGenContext<I, E>
): Unit {
    val (table, fallback, expected) = toJumpTable(p, ctx.subs)

    suspend fun <A> DeepRecursiveScope<ParserF<I, E, A>, Unit>.asChoice(xs: List<ParserF<I, E, A>>): Unit =
        when (xs.size) {
            1 -> callRecursive(xs.first())
            else -> {
                val fst = xs.first()
                if (p.left is Attempt) {
                    if (p.right is Pure) {
                        val badLabel = ctx.mkLabel()
                        ctx += InputCheck(badLabel)
                        callRecursive(fst)
                        ctx += Label(badLabel)
                        if (ctx.discard) ctx += RecoverAttempt()
                        else ctx += RecoverAttemptWith(p.right.unsafe<Pure<Any?>>().a)
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
                    if (p.right is Pure) {
                        val skip = ctx.mkLabel()
                        val badLabel = ctx.mkLabel()
                        ctx += InputCheck(badLabel)
                        callRecursive(fst)
                        ctx += JumpGood(skip)
                        ctx += Label(badLabel)
                        if (ctx.discard) ctx += Recover()
                        else ctx += RecoverWith(p.right.unsafe<Pure<Any?>>().a)
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
        table.size <= 1 -> asChoice(listOf(p.left, p.right))
        else -> {
            val end = ctx.mkLabel()
            val labels = table.mapValues { ctx.mkLabel() }
            val fL = ctx.mkLabel()
            if (fallback.isNotEmpty()) {
                ctx += InputCheck(fL)
            }
            ctx += JumpTable(labels, expected)
            table.forEach { (i, xs) ->
                ctx += Label(labels[i]!!)
                // TODO Is this even good?
                if (xs.first() is Attempt) {
                    val l = ctx.mkLabel()
                    ctx += InputCheck(l)
                    asChoice(listOf(xs.first().unsafe<Attempt<I, E, Any?>>().p) + xs.drop(1))
                    ctx += JumpGoodAttempt(end)
                } else {
                    asChoice(xs)
                    if (fallback.isNotEmpty()) {
                        ctx += JumpGood(end)
                    } else {
                        ctx += Jump(end)
                    }
                }
            }
            if (fallback.isNotEmpty()) {
                ctx += Label(fL)
                ctx += Catch()
                asChoice(fallback)
            }
            ctx += Label(end)
        }
    }
}

private fun <I, E> toJumpTable(
    p: Alt<I, E, Any?>,
    subs: IntMap<ParserF<I, E, Any?>>
): Triple<MutableMap<I, MutableList<ParserF<I, E, Any?>>>, MutableList<ParserF<I, E, Any?>>, Set<ErrorItem<I>>> {
    val table = mutableMapOf<I, MutableList<ParserF<I, E, Any?>>>()
    val fallback = mutableListOf<ParserF<I, E, Any?>>()
    var curr: ParserF<I, E, Any?> = p
    val expectedBuf = mutableSetOf<ErrorItem<I>>()
    while (curr is Alt) {
        curr.left.findLeading(subs)?.let { (els, expected) ->
            els.forEach {
                val el = curr.unsafe<Alt<I, E, Any?>>().left
                if (table.containsKey(it)) table[it]!!.add(el)
                else table[it] = mutableListOf(el)
            }
            expectedBuf.addAll(getExpected(curr.unsafe<Alt<I, E, Any?>>().left, subs) ?: expected)
        } ?: fallback.add(curr.left)
        curr = curr.right
    }
    curr.findLeading(subs)?.let { (els, expected) ->
        els.forEach {
            if (table.containsKey(it)) table[it]!!.add(curr)
            else table[it] = mutableListOf(curr)
        }
        expectedBuf.addAll(getExpected(curr, subs) ?: expected)
    } ?: fallback.add(curr)

    return Triple(table, fallback, expectedBuf)
}

fun <I, E> getExpected(p: ParserF<I, E, Any?>, subs: IntMap<ParserF<I, E, Any?>>): Set<ErrorItem<I>>? {
    val expected = mkPath(p, subs)?.map  {
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

// TODO Try to handle Alt as well. This would essentially be lifting the Alt up and then duplicating
//  part of the underlying parser...
// TODO Collect all tokens of a path if the pattern is ApR(ApR(Sat, Sat), Sat) etc and it only has one expected
/*
 * Idea: Look for Ap/ApR/ApL(Alt(...), p) and collect jump table from Alt
 *  Also look for special case Ap/ApR/ApL(Alt(..., Pure(x)), p) and also collect from p because there is a no-consume way to it
 */
private tailrec fun <I, E> ParserF<I, E, Any?>.findLeading(subs: IntMap<ParserF<I, E, Any?>>): Pair<Set<I>, Set<ErrorItem<I>>>? =
    when (this) {
        is Satisfy<*> -> emptySet<I>() to expected.unsafe()
        is Single<*> -> setOf(i.unsafe<I>()) to expected.unsafe()
        is Attempt -> p.findLeading(subs)
        is Ap<I, E, *, Any?> -> {
            if (pF is Pure) pA.findLeading(subs)
            else pF.findLeading(subs)
        }
        is ApR<I, E, *, Any?> -> pA.findLeading(subs)
        is ApL<I, E, Any?, *> -> pA.findLeading(subs)
        is ChunkOf -> p.findLeading(subs)
        is MatchOf<I, E, Any?> -> p.findLeading(subs)
        is parsley.frontend.Label -> p.findLeading(subs)
        is Let -> subs[sub].findLeading(subs)
        else -> null
    }
