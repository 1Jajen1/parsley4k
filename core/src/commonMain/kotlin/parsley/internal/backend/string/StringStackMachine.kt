package parsley.internal.backend.string

import parsley.ErrorItem
import parsley.ParseError
import parsley.internal.backend.CanFail
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine

internal class StringStackMachine<E>(
    instr: Array<Instruction<Char, E>>
) : StackMachine<Char, E>(instr) {
    internal var input: CharArray = charArrayOf()

    override fun consume() {
        inputOffset += 1
    }

    override fun hasMore(): Boolean {
        return input.size > inputOffset
    }

    override fun take(): Char {
        return input[inputOffset]
    }

    fun takeP(): Char = input[inputOffset]
}

internal class MatchChar<E>(val c: Char) : Instruction<Char, E>, Pushes, CanFail<Char, E> {
    override var error: ParseError<Char, E> = ParseError.Trivial(expected = setOf(ErrorItem.Tokens(c)), offset = -1)

    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine as StringStackMachine
        if (machine.hasMore()) {
            val i = machine.takeP()
            if (i == c) {
                machine.consume()
                machine.dataStack.push(i)
            } else {
                machine.failWith(error)
            }
        } else {
            val expected = if (error is ParseError.Trivial) (error as ParseError.Trivial<Char>).expected else emptySet()
            machine.status = ParseStatus.NeedInput(expected)
        }
    }

    override fun pushes(): Int = 1

    override fun toString(): String = "MatchChar($c)"
}
