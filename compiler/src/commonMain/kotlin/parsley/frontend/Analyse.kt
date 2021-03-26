package parsley.frontend

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import parsley.Either
import parsley.collections.IntMap
import parsley.unsafe

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.findLetBound(
    letsteps: Array<LetBoundStep<I, E>>
): Pair<Set<ParserF<I, E, Any?>>, Set<ParserF<I, E, Any?>>> {
    val refCount = mutableMapOf<ParserF<I, E, Any?>, Int>()
    val recursives = mutableSetOf<ParserF<I, E, Any?>>()

    DeepRecursiveFunction<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit> { (seen, p) ->
        letsteps.forEach { s ->
            s.run {
                if (step(p, seen, refCount, recursives)) return@DeepRecursiveFunction
            }
        }
    }(persistentHashSetOf<ParserF<I, E, Any?>>() to this)
    return Pair(refCount.filter { (_, v) -> v > 1 }.keys, recursives)
}

interface LetBoundStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.step(
        p: ParserF<I, E, Any?>,
        seen: PersistentSet<ParserF<I, E, Any?>>,
        refCount: MutableMap<ParserF<I, E, Any?>, Int>,
        recursives: MutableSet<ParserF<I, E, Any?>>
    ): Boolean

    companion object
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <I, E> LetBoundStep.Companion.fallthrough(crossinline f: suspend DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.(p: ParserF<I, E, Any?>, PersistentSet<ParserF<I, E, Any?>>) -> Boolean) =
    object : LetBoundStep<I, E> {
        override suspend fun DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.step(
            p: ParserF<I, E, Any?>,
            seen: PersistentSet<ParserF<I, E, Any?>>,
            refCount: MutableMap<ParserF<I, E, Any?>, Int>,
            recursives: MutableSet<ParserF<I, E, Any?>>
        ): Boolean {
            refCount[p].also { refCount[p] = it?.let { it + 1 } ?: 1 }

            if (seen.contains(p)) {
                recursives.add(p)
                return true
            }

            if (refCount[p]!! == 1) {
                val nS = seen.add(p)
                return f(p, nS)
            }

            return false
        }
    }

class DefaultLetBoundStep<I, E> : LetBoundStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<Pair<PersistentSet<ParserF<I, E, Any?>>, ParserF<I, E, Any?>>, Unit>.step(
        p: ParserF<I, E, Any?>,
        seen: PersistentSet<ParserF<I, E, Any?>>,
        refCount: MutableMap<ParserF<I, E, Any?>, Int>,
        recursives: MutableSet<ParserF<I, E, Any?>>
    ): Boolean {
        refCount[p].also { refCount[p] = it?.let { it + 1 } ?: 1 }

        if (seen.contains(p)) {
            recursives.add(p)
            return true
        }

        if (refCount[p]!! == 1) {
            val nS = seen.add(p)
            when (p) {
                is Ap<I, E, *, Any?> -> {
                    callRecursive(nS to p.pF)
                    callRecursive(nS to p.pA)
                }
                is ApL<I, E, Any?, *> -> {
                    callRecursive(nS to p.pA)
                    callRecursive(nS to p.pB)
                }
                is ApR<I, E, *, Any?> -> {
                    callRecursive(nS to p.pA)
                    callRecursive(nS to p.pB)
                }
                is Alt -> {
                    callRecursive(nS to p.left)
                    callRecursive(nS to p.right)
                }
                is Select<I, E, *, Any?> -> {
                    callRecursive(nS to p.pEither)
                    callRecursive(nS to p.pIfLeft)
                }
                is LookAhead -> {
                    callRecursive(nS to p.p)
                }
                is NegLookAhead -> {
                    callRecursive(nS to p.p)
                }
                is Attempt -> {
                    callRecursive(nS to p.p)
                }
                is Lazy -> {
                    callRecursive(nS to p.f.invoke())
                }
                is Many<I, E, Any?> -> {
                    callRecursive(nS to p.p)
                }
                is ChunkOf<I, E, Any?> -> {
                    callRecursive(nS to p.p)
                }
                is Label -> {
                    callRecursive(nS to p.p)
                }
                else -> Unit
            }
        }
        return true
    }

}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> ParserF<I, E, A>.insertLets(
    letBound: Set<ParserF<I, E, Any?>>,
    recursive: Set<ParserF<I, E, Any?>>,
    insertLetSteps: Array<InsertLetStep<I, E>>
): Triple<ParserF<I, E, A>, IntMap<ParserF<I, E, Any?>>, Int> {
    var labelC = 0
    val subParsers = IntMap.empty<ParserF<I, E, Any?>>()
    val handled = mutableMapOf<ParserF<I, E, Any?>, Int>()

    val nP = DeepRecursiveFunction<ParserF<I, E, Any?>, ParserF<I, E, Any?>> { p ->
        insertLetSteps.forEach { s ->
            s.run {
                val res = step(p, letBound, recursive, subParsers, handled) { labelC++ }
                if (res != null) return@DeepRecursiveFunction res
            }
        }
        throw IllegalStateException("Let insertion did not provide a result")
    }(this)

    return Triple(nP.unsafe(), subParsers, labelC)
}

