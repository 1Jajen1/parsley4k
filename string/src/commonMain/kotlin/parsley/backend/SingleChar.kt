package parsley.backend

import parsley.CharTokensT
import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.ParseErrorT
import parsley.StringStackMachine
import parsley.unsafe

// TODO Add warning after compiling that this boxes
class SingleChar<E>(val c: Char) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
                machine.push(el)
            } else {
                unexpected.head = el
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SingleChar($c)"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SingleChar_<E>(val c: Char) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else {
                unexpected.head = el
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SingleChar_($c)"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SingleCharMany<E>(val c: Char) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else {
                machine.push(machine.takeP(start, machine.inputOffset).concatToString())
                return
            }
        }
    }

    override fun toString(): String = "SingleCharMany($c)"
}

class SingleCharMany_<E>(val c: Char) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        while (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else return
        }
    }

    override fun toString(): String = "SingleCharMany_($c)"
}

class CharEof<E> : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            unexpected.head = machine.takeP()
            machine.failWith(error)
        }
    }

    override fun toString(): String = "CharEof"

    private val unexpected = CharTokensT(' ', charArrayOf())
    private val error = ParseErrorT<Char, E>(-1, unexpected, setOf(ErrorItem.EndOfInput), emptySet())
}
