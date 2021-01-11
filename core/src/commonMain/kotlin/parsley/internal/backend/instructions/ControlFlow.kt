package parsley.internal.backend.instructions

import arrow.Either
import parsley.ErrorItem
import parsley.ParseError
import parsley.internal.backend.CanFail
import parsley.internal.backend.Handler
import parsley.internal.backend.Instruction
import parsley.internal.backend.Jumps
import parsley.internal.backend.StackMachine
import parsley.internal.unsafe

internal class JumpOnFail<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: StackMachine<I, E>) {
        machine.handlerStack.push(
            JumpOnFailHandler(
                machine.inputOffset,
                machine.dataStack.size(),
                machine.returnStack.size(),
                to
            )
        )
    }

    override fun toString(): String = "JumpOnFail($to)"
}

internal class JumpOnFailHandler<I, E>(
    val offset: Int,
    val stackOffset: Int,
    val retStackOffset: Int,
    val to: Int
) : Handler<I, E> {
    override fun onFail(machine: StackMachine<I, E>) {
        if (machine.inputOffset != offset) {
            return machine.failWith()
        }
        machine.dataStack.setOffset(stackOffset)
        machine.returnStack.setOffset(retStackOffset)
        machine.jump(to)
    }
}

internal class Call<I, E>(val recursive: Boolean, override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: StackMachine<I, E>) {
        machine.call(to)
    }

    override fun toString(): String = "Call($recursive, $to)"
}

internal class ResetOffset<I, E> : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.handlerStack.push(ResetOffsetHandler(machine.inputOffset))
    }

    override fun toString(): String = "Attempt"
}

internal class ResetOffsetHandler<I, E>(val offset: Int) : Handler<I, E> {
    override fun onFail(machine: StackMachine<I, E>) {
        machine.inputOffset = offset
        machine.failWith()
    }
}

internal class PopHandler<I, E> : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.handlerStack.pop().onRemove(machine)
    }

    override fun toString(): String = "PopHandler"
}

internal class Label<I, E>(val nr: Int) : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {}

    override fun toString(): String = "Label($nr)"
}

internal class Jump<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: StackMachine<I, E>) {
        machine.jump(to)
    }

    override fun toString(): String = "Jump($to)"
}

internal class Fail<I, E> : Instruction<I, E>, CanFail<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.failWith()
    }

    override fun toString(): String = "Fail"
}

internal class JumpOnFailAndFailOnSuccess<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: StackMachine<I, E>) {
        machine.handlerStack.push(
            JumpOnFailAndFailOnSuccessHandler(
                machine.inputOffset,
                machine.dataStack.size(),
                machine.returnStack.size() + 1,
                to
            )
        )
    }

    override fun toString(): String = "JumpOnFailAndFailOnSuccess($to)"
}

internal class JumpOnFailAndFailOnSuccessHandler<I, E>(
    val offset: Int,
    val stackOffset: Int,
    val retStackOffset: Int,
    val to: Int
) : Handler<I, E> {
    override fun onFail(machine: StackMachine<I, E>) {
        machine.dataStack.setOffset(stackOffset)
        machine.returnStack.setOffset(retStackOffset)

        machine.inputOffset = offset
        machine.jump(to)
    }

    override fun onRemove(machine: StackMachine<I, E>) {
        machine.failWith()
    }
}

internal class JumpOnRight<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: StackMachine<I, E>) {
        val top = machine.dataStack.pop().unsafe<Either<Any?, Any?>>()
        top.fold({
            machine.dataStack.push(it)
        }, {
            machine.dataStack.push(it)
            machine.jump(to)
        })
    }

    override fun toString(): String = "JumpOnRight($to)"
}

internal class Exit<I, E> : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.exit()
    }

    override fun toString(): String = "Exit"
}

internal class End<I, E> : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.jump(Int.MAX_VALUE)
    }

    override fun toString(): String = "End"
}

// Variant of JumpOnFail that always succeeds in the second branch
internal class JumpOnFailPure<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: StackMachine<I, E>) {
        machine.handlerStack.push(
            JumpOnFailPureHandler(
                machine.dataStack.size(),
                machine.returnStack.size(),
                to
            )
        )
    }

    override fun toString(): String = "JumpOnFailPure($to)"
}

internal class JumpOnFailPureHandler<I, E>(
    val stackOffset: Int,
    val retStackOffset: Int,
    val to: Int
) : Handler<I, E> {
    override fun onFail(machine: StackMachine<I, E>) {
        machine.dataStack.setOffset(stackOffset)
        machine.returnStack.setOffset(retStackOffset)

        machine.jump(to)
    }
}
