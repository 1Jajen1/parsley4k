package parsley.internal.backend.string

import parsley.internal.backend.CanFail
import parsley.internal.backend.Consumes
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.unsafe

internal class SingleChar<E>(val c: Char) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val i = machine.takeP()
            if (i == c) {
                machine.consume()
                machine.dataStack.push(i)
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "SingleChar($c)"
    override fun pushes(): Int = 1
    override fun consumes(): Int = 1
}

internal class SingleChar_<E>(val c: Char) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val i = machine.takeP()
            if (i == c) {
                machine.consume()
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "SingleChar_($c)"
    override fun consumes(): Int = 1
}

internal class MatchCharOf<E>(val c: CharArray) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val s = machine.takeP()
            if (c.indexOf(s) != -1) {
                machine.consume()
                machine.dataStack.push(s)
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "MatchCharOf(${c.toSet()})"
    override fun consumes(): Int = 1
    override fun pushes(): Int = 1
}

internal class MatchCharOf_<E>(val c: CharArray) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val s = machine.takeP()
            if (c.indexOf(s) != -1) {
                machine.consume()
            } else {
                machine.failWith()
                return
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "MatchCharOf_(${c.toSet()})"
    override fun consumes(): Int = 1
}

internal class MatchCharIn<E>(val c: CharRange) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val s = machine.takeP()
            if (s in c) {
                machine.consume()
                machine.dataStack.push(s)
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "MatchCharIn($c)"
    override fun consumes(): Int = 1
    override fun pushes(): Int = 1
}

internal class MatchCharIn_<E>(val c: CharRange) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val s = machine.takeP()
            if (s in c) {
                machine.consume()
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "MatchCharIn_($c)"
    override fun consumes(): Int = 1
}
