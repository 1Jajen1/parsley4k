package parsley.internal.backend

import parsley.ParseError

internal interface Instruction<I, E> {
    fun apply(machine: StackMachine<I, E>): Unit
}

internal interface Handler<I, E> {
    fun onFail(machine: StackMachine<I, E>, error: ParseError<I, E>): Unit
    fun onRemove(machine: StackMachine<I, E>): Unit = Unit
}
// Meta interfaces
internal interface Consumes {
    fun consumes(): Int
}

internal interface Pushes {
    fun pushes(): Int
}

internal interface Pops {
    fun pops(): Int
}

internal interface CanFail<I, E> {
    var error: ParseError<I, E>
}

internal interface Jumps {
    var to: Int
}
