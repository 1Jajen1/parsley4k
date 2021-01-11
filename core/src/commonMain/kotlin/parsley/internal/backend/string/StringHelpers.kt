package parsley.internal.backend.string

import parsley.internal.backend.Instruction
import parsley.internal.backend.Pops
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.unsafe

internal class StringToCharList<E> : Instruction<Char, E>, Pushes, Pops {
    override fun apply(machine: StackMachine<Char, E>) {
        val str = machine.dataStack.pop().unsafe<String>()
        machine.dataStack.push(str.toList())
    }

    override fun toString(): String = "StringToCharList"
    override fun pops(): Int = 1
    override fun pushes(): Int = 1
}

internal class CharListToString<E> : Instruction<Char, E>, Pushes, Pops {
    override fun apply(machine: StackMachine<Char, E>) {
        val str = machine.dataStack.pop().unsafe<List<Char>>()
        machine.dataStack.push(str.toCharArray().concatToString())
    }

    override fun toString(): String = "CharListToString"
    override fun pops(): Int = 1
    override fun pushes(): Int = 1
}
