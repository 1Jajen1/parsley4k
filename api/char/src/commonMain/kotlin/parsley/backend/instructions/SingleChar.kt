package parsley.backend.instructions

import parsley.CharPredicate
import parsley.CharTokensT
import parsley.ErrorItem
import parsley.ParseErrorT
import parsley.backend.StringStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.unsafe
import kotlin.math.max
import kotlin.math.min

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

class SingleCharNoFail<E>(val c: Char) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            }
        } else machine.needInput(onFail = {})
    }

    override fun toString(): String = "SingleCharNoFail"
}

class SingleCharMany<E>(val c: Char) : Instruction<Char, E> {
    private var st: Int = Int.MAX_VALUE
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = min(st, machine.inputOffset).also { st = Int.MAX_VALUE }
        while (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else {
                machine.push(machine.takeP(start, machine.inputOffset).concatToString())
                return
            }
        }
        machine.needInput(
            onSuspend = { st = start },
            onFail = { machine.push(machine.takeP(start, machine.inputOffset).concatToString()) }
        )
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
        machine.needInput(onFail = {})
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
