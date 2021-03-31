package parsley.backend.instructions

import parsley.BytePredicate
import parsley.ByteTokensT
import parsley.ErrorItem
import parsley.ParseErrorT
import parsley.backend.ByteArrayStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.unsafe

class SatisfyBytes_<E>(val fArr: Array<BytePredicate>, eArr: Array<Set<ErrorItem<Byte>>>) : Instruction<Byte, E>,
    Errors<Byte, E> {

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    private var errRef = ParseErrorT<Byte, E>(-1, unexpected, emptySet(), emptySet())
    override var error = errRef

    init {
        // TODO Handle relabel better
        val buf = mutableListOf<Byte>()
        var onlySingleToken = true
        eArr.forEach {
            if (it.size == 1) {
                val fst = it.first()
                if (fst is ErrorItem.Tokens && fst.tail.isEmpty()) {
                    buf += fst.head
                } else onlySingleToken = false
            } else onlySingleToken = false
        }
        if (onlySingleToken)
            error = ParseErrorT(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))), emptySet())
    }

    private val sz = fArr.size

    // TODO Does this benefit if the for loop is implemented in StringStackMachine?
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        if (machine.hasMore(sz)) {
            val start = machine.inputOffset
            for (f in fArr) {
                val c = machine.takeP()
                if (f(c)) machine.consume()
                else {
                    machine.inputOffset = start
                    unexpected.all = machine.takeP(start, start + sz)
                    return machine.failWith(error)
                }
            }
        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyBytes_($sz)"
}

class MatchByteArr_<E>(val arr: ByteArray, eArr: Array<Set<ErrorItem<Byte>>>) : Instruction<Byte, E>, Errors<Byte, E> {

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    private var errRef = ParseErrorT<Byte, E>(-1, unexpected, emptySet(), emptySet())
    override var error = errRef

    init {
        // TODO Handle relabel better
        val buf = mutableListOf<Byte>()
        var onlySingleToken = true
        eArr.forEach {
            if (it.size == 1) {
                val fst = it.first()
                if (fst is ErrorItem.Tokens && fst.tail.isEmpty()) {
                    buf += fst.head
                } else onlySingleToken = false
            } else onlySingleToken = false
        }
        if (onlySingleToken)
            error = ParseErrorT(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))), emptySet())
    }

    private val sz = arr.size

    // TODO Does this benefit if the for loop is implemented in StringStackMachine?
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        if (machine.hasMore(sz)) {
            val start = machine.inputOffset
            for (i in 0 until sz) {
                val c = machine.takeP()
                if (c == arr[i]) machine.consume()
                else {
                    machine.inputOffset = start
                    unexpected.all = machine.takeP(start, start + sz)
                    return machine.failWith(error)
                }
            }
        } else machine.needInput()
    }

    override fun toString(): String = "MatchByteArr_(${arr})"
}

class MatchManyByte_<E>(val path: Array<ByteMatcher>) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        while (true) {
            if (machine.hasMore(path.size)) {
                path.forEachIndexed { ind, m ->
                    val i = machine.takeP()
                    if (m.invokeP(i)) {
                        machine.consume()
                    } else {
                        if (ind == 0) return@apply
                        else {
                            machine.fail()
                        }
                    }
                }
            } else return@apply
        }
    }

    override fun toString(): String = "MatchManyByte_(${path.toList()})"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class MatchManyByteN_<E>(val paths: Array<Array<ByteMatcher>>) : Instruction<Byte, E>, Errors<Byte, E> {
    override fun apply(machine: AbstractStackMachine<Byte, E>) {
        val machine = machine.unsafe<ByteArrayStackMachine<E>>()
        loop@while (true) {
            pathL@for (p in paths) {
                if (machine.hasMore(p.size)) {
                    for (ind in p.indices) {
                        val m = p[ind]
                        val i = machine.takeP()
                        if (m.invokeP(i)) {
                            machine.consume()
                        } else {
                            if (ind != 0) return@apply machine.fail()
                            else continue@pathL
                        }
                    }
                    continue@loop
                }
            }
            return@apply
        }
    }

    override fun toString(): String = "MatchManyByteN_(${paths.map { it.toList() }})"

    private var unexpected = ByteTokensT(' '.toByte(), byteArrayOf())
    override var error: ParseErrorT<Byte, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

sealed class ByteMatcher : BytePredicate {
    class Sat(val f: BytePredicate, val expected: Set<ErrorItem<Byte>>): ByteMatcher()
    class El(val el: Byte, val expected: Set<ErrorItem<Byte>>): ByteMatcher()
    object Eof: ByteMatcher()

    override fun invoke(i: Byte): Boolean = when (this) {
        is Sat -> f(i)
        is El -> el == i
        Eof -> false
    }

    override fun invokeP(i: Byte): Boolean = when (this) {
        is Sat -> f(i)
        is El -> el == i
        Eof -> false
    }

    override fun toString(): String = when (this) {
        is Sat -> "Sat"
        is El -> "El($el)"
        Eof -> "Eof"
    }
}

fun Array<Matcher<Byte>>.convert(): Array<ByteMatcher> = map {
    when (it) {
        is Matcher.Sat -> ByteMatcher.Sat(it.f.unsafe(), it.expected.unsafe())
        is Matcher.El -> ByteMatcher.El(it.el, it.expected.unsafe())
        is Matcher.Eof -> ByteMatcher.Eof
    }
}.toTypedArray()

fun Array<Array<Matcher<Byte>>>.convert(): Array<Array<ByteMatcher>> = map {
    it.convert()
}.toTypedArray()
