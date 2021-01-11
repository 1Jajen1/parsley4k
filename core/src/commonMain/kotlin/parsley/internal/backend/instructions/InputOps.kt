package parsley.internal.backend.instructions

import parsley.ErrorItem
import parsley.ParseError
import parsley.internal.backend.CanFail
import parsley.internal.backend.Consumes
import parsley.internal.backend.FuseMap
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.frontend.Predicate
import parsley.internal.unsafe

internal class Satisfy<I, E>(
    val f: Predicate<I>,
    val expected: Set<ErrorItem<I>>
) : Instruction<I, E>, CanFail<I, E>, Pushes, Consumes, FuseMap<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.dataStack.push(i)
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "Satisfy"
    override fun pushes(): Int = 1
    override fun consumes(): Int = 1
    override fun fuseWith(f: (Any?) -> Any?): Instruction<I, E> = SatisfyMap(this.f, f.unsafe(), expected)
}

internal class SatisfyDiscard<I, E>(
    val f: Predicate<I>,
    expected: Set<ErrorItem<I>>
) : Instruction<I, E>, CanFail<I, E>, Consumes {
    override fun apply(machine: StackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "SatisfyDiscard"
    override fun consumes(): Int = 1
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

internal class JumpTable<I, E>(val to: MutableMap<I, Int>) : Instruction<I, E> {
    override fun apply(machine: StackMachine<I, E>) {
        if (machine.hasMore()) {
            machine.take().let { i -> to[i] }?.let { machine.jump(it) } ?: machine.failWith()
        } else {
            // TODO This needs some thoughts. This is currently bugged if the fallback tries to consume
            machine.failWith()
        }
    }

    override fun toString(): String = "JumpTable($to)"
}

internal class SatisfyMap<I, E>(
    val f: Predicate<I>,
    val fa: (I) -> Any?,
    val expected: Set<ErrorItem<I>>
) : Instruction<I, E>, CanFail<I, E>, Pushes, Consumes {
    override fun apply(machine: StackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.dataStack.push(fa(i))
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "SatisfyMap"
    override fun pushes(): Int = 1
    override fun consumes(): Int = 1
}
