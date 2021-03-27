package parsley.backend

import parsley.ErrorItemT
import parsley.ParseErrorT

interface Instruction<I, E> {
    fun apply(machine: AbstractStackMachine<I, E>)
}

// TODO This got a bit ugly
interface Jumps {
    var to: Int

    fun onAssembly(f: (Int) -> Int): Boolean = false
}

interface FuseMap<I, E> {
    fun fuse(f: (Any?) -> Any?): Instruction<I, E>
}

interface Errors<I, E> {
    var error: ParseErrorT<I, E>
}
