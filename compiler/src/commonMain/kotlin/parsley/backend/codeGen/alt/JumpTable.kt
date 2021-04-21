package parsley.backend.codeGen.alt

import parsley.AnyParser
import parsley.Either
import parsley.ErrorItem
import parsley.Predicate
import parsley.backend.CodeGenContext
import parsley.backend.instructions.Catch
import parsley.backend.instructions.InputCheck
import parsley.backend.instructions.Jump
import parsley.backend.instructions.JumpGood
import parsley.backend.instructions.JumpGoodAttempt
import parsley.backend.instructions.JumpTable
import parsley.backend.instructions.Label
import parsley.frontend.Alt
import parsley.frontend.Ap
import parsley.frontend.Attempt
import parsley.frontend.Binary
import parsley.frontend.Let
import parsley.frontend.ParserF
import parsley.frontend.Pure
import parsley.frontend.Satisfy
import parsley.frontend.Single
import parsley.frontend.Unary
import parsley.settings.SubParsers
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
suspend fun <I, E> DeepRecursiveScope<AnyParser<I, E>, Unit>.altJumpTableGen(
    alternatives: List<ParserF<I, E, Any?>>,
    ctx: CodeGenContext<I, E>
): Unit? {
    val (table, fallback, expected, overlaps) = toJumpTable(alternatives, ctx)

    when {
        table.size <= 1 -> return null
        else -> {
            val end = ctx.mkLabel()
            // TODO find duplicates in this and fallback and put them at the same label!
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
                    altDefaultGen(listOf(xs.first().unsafe<Attempt<I, E, Any?>>().inner) + xs.drop(1), ctx)
                    ctx += JumpGoodAttempt(end)
                } else {
                    altDefaultGen(xs, ctx)
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
                altDefaultGen(fallback, ctx)
            }
            ctx += Label(end)
            return Unit
        }
    }
}

private fun <I, E> toJumpTable(
    alternatives: List<ParserF<I, E, Any?>>,
    ctx: CodeGenContext<I, E>
): Tuple4<MutableMap<I, MutableList<ParserF<I, E, Any?>>>, List<ParserF<I, E, Any?>>, Set<ErrorItem<I>>, Boolean> {
    val table = mutableMapOf<I, MutableList<ParserF<I, E, Any?>>>()
    val fallback = mutableListOf<Pair<Predicate<I>, ParserF<I, E, Any?>>>()
    val expectedBuf = mutableSetOf<ErrorItem<I>>()

    alternatives.forEach { p ->
        p.findLeading(ctx.getSubParsers()).fold({ pred ->
            fallback.add(pred to p)
        }, { (leading, expected) ->
            if (leading in table) table[leading]!!.add(p)
            else table[leading] = mutableListOf(p)
            expectedBuf.addAll(getExpected(p, ctx.getSubParsers()) ?: expected)
        })
        /* TODO to make this work we need to guarantee that we don't overlap in the lifted cases,
            we need to float in definitions in the lifted cases and optimise there and also join duplicate paths
            This seems much better suited for the frontend optimise phase
        val lbl = ctx.mkLabel()
        val let = Let(recursive = false, lbl)
        ctx.subs[lbl] = p
        p.findAllLeading(ctx.subs).forEach { e ->
            e.fold({ pred ->
                fallback.add(pred to let)
            }, { (leading, expected) ->
                if (leading in table) table[leading]!!.add(let)
                else table[leading] = mutableListOf(let)
                expectedBuf.addAll(getExpected(p, ctx.subs) ?: expected)
            })
        }
         */
    }

    val overlaps = fallback.any { (f, _) -> table.keys.any { f(it) } }

    return Tuple4(table, fallback.map { it.second }, expectedBuf, overlaps)
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E> ParserF<I, E, Any?>.findAllLeading(subs: SubParsers<I, E>): List<Either<Predicate<I>, Pair<I, Set<ErrorItem<I>>>>> =
    DeepRecursiveFunction<ParserF<I, E, Any?>, List<Either<Predicate<I>, Pair<I, Set<ErrorItem<I>>>>>> { p ->
        when (p) {
            // Terminal cases
            is Pure -> listOf(Either.Left(Predicate { false }))
            is Satisfy<*> -> listOf(Either.left(p.match.unsafe()))
            is Single<*> -> listOf(Either.right(p.i.unsafe<I>() to p.expected.unsafe()))
            // Alt
            is Alt -> {
                val left = callRecursive(p.first)
                val right = callRecursive(p.second)
                left + right
            }
            // fallthrough cases
            is Ap<I, E, *, Any?> ->
                if (p.first is Pure) callRecursive(p.second)
                else callRecursive(p.first)
            is Unary<I, E, *, Any?> -> callRecursive(p.inner)
            is Binary<I, E, *, *, Any?> -> callRecursive(p.first)
            is Let ->
                if (p.recursive) listOf(Either.left(Predicate { true }))
                else callRecursive(subs[p.sub])
            // TODO vv ??
            else -> listOf(Either.left(Predicate { true }))
        }
    }.invoke(this)

