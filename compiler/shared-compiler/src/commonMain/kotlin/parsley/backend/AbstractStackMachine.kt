package parsley.backend

import parsley.ErrorItem
import parsley.ErrorItemT
import parsley.FancyError
import parsley.ParseError
import parsley.ParseErrorT
import parsley.stack.ArrayStack
import parsley.stack.IntStack
import parsley.unsafe

abstract class AbstractStackMachine<I, E>(val instructions: Array<Instruction<I, E>>) {

    val dataStack = ArrayStack()
    val returnStack = IntStack()

    val handlerPosStack = IntStack()
    val handlerOffsetStack = IntStack()
    val handlerReturnOffsetStack = IntStack()
    val inputCheckStack = IntStack()

    var errorOffset: Int = 0
    var errorUnexpected: ErrorItemT<I>? = null
    var errorExpected = ArrayStack()
    var errorFancy = ArrayStack()

    lateinit var finalError: ParseError<I, E>

    var status: ParseStatus = ParseStatus.Ok

    var programCounter: Int = 0
    var inputOffset: Int = 0

    // streaming
    open var acceptMoreInput: Boolean = true
    // TODO consumption analysis to figure out where we and when can drop chunks of the input
    //  The idea being: Whenever we push new input we do a quick check what the last element is we
    //  definitely consumed and no longer need access to.
    // private var retainInputOffset = Int

    // TODO Decide if these should be kept
    fun push(el: Any?): Unit = dataStack.push(el)
    fun pop(): Any? = dataStack.pop()
    fun peek(): Any? = dataStack.peek()
    fun exchange(a: Any?): Unit = dataStack.exchange(a)
    fun drop(): Unit = dataStack.drop()

    fun pushHandler(label: Int): Unit {
        handlerPosStack.push(label)
        handlerOffsetStack.push(dataStack.size())
        handlerReturnOffsetStack.push(returnStack.size())
    }

    fun dropHandler() {
        handlerPosStack.drop()
        handlerOffsetStack.drop()
        handlerReturnOffsetStack.drop()
    }

    fun fail(): Unit {
        status = ParseStatus.Err

        if (handlerPosStack.isNotEmpty()) {
            dataStack.setOffset(handlerOffsetStack.pop())
            returnStack.setOffset(handlerReturnOffsetStack.pop())
            programCounter = handlerPosStack.pop()
        } else {
            finalError = makeError()
            programCounter = instructions.size
        }
    }

    fun makeError(): ParseError<I, E> {
        val expected = mutableSetOf<ErrorItem<I>>()
        errorExpected.forEach { expected.addAll(it.unsafe()) }
        val errors = mutableSetOf<FancyError<E>>()
        errorFancy.forEach { errors.addAll(it.unsafe()) }
        return ParseError(errorOffset, errorUnexpected?.toFinal(), expected, errors)
    }

    fun failWith(err: ParseErrorT<I, E>): Unit {
        setError(err)
        fail()
    }

    fun setError(err: ParseErrorT<I, E>): Unit {
        err.offset = inputOffset
        if (err.offset > errorOffset) {
            errorOffset = err.offset
            errorUnexpected = err.unexpected
            errorExpected.clear()
            errorExpected.push(err.expected)
            errorFancy.clear()
            errorFancy.push(err.errors)
        } else if (err.offset == errorOffset) {
            addUnexpected(err.unexpected)
            errorExpected.push(err.expected)
            errorFancy.push(err.errors)
        }
    }

    fun addUnexpected(item: ErrorItemT<I>?): Unit {
        errorUnexpected = when {
            errorUnexpected == null -> item
            item == null -> errorUnexpected
            errorUnexpected!!.length() > item.length() -> errorUnexpected
            else -> item
        }
    }

    fun addAsHint(error: ParseErrorT<I, E>): Unit {
        if (errorOffset <= inputOffset) {
            if (errorOffset < inputOffset) clearError()
            errorExpected.push(error.expected)
            errorFancy.push(error.errors)
            errorOffset = inputOffset
        }
    }

    fun clearError(): Unit {
        errorOffset = -1
        errorUnexpected = null
        errorExpected.clear()
        errorFancy.clear()
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

    val eofErr = ParseErrorT<I, E>(-1, ErrorItemT.EndOfInput, emptySet(), emptySet())
    inline fun needInput(
        expected: Set<ErrorItem<I>> = emptySet(),
        onSuspend: () -> Unit = {},
        onFail: () -> Unit = { failWith(eofErr.also { it.expected = expected }) }
    ): Unit =
        if (acceptMoreInput) {
            status = ParseStatus.Suspended
            returnStack.push(programCounter - 1)
            programCounter = instructions.size
            onSuspend()
        } else {
            onFail()
        }

    fun execute() {
        while (programCounter < instructions.size) {
            instructions[programCounter++].apply(this)
        }
    }

    fun reexecute() {
        programCounter = returnStack.pop()
        status = ParseStatus.Ok
        execute()
    }
}

sealed class ParseStatus {
    object Ok : ParseStatus()
    object Suspended : ParseStatus()
    object Err : ParseStatus()
}
