package parsley.internal.frontend

import arrow.Either
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import parsley.Parser
import parsley.internal.unsafe

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> Parser<I, E, A>.findLetBound(): Pair<Set<ParserF<I, E, Any?>>, Set<ParserF<I, E, Any?>>> {
    val refCount = mutableMapOf<ParserF<I, E, Any?>, Int>()
    val recursives = mutableSetOf<ParserF<I, E, Any?>>()
    DeepRecursiveFunction<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit> { (seen, p) ->
        refCount[p].also { refCount[p] = it?.let { it + 1 } ?: 1 }

        if (seen.contains(p)) {
            recursives.add(p)
            return@DeepRecursiveFunction
        }

        if (refCount[p]!! == 1) {
            val nS = seen.add(p)
            when (p) {
                is ParserF.Ap<I, E, *, Any?> -> {
                    callRecursive(nS to p.pF)
                    callRecursive(nS to p.pA)
                }
                is ParserF.ApL<I, E, Any?, *> -> {
                    callRecursive(nS to p.pA)
                    callRecursive(nS to p.pB)
                }
                is ParserF.ApR<I, E, *, Any?> -> {
                    callRecursive(nS to p.pA)
                    callRecursive(nS to p.pB)
                }
                is ParserF.Alt -> {
                    callRecursive(nS to p.left)
                    callRecursive(nS to p.right)
                }
                is ParserF.Select<I, E, *, Any?> -> {
                    callRecursive(nS to p.pEither)
                    callRecursive(nS to p.pIfLeft)
                }
                is ParserF.LookAhead -> {
                    callRecursive(nS to p.p)
                }
                is ParserF.NegLookAhead -> {
                    callRecursive(nS to p.p)
                }
                is ParserF.Attempt -> {
                    callRecursive(nS to p.p)
                }
                is ParserF.Lazy -> {
                    callRecursive(nS to p.f.invoke())
                }
                is ParserF.Many<I, E, Any?> -> {
                    callRecursive(nS to p.p)
                }
                is ParserF.ConcatString -> {
                    callRecursive(nS to p.p.unsafe())
                }
                else -> Unit
            }
        }
    }(persistentHashSetOf<ParserF<I, E, Any?>>() to parserF)
    return Pair(refCount.filter { (_, v) -> v > 1 }.keys, recursives)
}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> Parser<I, E, A>.insertLets(
    letBound: Set<ParserF<I, E, Any?>>,
    recursive: Set<ParserF<I, E, Any?>>
): Triple<Parser<I, E, A>, Map<Int, Parser<I, E, Any?>>, Int> {
    var labelC = 0
    val subParsers = mutableMapOf<Int, Parser<I, E, Any?>>()
    val handled = mutableMapOf<ParserF<I, E, Any?>, Int>()

    val nP = DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        val bound = letBound.contains(p)
        val rec = recursive.contains(p)

        suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.new(): Parser<I, E, Any?> =
            when (p) {
                is ParserF.Ap<I, E, *, Any?> -> {
                    val l = callRecursive(p.pF)
                    val r = callRecursive(p.pA)
                    ParserF.Ap(l.unsafe(), r)
                }
                is ParserF.ApL<I, E, Any?, *> -> {
                    val l = callRecursive(p.pA)
                    val r = callRecursive(p.pB)
                    ParserF.ApL(l, r)
                }
                is ParserF.ApR<I, E, *, Any?> -> {
                    val l = callRecursive(p.pA)
                    val r = callRecursive(p.pB)
                    ParserF.ApR(l, r)
                }
                is ParserF.Alt -> {
                    val l = callRecursive(p.left)
                    val r = callRecursive(p.right)
                    ParserF.Alt(l, r)
                }
                is ParserF.Select<I, E, *, Any?> -> {
                    val e = callRecursive(p.pEither)
                    val l = callRecursive(p.pIfLeft)
                    ParserF.Select(
                        e.unsafe<ParserF<I, E, Either<Any?, Any?>>>(),
                        l.unsafe()
                    )
                }
                is ParserF.LookAhead -> {
                    ParserF.LookAhead(callRecursive(p.p))
                }
                is ParserF.NegLookAhead -> {
                    ParserF.NegLookAhead(callRecursive(p.p))
                }
                is ParserF.Attempt -> {
                    ParserF.Attempt(callRecursive(p.p))
                }
                is ParserF.ConcatString -> {
                    ParserF.ConcatString(callRecursive(p.p.unsafe()).unsafe<ParserF<Char, E, List<Char>>>()).unsafe()
                }
                is ParserF.Lazy -> {
                    if (handled.containsKey(p).not())
                        callRecursive(p.f.invoke())
                    else p
                }
                is ParserF.Many<I, E, Any?> -> {
                    ParserF.Many(callRecursive(p.p))
                }
                else -> p
            }.let(::Parser)

        fun <I, E, A> ParserF<I, E, A>.small(): Boolean = when (this) {
            is ParserF.Pure, is ParserF.Satisfy<*>, is ParserF.Single<*>, is ParserF.Empty -> true
            is ParserF.Ap<I, E, *, A> -> pF.small() && pA.small()
            is ParserF.ApR<I, E, *, A> -> pA.small() && pB.small()
            is ParserF.ApL<I, E, A, *> -> pA.small() && pB.small()
            else -> false
        }

        if (bound && !p.small()) {
            val newLabel =
                if (handled.containsKey(p)) handled[p]!!
                else {
                    labelC++.also {
                        handled[p] = it
                        subParsers[it] = new()
                    }
                }
            return@DeepRecursiveFunction ParserF.Let(rec, newLabel)
        } else new().parserF
    }(parserF).let(::Parser)

    return Triple(nP.unsafe(), subParsers, labelC)
}