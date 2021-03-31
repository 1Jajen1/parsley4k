package parsley.backend.instructions

import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.unsafe

class ByteListToArr<E> : Instruction<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val bytes = machine.pop().unsafe<List<Byte>>()
        machine.push(bytes.toByteArray())
    }

    override fun toString(): String = "ByteListToArr"
}

class ByteArrToByteList<E> : Instruction<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val bytes = machine.pop().unsafe<ByteArray>()
        machine.push(bytes.toList())
    }

    override fun toString(): String = "ByteArrToByteList"
}
