package parsley.backend

import parsley.StringStackMachine
import parsley.unsafe

// TODO Add warning after compiling that this boxes
class SingleChar<E>(val c: Char) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
                machine.push(el)
            } else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String = "SingleChar($c)"
}

class SingleChar_<E>(val c: Char) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String = "SingleChar_($c)"
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
        // TODO
        machine.needInput()
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
        // TODO
        machine.needInput()
    }

    override fun toString(): String = "SingleCharMany_($c)"
}

class SingleCharMap<E>(val c: Char, val res: Any?) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
                machine.push(res)
            } else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String = "SingleCharMap($c, $res)"
}
