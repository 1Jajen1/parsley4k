package parsley

sealed class ParseError<out I, out E>(val offset: Int) {
    class Trivial<I>(
        offset: Int,
        val unexpected: ErrorItem<I>?,
        val expected: Set<ErrorItem<I>>
    ) : ParseError<I, Nothing>(offset)

    class Fancy<E>(offset: Int, val errors: Set<FancyError<E>>) : ParseError<Nothing, E>(offset)
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
}

sealed class FancyError<out E> {
    class Message(val msg: String): FancyError<Nothing>()
    class Custom<E>(val error: E): FancyError<E>()
}
