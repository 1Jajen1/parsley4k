package parsley.backend.instructions

import parsley.ByteFunc
import parsley.BytePredicate
import parsley.ByteTokensT
import parsley.ParseErrorT
import parsley.backend.ByteArrayStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.unsafe
import kotlin.math.min

// TODO Add warning after compiling that this boxes. Also warn about generic satisfy instructions
class SatisfyByte<E>(val p: BytePredicate) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
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

    override fun toString(): String = "SatisfyByte"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SatisfyByte_<E>(val p: BytePredicate) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
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

    override fun toString(): String = "SatisfyByte_"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class SatisfyByteMany<E>(val p: BytePredicate) : Instruction<Byte, E> {
    private var st: Int = Int.MAX_VALUE
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        val start = min(st, machine.inputOffset).also { st = Int.MAX_VALUE }
        while (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
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

    override fun toString(): String = "SatisfyByteMany"
}

class SatisfyByteMany_<E>(val p: BytePredicate) : Instruction<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        while (machine.hasMore()) {
            val c = machine.takeP()
            if (p.invokeP(c)) {
                machine.consume()
            } else return
        }
    }

    override fun toString(): String = "SatisfyByteMany_"
}

class SatisfyByteMap<E>(val p: BytePredicate, val f: ByteFunc<Any?>) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
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

    override fun toString(): String = "SatisfyByteMap"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}
