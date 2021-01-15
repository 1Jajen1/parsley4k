package parsley.backend

import parsley.CharFunc
import parsley.CharPredicate
import parsley.StringStackMachine
import parsley.unsafe

// TODO Add warning after compiling that this boxes. Also warn about generic satisfy instructions
class SatisfyChar<E>(val p: CharPredicate) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
                machine.push(c)
            } else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyChar"
}

class SatisfyChar_<E>(val p: CharPredicate) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
            } else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyChar_"
}

class SatisfyCharMany<E>(val p: CharPredicate) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
            } else {
                machine.push(machine.takeP(start, machine.inputOffset).concatToString())
                return
            }
        }
        // TODO
        machine.needInput()
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
        // TODO
        machine.needInput()
    }

    override fun toString(): String = "SatisfyCharMany_"
}

class SatisfyCharMap<E>(val p: CharPredicate, val f: CharFunc<Any?>) : Instruction<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
                machine.push(f.invokeP(c))
            } else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyCharMap"
}
