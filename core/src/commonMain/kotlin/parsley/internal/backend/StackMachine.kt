package parsley.internal.backend

import parsley.ErrorItem
import parsley.ParseError
import parsley.internal.backend.util.ArrayStack
import parsley.internal.backend.util.IntStack
import parsley.internal.backend.util.Stack
import parsley.longestMatch

internal abstract class StackMachine<I, E>(val instructions: Array<Instruction<I, E>>) {
    var status: ParseStatus<I, E> = ParseStatus.Ok
    var inputOffset = 0
    var programCounter = 0

    val dataStack: Stack<Any?> = ArrayStack()
    val returnStack: IntStack = IntStack()
    val handlerStack: Stack<Handler<I, E>> = ArrayStack()
    var lastError: ParseError<I, E>? = null

    fun call(to: Int): Unit {
        returnStack.push(programCounter)
        programCounter = to
    }

    fun exit(): Unit {
        programCounter = returnStack.pop()
    }

    fun jump(to: Int): Unit {
        programCounter = to
    }

    fun failWith(err: ParseError<I, E>): Unit {
        if (handlerStack.size() > 0)
            handlerStack.pop().onFail(this, err)
        else {
            status =
                if (lastError != null) ParseStatus.Failed(lastError!!.longestMatch(err))
                else ParseStatus.Failed(err)
        }
    }

    abstract fun hasMore(): Boolean
    abstract fun take(): I
    abstract fun consume(): Unit

    fun execute(): Unit {
        while (status is ParseStatus.Ok && programCounter < instructions.size) {
            instructions[programCounter++].apply(this)
        }
    }
}

internal sealed class ParseStatus<out I, out E> {
    object Ok : ParseStatus<Nothing, Nothing>()
    class NeedInput<I>(val expected: Set<ErrorItem<I>>) : ParseStatus<I, Nothing>()
    class Failed<I, E>(val error: ParseError<I, E>) : ParseStatus<I, E>()
}
