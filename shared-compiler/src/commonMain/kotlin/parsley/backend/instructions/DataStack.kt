package parsley.backend.instructions

import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.unsafe

class Push<I, E>(val el: Any?): Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        machine.push(el)
    }

    override fun toString(): String = "Push($el)"
}

class Pop<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        machine.drop()
    }

    override fun toString(): String = "Pop"
}

class Flip<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val a = machine.pop()
        val b = machine.peek()
        machine.push(a)
        machine.exchange(b)
    }

    override fun toString(): String = "Flip"
}

class Map<I, E>(val f: (Any?) -> Any?): Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val top = machine.peek()
        machine.exchange(f(top))
    }

    override fun toString(): String = "Map"
}

class Apply<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val a = machine.pop()
        val f = machine.peek().unsafe<(Any?) -> Any?>()
        machine.exchange(f(a))
    }

    override fun toString(): String = "Apply"
}

class MkPair<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val b = machine.pop()
        val a = machine.peek()
        machine.exchange(a to b)
    }

    override fun toString(): String = "MkPair"
}
