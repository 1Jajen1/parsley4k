package parsley.backend

import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.ParseError
import parsley.ParseErrorT
import parsley.stack.ArrayStack
import parsley.stack.IntStack
import parsley.unsafe

abstract class AbstractStackMachine<I, E>(val instructions: Array<Instruction<I, E>>) {

    val dataStack = ArrayStack()
    val returnStack = IntStack()
    val handlerStack = ArrayStack()
    val inputCheckStack = IntStack()
    var error: ParseErrorT<I, E>? = null
    var hints = ArrayStack()
    var hintOffset: Int = 0
    lateinit var finalError: ParseError<I, E>

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
            if (error == null) TODO("Empty error")

            val err = error!!
            val offset = error!!.offset
            val finalErr = when (err) {
                is ParseErrorT.Trivial -> {
                    val unexpected = err.unexpected?.toFinal()
                    clearError()
                    val expected = mutableSetOf<ErrorItem<I>>()
                    hints.forEach { expected.addAll(it.unsafe()) }
                    ParseError.Trivial(offset, unexpected, expected)
                }
                is ParseErrorT.Fancy -> {
                    ParseError.Fancy(offset, err.errors)
                }
            }
            finalError = finalErr
            programCounter = instructions.size
        }
    }

    fun failWith(err: ParseErrorT<I, E>): Unit {
        err.offset = inputOffset
        error = err
        fail()
    }

    fun clearError() {
        if (error is ParseErrorT.Trivial) {
            val err = error.unsafe<ParseErrorT.Trivial<I>>()
            if (hintOffset == err.offset) {
                hints.push(err.expected)
            } else if (hintOffset < err.offset) {
                hints.clear()
                hints.push(err.expected)
                hintOffset = err.offset
            }
        }
        error = null
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
    abstract fun slice(start: Int, end: Int): Array<I>
    fun consume(): Int = inputOffset++
    fun consume(n: Int) {
        inputOffset += n
    }

    private val eofErr = ParseErrorT.Trivial<I>(-1, ErrorItemT.EndOfInput, emptySet())
    open fun needInput(expected: Set<ErrorItem<I>> = emptySet()): Unit =
        failWith(eofErr.also { it.expected = expected })

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
