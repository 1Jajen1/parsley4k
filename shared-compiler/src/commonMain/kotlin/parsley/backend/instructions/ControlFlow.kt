package parsley.backend.instructions

import parsley.Either
import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.ParseErrorT
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.backend.Jumps
import parsley.unsafe
import kotlin.collections.Map

class Call<I, E>(val recursive: Boolean, override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) = machine.call(to)

    override fun toString(): String = "Call($to)"
}

class Jump<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) = machine.jump(to)

    override fun toString(): String = "Jump($to)"
}

class Label<I, E>(val id: Int) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        throw IllegalStateException("Label was not removed on assembly")
    }

    override fun toString(): String = "Label($id)"
}

class Fail<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        machine.fail()
    }

    override fun toString(): String = "Fail"
}

class FailIfLeft<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val a = machine.peek().unsafe<Either<Any?, Any?>>()
        a.fold({ machine.fail() }, { machine.exchange(it) })
    }

    override fun toString(): String = "FailIfLeft"
}

class JumpIfRight<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val a = machine.peek().unsafe<Either<Any?, Any?>>()
        a.fold({ machine.exchange(it) }, {
            machine.exchange(it)
            machine.jump(to)
        })
    }

    override fun toString(): String = "JumpIfRight($to)"
}

class Return<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) = machine.exit()

    override fun toString(): String = "Return"
}

// Optimised
class JumpTable<I, E>(var table: Map<I, Int>, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Jumps, Errors<I> {
    override var to: Int = -1 // unused
    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error: ParseErrorT.Trivial<I> = ParseErrorT.Trivial(-1, unexpected, expected)
    override fun onAssembly(f: (Int) -> Int): Boolean {
        table = table.mapValues { (_, i) -> f(i) }
        return true
    }
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (table.containsKey(i)) {
                machine.jump(table[i]!!)
            } else {
                unexpected.head = i
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "JumpTable($table)"
}
