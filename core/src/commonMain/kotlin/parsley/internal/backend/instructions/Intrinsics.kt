package parsley.internal.backend.instructions

import parsley.internal.backend.Handler
import parsley.internal.backend.Instruction
import parsley.internal.backend.Jumps
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.backend.util.ArrayStack
import parsley.internal.backend.util.IntStack
import parsley.internal.frontend.Predicate

// Helpers to implement intrinsic methods
internal class Many<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    var offset = -1
    val stackedOffsets = IntStack()
    var acc = mutableListOf<Any?>()
    val stackedAccs = ArrayStack<MutableList<Any?>>()
    var id = -1
    val stackedIds = IntStack()

    override fun apply(machine: StackMachine<I, E>) {
        val stackOff = machine.dataStack.size()
        if (id == stackOff) {
            offset = machine.inputOffset
            acc.add(machine.dataStack.pop())
        } else {
            stackedOffsets.push(offset)
            offset = machine.inputOffset

            stackedIds.push(id)
            id = stackOff + 1

            stackedAccs.push(acc)
            acc = mutableListOf()
            machine.handlerStack.push(
                ManyHandler(
                    this,
                    stackOff,
                    machine.returnStack.size()
                )
            )
        }
    }

    override fun toString(): String = "Many($to)"
}

internal class ManyHandler<I, E>(
    val manyRef: Many<I, E>,
    val stackOffset: Int,
    val retOffset: Int
) : Handler<I, E> {
    override fun onFail(machine: StackMachine<I, E>) {
        machine.dataStack.setOffset(stackOffset)
        machine.returnStack.setOffset(retOffset)

        val list = manyRef.acc
        manyRef.acc = manyRef.stackedAccs.pop()

        machine.inputOffset = manyRef.offset
        manyRef.offset = manyRef.stackedOffsets.pop()

        manyRef.id = manyRef.stackedIds.pop()

        machine.jump(manyRef.to)
        machine.dataStack.push(list)
    }
}

internal class Many_<I, E>(override var to: Int) : Instruction<I, E>, Jumps {
    var id = -1
    val stackedIds = IntStack()
    var offset = 0
    val stackedOffs = IntStack()

    override fun apply(machine: StackMachine<I, E>) {
        val stackOff = machine.dataStack.size()
        if (id != stackOff) {
            stackedIds.push(id)
            id = stackOff

            stackedOffs.push(offset)
            offset = machine.inputOffset

            machine.handlerStack.push(
                Many_Handler(
                    this,
                    stackOff,
                    machine.returnStack.size()
                )
            )
        } else {
            offset = machine.inputOffset
        }
    }

    override fun toString(): String = "Many_($to)"
}

internal class Many_Handler<I, E>(
    val manyRef: Many_<I, E>,
    val stackOffset: Int,
    val retOffset: Int
) : Handler<I, E> {
    override fun onFail(machine: StackMachine<I, E>) {
        machine.dataStack.setOffset(stackOffset)
        machine.returnStack.setOffset(retOffset)

        manyRef.id = manyRef.stackedIds.pop()

        machine.inputOffset = manyRef.offset
        manyRef.offset = manyRef.stackedOffs.pop()

        machine.jump(manyRef.to)
    }
}

internal class SatisfyMany<I, E>(val f: Predicate<I>): Instruction<I, E>, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        val acc = mutableListOf<I>()
        while (machine.hasMore()) {
            val i = machine.take()
            if (f(i).not()) {
                machine.dataStack.push(acc)
                return
            } else {
                acc.add(i)
                machine.consume()
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "SatisfyMany"
    override fun pushes(): Int = 1
}

internal class SatisfyMany_<I, E>(val f: Predicate<I>): Instruction<I, E>, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        while (machine.hasMore()) {
            val i = machine.take()
            if (f(i).not()) return
            else machine.consume()
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "SatisfyMany_"
    override fun pushes(): Int = 1
}

internal class SatisfyManyAndMap<I, E>(val f: Predicate<I>, val fa: (I) -> Any?): Instruction<I, E>, Pushes {
    override fun apply(machine: StackMachine<I, E>) {
        val acc = mutableListOf<Any?>()
        while (machine.hasMore()) {
            val i = machine.take()
            if (f(i).not()) {
                machine.dataStack.push(acc)
                return
            } else {
                acc.add(f(i))
                machine.consume()
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "SatisfyManyAndMap"
    override fun pushes(): Int = 1
}
