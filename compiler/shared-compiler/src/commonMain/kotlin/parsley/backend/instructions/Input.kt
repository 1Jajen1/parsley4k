package parsley.backend.instructions

import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.ParseErrorT
import parsley.Predicate
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.FuseMap
import parsley.backend.Instruction
import parsley.unsafe

class Satisfy<I, E>(val f: Predicate<I>, expected: Set<ErrorItem<I>>) : Instruction<I, E>, FuseMap<I, E>, Errors<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.push(i)
            } else {
                unexpected.head = i
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "Satisfy"
    override fun fuse(f: (Any?) -> Any?): Instruction<I, E> = SatisfyMap(this.f, f, error.expected)

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT<I, E>(-1, unexpected, expected, emptySet())
}

class Satisfy_<I, E>(val f: Predicate<I>, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
            } else {
                unexpected.head = i
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "Satisfy_"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT<I, E>(-1, unexpected, expected, emptySet())
}

class SatisfyNoFail<I, E>(val f: Predicate<I>) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val el = machine.take()
            if (f(el)) machine.consume()
        } else machine.needInput(onFail = {})
    }

    override fun toString(): String = "SatisfyNoFail"
}

class Single<I, E>(val i: I, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val el = machine.take()
            if (el == i) {
                machine.consume()
                machine.push(el)
            } else {
                unexpected.head = el
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "Single $i"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT<I, E>(-1, unexpected, expected, emptySet())
}

class Single_<I, E>(val i: I, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val el = machine.take()
            if (el == i) {
                machine.consume()
            } else {
                unexpected.head = el
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "Single_ $i"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT<I, E>(-1, unexpected, expected, emptySet())
}

class SingleNoFail<I, E>(val i: I) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val el = machine.take()
            if (el == i) machine.consume()
        } else machine.needInput(onFail = {})
    }

    override fun toString(): String = "SingleNoFail"
}

// Optimized concatenated methods
class SatisfyN_<I, E>(val fArr: Array<Predicate<I>>, val eArr: Array<Set<ErrorItem<I>>>) : Instruction<I, E>, Errors<I, E> {

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    private var errRef = ParseErrorT<I, E>(-1, unexpected, emptySet(), emptySet())
    override var error = errRef

    init {
        // TODO Handle relabel better
        val buf = mutableListOf<I>()
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
            error = ParseErrorT<I, E>(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))), emptySet())
    }

    private val sz = fArr.size
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore(sz)) {
            for (ind in fArr.indices) {
                val f = fArr[ind]
                val el = machine.take()
                if (f(el)) machine.consume()
                else {
                    // TODO Get whole slice here
                    unexpected.head = el
                    errRef.expected = eArr[ind]
                    return machine.failWith(error)
                }
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SatisfyN_ $sz"
}

class SingleN_<I, E>(val fArr: Array<I>, val eArr: Array<Set<ErrorItem<I>>>) : Instruction<I, E>, Errors<I, E> {

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    private var errRef = ParseErrorT<I, E>(-1, unexpected, emptySet(), emptySet())
    override var error = errRef

    init {
        // TODO Handle relabel better
        val buf = mutableListOf<I>()
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
            error = ParseErrorT<I, E>(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))), emptySet())
    }

    private val sz = fArr.size
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore(sz)) {
            for (ind in fArr.indices) {
                val i = fArr[ind]
                val el = machine.take()
                if (i == el) machine.consume()
                else {
                    // TODO Get whole slice
                    unexpected.head = el
                    errRef.expected = eArr[ind]
                    return machine.failWith(error)
                }
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SingleN_ ${fArr.toList()}"
}

// Optimized fused methods
class SatisfyMap<I, E>(val f: Predicate<I>, val g: (I) -> Any?, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.push(g(i))
            } else {
                unexpected.head = i
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SatisfyMap"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT<I, E>(-1, unexpected, expected, emptySet())
}

class Eof<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            unexpected.head = machine.take()
            machine.failWith(error)
        }
    }

    override fun toString(): String = "Eof"

    private val unexpected = ErrorItemT.Tokens(null as I, mutableListOf())
    private val error = ParseErrorT<I, E>(-1, unexpected, setOf(ErrorItem.EndOfInput), emptySet())
}
