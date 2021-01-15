package parsley.backend.instructions

import parsley.Predicate
import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.backend.Jumps
import parsley.backend.ParseStatus
import parsley.stack.ArrayStack
import parsley.stack.IntStack
import parsley.unsafe

class Many<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    var head = mutableListOf<Any?>()
    val accumStack = ArrayStack()
    var id = -1
    val idStack = IntStack()
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val retSize = machine.returnStack.size()
        if (machine.status == ParseStatus.Ok) {
            machine.inputCheckStack.exchange(machine.inputOffset)
            if (id != retSize) {
                idStack.push(id)
                accumStack.push(head)
                head = mutableListOf()
                id = retSize
            }
            head.add(machine.pop())
            machine.jump(to)
        } else {
            if (machine.inputOffset == machine.inputCheckStack.pop()) {
                machine.status = ParseStatus.Ok
                if (id == retSize) {
                    machine.push(head)
                    head = accumStack.pop().unsafe()
                    id = idStack.pop()
                } else {
                    machine.push(emptyList<Any?>())
                }
            } else machine.fail()
        }
    }

    override fun toString(): String = "Many($to)"
}

class Many_<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.status == ParseStatus.Ok) {
            machine.inputCheckStack.exchange(machine.inputOffset)
            machine.jump(to)
        } else {
            if (machine.inputOffset == machine.inputCheckStack.pop()) {
                machine.status = ParseStatus.Ok
            } else machine.fail()
        }
    }

    override fun toString(): String = "Many_($to)"
}

// Optimised fused variants of Many that loop over the input directly rather than looping through instructions
class SatisfyMany<I, E>(val f: Predicate<I>) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        val acc = mutableListOf<I>()
        while (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
                acc.add(i)
            } else {
                machine.push(acc)
                return
            }
        }
        // TODO This won't work
        machine.needInput()
    }

    override fun toString(): String = "SatisfyMany"
}

class SatisfyMany_<I, E>(val f: Predicate<I>) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        while (machine.hasMore()) {
            val i = machine.take()
            if (f(i)) {
                machine.consume()
            } else {
                return
            }
        }
        // TODO This won't work
        machine.needInput()
    }

    override fun toString(): String = "SatisfyMany_"
}

class SingleMany<I, E>(val i: I) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        var els = 0
        while (machine.hasMore()) {
            val el = machine.take()
            if (i == el) {
                machine.consume()
                els++
            } else {
                machine.push(Array<Any?>(els) { i }.toList())
                return
            }
        }
        // TODO This won't work
        machine.needInput()
    }

    override fun toString(): String = "SingleMany"
}

class SingleMany_<I, E>(val i: I) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        while (machine.hasMore()) {
            val el = machine.take()
            if (i == el) {
                machine.consume()
            } else {
                return
            }
        }
        // TODO This won't work
        machine.needInput()
    }

    override fun toString(): String = "SingleMany_"
}