interface InsertLetStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        letBound: Set<ParserF<I, E, Any?>>,
        recursive: Set<ParserF<I, E, Any?>>,
        subParsers: IntMap<ParserF<I, E, Any?>>,
        handled: MutableMap<ParserF<I, E, Any?>, Int>,
        mkLabel: () -> Int
    ): ParserF<I, E, Any?>?

    companion object
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <I, E> InsertLetStep.Companion.fallthrough(
    crossinline f: suspend DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.(ParserF<I, E, Any?>) -> ParserF<I, E, Any?>?,
    crossinline isSmall: (ParserF<I, E, Any?>) -> Boolean
): InsertLetStep<I, E> =
    object : InsertLetStep<I, E> {
        override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
            p: ParserF<I, E, Any?>,
            letBound: Set<ParserF<I, E, Any?>>,
            recursive: Set<ParserF<I, E, Any?>>,
            subParsers: IntMap<ParserF<I, E, Any?>>,
            handled: MutableMap<ParserF<I, E, Any?>, Int>,
            mkLabel: () -> Int
        ): ParserF<I, E, Any?>? {
            val bound = letBound.contains(p)
            val rec = recursive.contains(p)

            return if (bound && !isSmall(p)) {
                val newLabel =
                    if (handled.containsKey(p)) handled[p]!!
                    else {
                        mkLabel().also {
                            handled[p] = it
                            subParsers[it] = f(p) ?: return null
                        }
                    }
                Let(rec, newLabel)
            } else f(p)
        }
    }

class DefaultInsertLetStep<I, E> : InsertLetStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        letBound: Set<ParserF<I, E, Any?>>,
        recursive: Set<ParserF<I, E, Any?>>,
        subParsers: IntMap<ParserF<I, E, Any?>>,
        handled: MutableMap<ParserF<I, E, Any?>, Int>,
        mkLabel: () -> Int
    ): ParserF<I, E, Any?>? {
        val bound = letBound.contains(p)
        val rec = recursive.contains(p)

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun <I, E> DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.new(
            p: ParserF<I, E, Any?>,
            handled: MutableMap<ParserF<I, E, Any?>, Int>
        ): ParserF<I, E, Any?> =
            when (p) {
                is Ap<I, E, *, Any?> -> {
                    val l = callRecursive(p.pF)
                    val r = callRecursive(p.pA)
                    Ap(l.unsafe(), r)
                }
                is ApL<I, E, Any?, *> -> {
                    val l = callRecursive(p.pA)
                    val r = callRecursive(p.pB)
                    ApL(l, r)
                }
                is ApR<I, E, *, Any?> -> {
                    val l = callRecursive(p.pA)
                    val r = callRecursive(p.pB)
                    ApR(l, r)
                }
                is Alt -> {
                    val l = callRecursive(p.left)
                    val r = callRecursive(p.right)
                    Alt(l, r)
                }
                is Select<I, E, *, Any?> -> {
                    val e = callRecursive(p.pEither)
                    val l = callRecursive(p.pIfLeft)
                    Select(
                        e.unsafe<ParserF<I, E, Either<Any?, Any?>>>(),
                        l.unsafe()
                    )
                }
                is LookAhead -> {
                    LookAhead(callRecursive(p.p))
                }
                is NegLookAhead -> {
                    NegLookAhead(callRecursive(p.p))
                }
                is Attempt -> {
                    Attempt(callRecursive(p.p))
                }
                is Lazy -> {
                    if (handled.containsKey(p).not())
                        callRecursive(p.f.invoke())
                    else p
                }
                is Many<I, E, Any?> -> {
                    Many(callRecursive(p.p))
                }
                is ChunkOf<I, E, Any?> -> {
                    ChunkOf(callRecursive(p.p))
                }
                is Label -> {
                    Label(p.label, callRecursive(p.p))
                }
                else -> p
            }

        return if (bound && !p.small()) {
            val newLabel =
                if (handled.containsKey(p)) handled[p]!!
                else {
                    mkLabel().also {
                        handled[p] = it
                        subParsers[it] = new(p, handled)
                    }
                }
            Let(rec, newLabel)
        } else new(p, handled)
    }
}

fun <I, E, A> ParserF<I, E, A>.small(): Boolean = when (this) {
    is Pure, is Satisfy<*>, is Single<*>, Empty, is Label -> true
    is Ap<I, E, *, A> -> pF.small() && pA.small()
    is ApR<I, E, *, A> -> pA.small() && pB.small()
    is ApL<I, E, A, *> -> pA.small() && pB.small()
    else -> false
}
