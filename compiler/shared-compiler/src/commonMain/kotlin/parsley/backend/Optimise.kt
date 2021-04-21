package parsley.backend

import parsley.collections.IntMap

class BackendOptimiseStep<I, E>(
    private val f: (Method<I, E>, IntMap<Method<I, E>>, mkLabel: () -> Int) -> Unit
) {
    operator fun component1() = f
}
