package parsley.backend.instructions

import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.unsafe

class CharListToString<E> : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val chars = machine.pop().unsafe<List<Char>>()
        machine.push(chars.toCharArray().concatToString())
    }

    override fun toString(): String = "CharListToString"
}

class StringToCharList<E> : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val chars = machine.pop().unsafe<String>()
        machine.push(chars.toList())
    }

    override fun toString(): String = "StringToCharList"
}
