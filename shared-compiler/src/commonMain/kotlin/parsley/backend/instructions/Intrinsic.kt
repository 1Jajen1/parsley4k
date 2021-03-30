package parsley.backend.instructions

import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.ParseErrorT
import parsley.Predicate
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
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

    override fun toString(): String = "Many $to"
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

    override fun toString(): String = "Many_ $to"
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

// TODO Is this correct?
class MatchMany_<I, E>(val path: Array<Matcher<I>>) : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        while (true) {
            if (machine.hasMore(path.size)) {
                path.forEachIndexed { ind, m ->
                    val i = machine.take()
                    if (m(i)) {
                        machine.consume()
                    } else {
                        if (ind == 0) return@apply
                        else machine.fail()
                    }
                }
            } else return@apply
        }
    }

    override fun toString(): String = "MatchMany_ ${path.toList()}"
}

class MatchManyN_<I, E>(val paths: Array<Array<Matcher<I>>>) : Instruction<I, E>, Errors<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        loop@while (true) {
            for (p in paths) {
                if (machine.hasMore(p.size)) {
                    p.forEachIndexed { ind, m ->
                        val i = machine.take()
                        if (m(i)) {
                            machine.consume()
                        } else {
                            if (ind != 0) {
                                unexpected.head = i
                                return@apply machine.failWith(error)
                            }
                        }
                    }
                    continue@loop
                }
            }
            // TODO fail here
            return@apply
        }
    }

    override fun toString(): String = "MatchManyN_ ${paths.map { it.toList() }}"

    private var unexpected = ErrorItemT.Tokens<I>(null.unsafe(), mutableListOf())
    override var error = ParseErrorT<I, E>(-1, unexpected, emptySet(), emptySet())
}

sealed class Matcher<I> : Predicate<I> {
    class Eof<I> : Matcher<I>()
    class Sat<I>(val f: Predicate<I>, val expected: Set<ErrorItem<I>>): Matcher<I>()
    class El<I>(val el: I, val expected: Set<ErrorItem<I>>): Matcher<I>()

    override fun invoke(i: I): Boolean = when (this) {
        is Sat -> f(i)
        is El -> el == i
        is Eof -> false
    }

    override fun toString(): String = when (this) {
        is Sat -> "Sat"
        is El -> "El($el)"
        is Eof -> "Eof"
    }
}

// ########## Raw input
class PushChunkOf<I, E> : Instruction<I, E> {
    override fun apply(machine: AbstractStackMachine<I, E>) {
        if (machine.status == ParseStatus.Err) {
            machine.inputCheckStack.drop()
            machine.fail()
        } else {
            machine.handlerStack.drop()
            val start = machine.inputCheckStack.pop()
            val end = machine.inputOffset
            machine.push(machine.slice(start, end).toList())
        }
    }

    override fun toString(): String = "PushChunkOf"
}
