package parsley.backend

import parsley.stack.ArrayStack
import parsley.stack.IntStack
import parsley.unsafe

abstract class AbstractStackMachine<I, E>(val instructions: Array<Instruction<I, E>>) {

    val dataStack = ArrayStack()
    val returnStack = IntStack()
    val handlerStack = ArrayStack()
    val inputCheckStack = IntStack()

    var status: ParseStatus = ParseStatus.Ok

    var programCounter: Int = 0
    var inputOffset: Int = 0

    // TODO Decide if these should be kept
    fun push(el: Any?): Unit = dataStack.push(el)
    fun pop(): Any? = dataStack.pop()
    fun peek(): Any? = dataStack.peek()
    fun exchange(a: Any?): Unit = dataStack.exchange(a)
    fun drop(): Unit = dataStack.drop()

    fun pushHandler(label: Int): Unit =
        handlerStack.push(Handler(label, dataStack.size(), returnStack.size()))

    fun fail(): Unit {
        status = ParseStatus.Err

        if (handlerStack.isNotEmpty()) {
            val handler = handlerStack.pop().unsafe<Handler>()
            dataStack.setOffset(handler.dataStackSz)
            returnStack.setOffset(handler.retStackSz)
            programCounter = handler.handlerPos
        } else {
            TODO("No more handlers!")
        }
    }

    fun call(sub: Int): Unit {
        returnStack.push(programCounter)
        programCounter = sub
    }

    fun exit(): Unit {
        programCounter = if (returnStack.isEmpty()) {
            Int.MAX_VALUE
        } else {
            returnStack.pop()
        }
    }

    fun jump(to: Int): Unit {
        programCounter = to
    }

    abstract fun hasMore(): Boolean
    abstract fun hasMore(n: Int): Boolean
    abstract fun take(): I
    fun consume(): Int = inputOffset++
    fun consume(n: Int) {
        inputOffset += n
    }
    open fun needInput(): Unit = fail()

    fun execute() {
        while (programCounter < instructions.size) {
            instructions[programCounter++].apply(this)
        }
    }
}

sealed class ParseStatus {
    object Ok : ParseStatus()
    object Err : ParseStatus()
}

internal data class Handler(val handlerPos: Int, val dataStackSz: Int, val retStackSz: Int)
