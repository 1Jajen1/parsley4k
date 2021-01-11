package parsley.internal.backend.string

import parsley.internal.backend.CanFail
import parsley.internal.backend.Consumes
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.unsafe

internal class MatchString<E>(val str: String) : Instruction<Char, E>, Pushes, CanFail<Char, E>, Consumes {
    val charArr = str.toCharArray()
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore(str.length)) {
            val s = machine.takeP(str.length)
            if (charArr.contentEquals(s)) {
                machine.dataStack.push(charArr.concatToString())
                machine.consume(str.length)
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "MatchString($str)"
    override fun consumes(): Int = str.length
    override fun pushes(): Int = str.length
}

internal class MatchString_<E>(val str: String) : Instruction<Char, E>, CanFail<Char, E>, Consumes {
    val charArr = str.toCharArray()
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore(str.length)) {
            val s = machine.takeP(str.length)
            if (charArr.contentEquals(s)) {
                machine.consume(str.length)
            } else {
                machine.failWith()
            }
        } else {
            machine.status = ParseStatus.NeedInput()
        }
    }

    override fun toString(): String = "MatchString_($str)"
    override fun consumes(): Int = str.length
}
