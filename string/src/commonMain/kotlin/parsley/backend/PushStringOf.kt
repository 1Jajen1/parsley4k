package parsley.backend

import parsley.StringStackMachine
import parsley.unsafe

class PushStringOf<E> : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.status == ParseStatus.Err) {
            machine.inputCheckStack.drop()
            machine.fail()
        } else {
            machine.handlerStack.drop()
            val start = machine.inputCheckStack.pop()
            val end = machine.inputOffset
            machine.push(machine.takeP(start, end).concatToString())
        }
    }

    override fun toString(): String = "PushStringOf"
}