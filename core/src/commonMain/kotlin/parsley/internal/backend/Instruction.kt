package parsley.internal.backend

internal interface Instruction<I, E> {
    fun apply(machine: StackMachine<I, E>): Unit
}

internal interface Handler<I, E> {
    fun onFail(machine: StackMachine<I, E>): Unit
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

}

internal interface Jumps {
    var to: Int
}

internal interface FuseMap<I, E> {
    fun fuseWith(f: (Any?) -> Any?): Instruction<I, E>
}
