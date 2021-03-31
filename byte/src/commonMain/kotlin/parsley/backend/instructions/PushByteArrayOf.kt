package parsley.backend.instructions

import parsley.backend.ByteArrayStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.backend.ParseStatus
import parsley.unsafe

class PushByteArrayOf<E> : Instruction<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        if (machine.status == ParseStatus.Err) {
            machine.inputCheckStack.drop()
            machine.fail()
        } else {
            machine.handlerStack.drop()
            val start = machine.inputCheckStack.pop()
            val end = machine.inputOffset
            machine.push(machine.takeP(start, end))
        }
    }

    override fun toString(): String = "PushByteArrayOf"
}