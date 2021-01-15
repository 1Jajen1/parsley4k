package parsley.backend.instructions

import parsley.Predicate
import parsley.backend.AbstractStackMachine
import parsley.backend.FuseMap
import parsley.backend.Instruction

class Satisfy<I, E>(val f: Predicate<I>) : Instruction<I, E>, FuseMap<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.push(i)
            } else {
                machine.fail()
            }
        } else machine.needInput()
    }

    override fun toString(): String = "Satisfy"
    override fun fuse(f: (Any?) -> Any?): Instruction<I, E> = SatisfyMap(this.f, f)
}

class Satisfy_<I, E>(val f: Predicate<I>) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
            } else {
                machine.fail()
            }
        } else machine.needInput()
    }

    override fun toString(): String = "Satisfy_"
}

class Single<I, E>(val i: I) : Instruction<I, E>, FuseMap<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val el = machine.take()
            if (el == i) {
                machine.consume()
                machine.push(el)
            } else {
                machine.fail()
            }
        } else machine.needInput()
    }

    override fun toString(): String = "Single($i)"
    override fun fuse(f: (Any?) -> Any?): Instruction<I, E> = SingleMap(i, f(i))
}

class Single_<I, E>(val i: I) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val el = machine.take()
            if (el == i) {
                machine.consume()
            } else {
                machine.fail()
            }
        } else machine.needInput()
    }

    override fun toString(): String = "Single_($i)"
}

// Optimized concatenated methods
class SatisfyN_<I, E>(val fArr: Array<Predicate<I>>) : Instruction<I, E> {
    private val sz = fArr.size
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore(sz)) {
            for (f in fArr) {
                val el = machine.take()
                if (f(el)) machine.consume()
                else return machine.fail()
            }

        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyN_($sz)"
}

class SingleN_<I, E>(val fArr: Array<I>) : Instruction<I, E> {
    private val sz = fArr.size
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore(sz)) {
            for (i in fArr) {
                val el = machine.take()
                if (i == el) machine.consume()
                else return machine.fail()
            }

        } else machine.needInput()
    }

    override fun toString(): String = "SingleN_(${fArr.toList()})"
}

// Optimized fused methods
class SatisfyMap<I, E>(val f: Predicate<I>, val g: (I) -> Any?) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                machine.push(g(i))
            } else {
                machine.fail()
            }
        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyMap"
}

// TODO Add a note to the docs later that g(i) == g(j) is expected if i == j
class SingleMap<I, E>(val el: I, val res: Any?) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.hasMore()) {
            val i = machine.take()
            if (el == i) {
                machine.consume()
                machine.push(res)
            } else {
                machine.fail()
            }
        } else machine.needInput()
    }

    override fun toString(): String = "SingleMap($el, $res)"
}
