package parsley.backend

import parsley.backend.instructions.Apply
import parsley.backend.instructions.Call
import parsley.backend.instructions.Catch
import parsley.backend.instructions.Fail
import parsley.backend.instructions.FailIfLeft
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
import parsley.backend.instructions.Push
import parsley.backend.instructions.PushHandler
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
import parsley.frontend.Empty
import parsley.frontend.Let
import parsley.frontend.LookAhead
import parsley.frontend.Many
import parsley.frontend.NegLookAhead
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Select
import parsley.frontend.Single
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
                    ctx += Satisfy_(p.match.unsafe())
                } else {
                    ctx += parsley.backend.instructions.Satisfy(p.match.unsafe())
                }
            }
            is Single<*> -> {
                if (ctx.discard) {
                    ctx += Single_(p.i.unsafe())
                } else {
                    ctx += parsley.backend.instructions.Single(p.i.unsafe())
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
                ctx += PushHandler(badLabel)
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
                if (p.pIfLeft is Empty) {
                    ctx += FailIfLeft()
                } else {
                    val rightLabel = ctx.mkLabel()
                    ctx += JumpIfRight(rightLabel)
                    callRecursive(p.pIfLeft)
                    if (!ctx.discard) {
                        ctx += Flip()
                        ctx += Apply()
                    }
                    ctx += Label(rightLabel)
                }
            }
            is Many<I, E, Any?> -> {
                when (p.p) {
                    is Satisfy<*> -> {
                        if (ctx.discard) ctx += SatisfyMany_(p.p.unsafe<Satisfy<I>>().match)
                        else ctx += SatisfyMany(p.p.unsafe<Satisfy<I>>().match)
                    }
                    is Single<*> -> {
                        if (ctx.discard) ctx += SingleMany_(p.p.unsafe<Single<I>>().i)
                        else ctx += SingleMany(p.p.unsafe<Single<I>>().i)
                    }
                    else -> {
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
            }
            else -> return false
        }
        return true
    }
}

@OptIn(ExperimentalStdlibApi::class)
private suspend fun <I, E> DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.genAlternative(
    p: Alt<I, E, Any?>,
    ctx: CodeGenContext<I, E>
): Unit {
    val (table, fallback) = toJumpTable(p, ctx.subs)

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
                        ctx += RecoverAttemptWith(p.right.unsafe<Pure<Any?>>().a)
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
                        ctx += RecoverWith(p.right.unsafe<Pure<Any?>>().a)
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
            ctx += JumpTable(labels)
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
): Pair<MutableMap<I, MutableList<ParserF<I, E, Any?>>>, MutableList<ParserF<I, E, Any?>>> {
    val table = mutableMapOf<I, MutableList<ParserF<I, E, Any?>>>()
    val fallback = mutableListOf<ParserF<I, E, Any?>>()
    var curr: ParserF<I, E, Any?> = p
    while (curr is Alt) {
        curr.left.findLeading(subs).takeIf { it.isNotEmpty() }?.forEach {
            val el = curr.unsafe<Alt<I, E, Any?>>().left
            if (table.containsKey(it)) table[it]!!.add(el)
            else table[it] = mutableListOf(el)
        } ?: fallback.add(curr.left)
        curr = curr.right
    }
    curr.findLeading(subs).takeIf { it.isNotEmpty() }?.forEach {
        if (table.containsKey(it)) table[it]!!.add(curr)
        else table[it] = mutableListOf(curr)
    } ?: fallback.add(curr)

    return table to fallback
}

// TODO Try to handle Alt as well. This would essentially be lifting the Alt up and then duplicating
//  part of the underlying parser...
private tailrec fun <I, E> ParserF<I, E, Any?>.findLeading(subs: IntMap<ParserF<I, E, Any?>>): Set<I> =
    when (this) {
        is Satisfy<*> -> accepts.unsafe()
        is Single<*> -> setOf(i.unsafe())
        is Attempt -> p.findLeading(subs)
        is Ap<I, E, *, Any?> -> {
            if (pF is Pure) pA.findLeading(subs)
            else pF.findLeading(subs)
        }
        is ApR<I, E, *, Any?> -> pA.findLeading(subs)
        is ApL<I, E, Any?, *> -> pA.findLeading(subs)
        is Let -> subs[sub].findLeading(subs)
        else -> emptySet()
    }
