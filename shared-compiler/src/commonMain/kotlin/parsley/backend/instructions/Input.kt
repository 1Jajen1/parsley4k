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

class Satisfy<I, E>(val f: Predicate<I>, expected: Set<ErrorItem<I>>) : Instruction<I, E>, FuseMap<I, E>, Errors<I> {
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
    override var error = ParseErrorT.Trivial(-1, unexpected, expected)
}

class Satisfy_<I, E>(val f: Predicate<I>, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I> {
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
    override var error = ParseErrorT.Trivial(-1, unexpected, expected)
}

class Single<I, E>(val i: I, expected: Set<ErrorItem<I>>) : Instruction<I, E>, FuseMap<I, E>, Errors<I> {
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

    override fun toString(): String = "Single($i)"
    override fun fuse(f: (Any?) -> Any?): Instruction<I, E> = SingleMap(i, f(i), error.expected)

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT.Trivial(-1, unexpected, expected)
}

class Single_<I, E>(val i: I, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I> {
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

    override fun toString(): String = "Single_($i)"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT.Trivial(-1, unexpected, expected)
}

// Optimized concatenated methods
class SatisfyN_<I, E>(val fArr: Array<Predicate<I>>, val eArr: Array<Set<ErrorItem<I>>>) : Instruction<I, E>, Errors<I> {

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    private var errRef = ParseErrorT.Trivial(-1, unexpected, emptySet())
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
            error = ParseErrorT.Trivial(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))))
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

    override fun toString(): String = "SatisfyN_($sz)"
}

class SingleN_<I, E>(val fArr: Array<I>, val eArr: Array<Set<ErrorItem<I>>>) : Instruction<I, E>, Errors<I> {

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    private var errRef = ParseErrorT.Trivial(-1, unexpected, emptySet())
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
            error = ParseErrorT.Trivial(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))))
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

    override fun toString(): String = "SingleN_(${fArr.toList()})"
}

// Optimized fused methods
class SatisfyMap<I, E>(val f: Predicate<I>, val g: (I) -> Any?, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I> {
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
    override var error = ParseErrorT.Trivial(-1, unexpected, expected)
}

// TODO Add a note to the docs later that g(i) == g(j) is expected if i == j
class SingleMap<I, E>(val el: I, val res: Any?, expected: Set<ErrorItem<I>>) : Instruction<I, E>, Errors<I> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (el == i) {
                machine.consume()
                machine.push(res)
            } else {
                unexpected.head = i
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String = "SingleMap($el, $res)"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT.Trivial(-1, unexpected, expected)
}
