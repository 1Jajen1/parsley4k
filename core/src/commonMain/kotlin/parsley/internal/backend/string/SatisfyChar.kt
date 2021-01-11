package parsley.internal.backend.string

import parsley.ErrorItem
import parsley.internal.backend.CanFail
import parsley.internal.backend.Consumes
import parsley.internal.backend.FuseMap
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.backend.instructions.SatisfyMap
import parsley.internal.frontend.CharPredicate
import parsley.internal.unsafe

internal class SatisfyChar<E>(
    val f: CharPredicate,
    val expected: Set<ErrorItem<Char>>
) : Instruction<Char, E>, CanFail<Char, E>, Pushes, Consumes, FuseMap<Char, E> {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val i = machine.takeP()
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

    override fun toString(): String = "SatisfyChar"
    override fun pushes(): Int = 1
    override fun consumes(): Int = 1
    override fun fuseWith(f: (Any?) -> Any?): Instruction<Char, E> = SatisfyMap(this.f, f.unsafe(), expected)
}

internal class SatisfyChar_<E>(
    val f: CharPredicate,
    expected: Set<ErrorItem<Char>>
) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val i = machine.takeP()
            if (f(i)) {
                machine.consume()
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "SatisfyChar_"
    override fun consumes(): Int = 1
}

internal class JumpTableChar<E>(val to: MutableMap<Char, Int>) : Instruction<Char, E> {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            machine.takeP().let { i -> to[i] }?.let { machine.jump(it) } ?: machine.failWith()
        } else {
            // TODO This needs some thoughts. This is currently bugged if the fallback tries to consume
            machine.failWith()
        }
    }

    override fun toString(): String = "JumpTableChar($to)"
}

internal class SatisfyCharMap<E>(
    val f: CharPredicate,
    val fa: (Char) -> Any?,
    val expected: Set<ErrorItem<Char>>
) : Instruction<Char, E>, CanFail<Char, E>, Pushes, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val i = machine.takeP()
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

    override fun toString(): String = "SatisfyCharMap"
    override fun pushes(): Int = 1
    override fun consumes(): Int = 1
}
