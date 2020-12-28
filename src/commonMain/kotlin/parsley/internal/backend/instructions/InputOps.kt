package parsley.internal.backend.instructions

import parsley.ErrorItem
import parsley.ParseError
import parsley.internal.backend.CanFail
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.StackMachine

internal class Satisfy<I, E>(
    val f: (I) -> Boolean,
    expected: Set<ErrorItem<I>>
) : Instruction<I, E>, CanFail<I, E> {
    override var error: ParseError<I, E> = ParseError.Trivial(expected = expected, offset = -1)
    override fun apply(machine: StackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.dataStack.push(i)
            } else {
                if (error is ParseError.Trivial)
                    error = (error as ParseError.Trivial<I>).copy(unexpected = ErrorItem.Tokens(i))
                error.offset = machine.inputOffset
                machine.failWith(error)
            }
        } else {
            val expected = if (error is ParseError.Trivial) (error as ParseError.Trivial<I>).expected else emptySet()
            machine.status = ParseStatus.NeedInput(expected)
        }
    }

    override fun toString(): String = "Satisfy"
}

internal class Satisfy_<I, E>(
    val f: (I) -> Boolean,
    expected: Set<ErrorItem<I>>
) : Instruction<I, E>, CanFail<I, E> {
    override var error: ParseError<I, E> = ParseError.Trivial(expected = expected, offset = -1)
    override fun apply(machine: StackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
            } else {
                if (error is ParseError.Trivial)
                    error = (error as ParseError.Trivial<I>).copy(unexpected = ErrorItem.Tokens(i))
                error.offset = machine.inputOffset
                machine.failWith(error)
            }
        } else {
            val expected = if (error is ParseError.Trivial) (error as ParseError.Trivial<I>).expected else emptySet()
            machine.status = ParseStatus.NeedInput(expected)
        }
    }

    override fun toString(): String = "Satisfy_"
}

internal class Tell<I, E> : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.dataStack.push(machine.inputOffset)
    }

    override fun toString(): String = "Tell"
}

internal class Seek<I, E> : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        machine.inputOffset = machine.dataStack.pop() as Int
    }

    override fun toString(): String = "Seek"
}
