package parsley.frontend

import kotlinx.collections.immutable.PersistentSet
import parsley.AnyParser
import parsley.collections.IntMap

@OptIn(ExperimentalStdlibApi::class)
class LetFinderStep<I, E>(
    private val f: suspend DeepRecursiveScope<Pair<PersistentSet<AnyParser<I, E>>, AnyParser<I, E>>, Unit>.(
        p: AnyParser<I, E>,
        seen: PersistentSet<AnyParser<I, E>>,
        refCount: MutableMap<AnyParser<I, E>, Int>,
        recursives: MutableSet<AnyParser<I, E>>
    ) -> Boolean
) {
    operator fun component1() = f
}

@OptIn(ExperimentalStdlibApi::class)
class InsertLetStep<I, E>(
    private val f: suspend DeepRecursiveScope<AnyParser<I, E>, AnyParser<I, E>>.(
        p: AnyParser<I, E>,
        letBound: Set<AnyParser<I, E>>,
        recursive: Set<AnyParser<I, E>>,
        subParsers: IntMap<AnyParser<I, E>>,
        handled: MutableMap<AnyParser<I, E>, Int>,
        mkLabel: () -> Int
    ) -> AnyParser<I, E>?
) {
    operator fun component1() = f
}
