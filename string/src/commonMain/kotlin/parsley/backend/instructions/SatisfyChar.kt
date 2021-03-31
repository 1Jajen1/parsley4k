package parsley.backend.instructions

import parsley.CharFunc
import parsley.CharPredicate
import parsley.CharTokensT
import parsley.ParseErrorT
import parsley.backend.StringStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.unsafe
import kotlin.math.max
import kotlin.math.min

// TODO Add warning after compiling that this boxes. Also warn about generic satisfy instructions
class SatisfyChar<E>(val p: CharPredicate) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
                machine.push(c)
            } else {
                unexpected.head = c
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SatisfyChar"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SatisfyChar_<E>(val p: CharPredicate) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
            } else {
                unexpected.head = c
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SatisfyChar_"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SatisfyCharMany<E>(val p: CharPredicate) : Instruction<Char, E> {
    private var st: Int = Int.MAX_VALUE
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = min(st, machine.inputOffset).also { st = Int.MAX_VALUE }
        while (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
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

    override fun toString(): String = "SatisfyCharMany"
}

class SatisfyCharMany_<E>(val p: CharPredicate) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        while (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
            } else return
        }
    }

    override fun toString(): String = "SatisfyCharMany_"
}

class SatisfyCharMap<E>(val p: CharPredicate, val f: CharFunc<Any?>) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
                machine.push(f.invokeP(c))
            } else {
                unexpected.head = c
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SatisfyCharMap"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}
