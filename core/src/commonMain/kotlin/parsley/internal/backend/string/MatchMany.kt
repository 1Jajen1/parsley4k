package parsley.internal.backend.string

import parsley.internal.backend.CanFail
import parsley.internal.backend.Consumes
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.unsafe

internal class MatchManyChars<E>(val c: Char) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val s = machine.takeP()
            if (s == c) {
                machine.consume()
            } else {
                machine.dataStack.push(machine.substring(start))
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "MatchManyChars($c)"
    override fun consumes(): Int = 1
    override fun pushes(): Int = 1
}

internal class MatchManyChars_<E>(val c: Char) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        while (machine.hasMore()) {
            val s = machine.takeP()
            if (s == c) {
                machine.consume()
            } else {
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "MatchManyChars_($c)"
    override fun consumes(): Int = 1
}

internal class MatchManyCharsOf<E>(val c: CharArray) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val s = machine.takeP()
            if (c.indexOf(s) != -1) {
                machine.consume()
            } else {
                machine.dataStack.push(machine.substring(start))
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "MatchManyCharsOf(${c.toSet()})"
    override fun consumes(): Int = 1
    override fun pushes(): Int = 1
}

internal class MatchManyCharsOf_<E>(val c: CharArray) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        while (machine.hasMore()) {
            val s = machine.takeP()
            if (c.indexOf(s) != -1) {
                machine.consume()
            } else {
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "MatchManyCharsOf_()"// ${c.toSet()}
    override fun consumes(): Int = 1
}

internal class MatchManyCharsIn<E>(val c: CharRange) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val s = machine.takeP()
            if (s in c) {
                machine.consume()
            } else {
                machine.dataStack.push(machine.substring(start))
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "MatchManyCharsIn(${c})"
    override fun consumes(): Int = 1
    override fun pushes(): Int = 1
}

internal class MatchManyCharsIn_<E>(val c: CharRange) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        while (machine.hasMore()) {
            val s = machine.takeP()
            if (s in c) {
                machine.consume()
            } else {
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "MatchManyCharsOf_(${c})"
    override fun consumes(): Int = 1
}
