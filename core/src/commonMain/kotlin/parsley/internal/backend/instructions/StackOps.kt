package parsley.internal.backend.instructions

import parsley.internal.backend.Instruction
import parsley.internal.backend.Pops
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.unsafe

internal class Push<I, E>(internal val el: Any?) : Instruction<I, E>, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        machine.dataStack.push(el)
    }

    override fun pushes(): Int = 1

    override fun toString(): String = "Push($el)"
}

internal class Pop<I, E> : Instruction<I, E>, Pops {
    override fun apply(machine: StackMachine<I, E>) {
        machine.dataStack.pop()
    }

    override fun pops(): Int = 1

    override fun toString(): String = "Pop"
}

internal class Flip<I, E> : Instruction<I, E>, Pops, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        val a = machine.dataStack.pop()
        val b = machine.dataStack.pop()
        machine.dataStack.push(a)
        machine.dataStack.push(b)
    }

    override fun pushes(): Int = 2
    override fun pops(): Int = 2

    override fun toString(): String = "Flip"
}

internal class Apply<I, E> : Instruction<I, E>, Pops, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        val a = machine.dataStack.pop()
        val f = machine.dataStack.pop().unsafe<(Any?) -> Any?>()
        machine.dataStack.push(f(a))
    }

    override fun pops(): Int = 2
    override fun pushes(): Int = 1

    override fun toString(): String = "Apply"
}

internal class Map<I, E>(val f: (Any?) -> Any?) : Instruction<I, E>, Pops, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        val a = machine.dataStack.pop()
        machine.dataStack.push(f(a))
    }

    override fun pops(): Int = 1
    override fun pushes(): Int = 1

    override fun toString(): String = "Map"
}
