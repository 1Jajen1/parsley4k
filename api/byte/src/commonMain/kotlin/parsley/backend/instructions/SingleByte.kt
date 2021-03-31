package parsley.backend.instructions

import parsley.ByteTokensT
import parsley.ErrorItem
import parsley.ParseErrorT
import parsley.backend.ByteArrayStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.unsafe
import kotlin.math.min

// TODO Add warning after compiling that this boxes
class SingleByte<E>(val c: Byte) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
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

    override fun toString(): String = "SingleByte($c)"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SingleByte_<E>(val c: Byte) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
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

    override fun toString(): String = "SingleByte_($c)"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SingleByteMany<E>(val c: Byte) : Instruction<Byte, E> {
    private var st: Int = Int.MAX_VALUE
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        val start = min(st, machine.inputOffset).also { st = Int.MAX_VALUE }
        while (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else {
                machine.push(machine.takeP(start, machine.inputOffset))
                return
            }
        }
        machine.needInput(
            onSuspend = { st = start },
            onFail = { machine.push(machine.takeP(start, machine.inputOffset)) }
        )
    }

    override fun toString(): String = "SingleByteMany($c)"
}

class SingleByteMany_<E>(val c: Byte) : Instruction<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        while (machine.hasMore()) {
            val el = machine.takeP()
            if (el == c) {
                machine.consume()
            } else return
        }
        machine.needInput(onFail = {})
    }

    override fun toString(): String = "SingleByteMany_($c)"
}

class ByteEof<E> : Instruction<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        if (machine.hasMore()) {
            unexpected.head = machine.takeP()
            machine.failWith(error)
        }
    }

    override fun toString(): String = "ByteEof"

    private val unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    private val error = ParseErrorT<Byte, E>(-1, unexpected, setOf(ErrorItem.EndOfInput), emptySet())
}
