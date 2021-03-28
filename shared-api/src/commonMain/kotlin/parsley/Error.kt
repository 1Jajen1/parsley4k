package parsley

import kotlin.math.max

class ParseError<out I, out E>(
    val offset: Int,
    val unexpected: ErrorItem<I>?,
    val expected: Set<ErrorItem<I>>,
    val errors: Set<FancyError<E>>
) {
    companion object {
        fun <I> failure(unexpected: ErrorItem<I>, expected: Set<ErrorItem<I>>): ParseError<I, Nothing> =
            ParseError(-1, unexpected, expected, emptySet())
        fun <I> unexpected(unexpected: ErrorItem<I>): ParseError<I, Nothing> =
            failure(unexpected, emptySet())
        fun <I> expected(item: ErrorItem<I>, vararg others: ErrorItem<I>): ParseError<I, Nothing> =
            ParseError(-1, null, setOf(item, *others), emptySet())
        
        fun <E> fancy(err: FancyError<E>, vararg others: FancyError<E>): ParseError<Nothing, E> =
            ParseError(-1, null, emptySet(), setOf(err, *others))
        fun message(msg: String): ParseError<Nothing, Nothing> =
            fancy(FancyError.Message(msg))
        fun <E> error(err: E): ParseError<Nothing, E> =
            fancy(FancyError.Custom(err))
    }
}

sealed class ErrorItem<out I> {
    class Tokens<I>(val head: I, val tail: List<I>) : ErrorItem<I>()
    class Label(val label: String) : ErrorItem<Nothing>()
    object EndOfInput : ErrorItem<Nothing>()

    override fun hashCode(): Int = when (this) {
        is Tokens -> 1.xor(head.hashCode().xor(tail.hashCode()))
        is Label -> 2.xor(label.hashCode())
        EndOfInput -> super.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        return when (this) {
            is Tokens -> {
                if (other !is Tokens<*>) false
                else head == other.head && tail == other.tail
            }
            is Label -> {
                if (other !is Label) false
                else label == other.label
            }
            EndOfInput -> other == EndOfInput
        }
    }

    fun length(): Int = when (this) {
        is Tokens -> 1 + tail.size
        is Label -> 0
        EndOfInput -> 0
    }
}

sealed class FancyError<out E> {
    class Message(val msg: String): FancyError<Nothing>()
    class Custom<E>(val error: E): FancyError<E>()
}
