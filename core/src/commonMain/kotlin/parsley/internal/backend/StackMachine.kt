package parsley.internal.backend

import parsley.ParseError
import parsley.internal.backend.util.ArrayStack
import parsley.internal.backend.util.IntStack

internal abstract class StackMachine<I, E>(val instructions: Array<Instruction<I, E>>) {
    var status: ParseStatus<I, E> = ParseStatus.Ok
    var inputOffset = 0
    var programCounter = 0

    val dataStack = ArrayStack<Any?>()
    val returnStack: IntStack = IntStack()
    val handlerStack = ArrayStack<Handler<I, E>>()
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

    fun failWith(): Unit {
        if (handlerStack.size() > 0)
            handlerStack.pop().onFail(this)
        else {
            status = ParseStatus.Failed()
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
    class NeedInput<I> : ParseStatus<I, Nothing>()
    class Failed<I, E> : ParseStatus<I, E>()
}
