package parsley.backend.instructions

import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.backend.Jumps
import parsley.backend.ParseStatus

class PushHandler<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        machine.pushHandler(to)
    }

    override fun toString(): String = "PushHandler($to)"
}

class InputCheck<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        machine.inputCheckStack.push(machine.inputOffset)
        machine.pushHandler(to)
    }

    override fun toString(): String = "InputCheck($to)"
}

class Catch<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val checkOff = machine.inputCheckStack.pop()
        if (machine.status == ParseStatus.Err) {
            if (checkOff == machine.inputOffset) {
                machine.status = ParseStatus.Ok
            } else machine.fail()
        } else {
            machine.handlerStack.drop()
        }
    }

    override fun toString(): String = "Catch"
}

class JumpGood<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        machine.handlerStack.drop()
        machine.inputCheckStack.drop()
        machine.jump(to)
    }

    override fun toString(): String = "JumpGood($to)"
}

class ResetOffsetOnFail<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val off = machine.inputCheckStack.pop()
        if (machine.status == ParseStatus.Err) {
            machine.inputOffset = off
            machine.fail()
        } else {
            machine.handlerStack.drop()
        }
    }

    override fun toString(): String = "ResetOffsetOnFail"
}

class ResetOffset<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val off = machine.inputCheckStack.pop()
        machine.inputOffset = off
        if (machine.status == ParseStatus.Err) machine.fail()
        else machine.handlerStack.drop()
    }

    override fun toString(): String = "ResetOffset"
}

class ResetOnFailAndFailOnOk<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val off = machine.inputCheckStack.pop()
        // TODO double check
        machine.inputOffset = off
        if (machine.status == ParseStatus.Err) {
            machine.status = ParseStatus.Ok
        } else {
            machine.handlerStack.drop()
            machine.fail()
        }
    }

    override fun toString(): String = "ResetOnFailAndFailOnOk"
}

// Optimised. Instructions that can be represented by a combination of the primitives but are fused/faster
class RecoverWith<I, E>(val el: Any?) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val checkOff = machine.inputCheckStack.pop()
        if (machine.status == ParseStatus.Err) {
            if (checkOff == machine.inputOffset) {
                machine.status = ParseStatus.Ok
                machine.push(el)
            } else machine.fail()
        } else {
            machine.handlerStack.drop()
        }
    }

    override fun toString(): String = "RecoverWith($el)"
}

class JumpGoodAttempt<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val checkOff = machine.inputCheckStack.pop()
        if (machine.status == ParseStatus.Err) {
            machine.status = ParseStatus.Ok
            machine.inputOffset = checkOff
        } else {
            machine.handlerStack.drop()
            machine.jump(to)
        }
    }

    override fun toString(): String = "JumpGoodAttempt($to)"
}

class RecoverAttemptWith<I, E>(val el: Any?) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val checkOff = machine.inputCheckStack.pop()
        if (machine.status == ParseStatus.Err) {
            machine.status = ParseStatus.Ok
            machine.inputOffset = checkOff
            machine.push(el)
        } else {
            machine.handlerStack.drop()
        }
    }

    override fun toString(): String = "RecoverAttemptWith($el)"
}
