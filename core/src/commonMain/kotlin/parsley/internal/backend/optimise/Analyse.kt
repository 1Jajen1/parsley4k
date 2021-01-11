package parsley.internal.backend.optimise

import parsley.Parser
import parsley.internal.frontend.ParserF

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF.Alt<I, E, A>.toJumpTable(
    subs: Map<Int, Parser<I, E, A>>
): Pair<Map<I, List<ParserF<I, E, A>>>, List<ParserF<I, E, A>>> {
    val table = mutableMapOf<I, MutableList<ParserF<I, E, A>>>()
    val noLeading = mutableListOf<ParserF<I, E, A>>()

    DeepRecursiveFunction<ParserF<I, E, A>, Unit> { p ->
        when {
            p is ParserF.Alt -> {
                callRecursive(p.left)
                callRecursive(p.right)
            }
            p is ParserF.Let && subs[p.sub]!!.parserF is ParserF.Alt -> {
                val r = (subs[p.sub]!!.parserF as ParserF.Alt)
                callRecursive(r.left)
                callRecursive(r.right)
            }
            else -> {
                p.findLeading(subs)?.let { i ->
                    if (table.containsKey(i)) table[i]!!.add(p)
                    else table[i] = mutableListOf(p)
                } ?: noLeading.add(p)
            }
        }
    }(this)

    return table to noLeading
}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.findLeading(subs: Map<Int, Parser<I, E, A>>): I? =
    DeepRecursiveFunction<ParserF<I, E, Any?>, I?> { p ->
        when (p) {
            is ParserF.Ap<I, E, *, Any?> -> {
                callRecursive(p.pF)// ?: callRecursive(p.pA)
            }
            is ParserF.ApL<I, E, Any?, *> -> callRecursive(p.pA)
            is ParserF.ApR<I, E, *, Any?> -> callRecursive(p.pA)
            is ParserF.Attempt -> callRecursive(p.p)
            is ParserF.Many<I, E, Any?> -> callRecursive(p.p)
            is ParserF.LookAhead -> callRecursive(p.p)
            is ParserF.NegLookAhead -> callRecursive(p.p)
            is ParserF.Let -> callRecursive(subs[p.sub]!!.parserF)
            is ParserF.Single<*> -> p.i as I
            else -> null
        }
    }(this)

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.isCheap(): Boolean =
    DeepRecursiveFunction<ParserF<I, E, Any?>, Boolean> { p ->
        when (p) {
            is ParserF.Single<*> -> true
            is ParserF.Satisfy<*> -> true
            is ParserF.Pure -> true
            is ParserF.Empty -> true
            is ParserF.Ap<I, E, *, Any?> -> callRecursive(p.pF) && callRecursive(p.pA)
            is ParserF.ApR<I, E, *, Any?> -> callRecursive(p.pA) && callRecursive(p.pB)
            is ParserF.ApL<I, E, Any?, *> -> callRecursive(p.pA) && callRecursive(p.pB)
            else -> false
        }
    }(this)

