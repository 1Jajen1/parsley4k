package parsley.backend

import parsley.CharPredicate
import parsley.StringStackMachine
import parsley.unsafe

class SatisfyChars_<E>(val fArr: Array<CharPredicate>) : Instruction<Char, E> {
    private val sz = fArr.size

    // TODO Does this benefit if the for loop is implemented in StringStackMachine?
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore(sz)) {
            for (f in fArr) {
                val c = machine.takeP()
                if (f(c)) machine.consume()
                else return machine.fail()
            }
        } else {
            // TODO Consume up to max here
            machine.needInput()
        }
    }

    override fun toString(): String = "SatisfyChars_($sz)"
}

class MatchString_<E>(val arr: CharArray) : Instruction<Char, E> {
    private val sz = arr.size

    // TODO Does this benefit if the for loop is implemented in StringStackMachine?
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore(sz)) {
            for (i in 0 until sz) {
                val c = machine.takeP()
                if (c == arr[i]) machine.consume()
                else return machine.fail()
            }
        } else {
            // TODO Consume up to max here
            machine.needInput()
        }
    }

    override fun toString(): String = "MatchString_(${arr.concatToString()})"
}
