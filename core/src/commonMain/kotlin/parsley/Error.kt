package parsley

sealed class ParseError<out I, out E> {
    fun getErrorOffset(): Int = when (this) {
        is Trivial -> offset
        is Fancy -> offset
    }

    fun setErrorOffset(o: Int): ParseError<I, E> = when (this) {
        is Trivial -> copy(offset = o)
        is Fancy -> copy(offset = o)
    }

    data class Trivial<out I>(
        val unexpected: ErrorItem<I>? = null,
        val expected: Set<ErrorItem<I>> = setOf(),
        val offset: Int
    ): ParseError<I, Nothing>()
    data class Fancy<out I, out E>(
        val errors: Set<FancyError<E>>,
        val offset: Int
    ): ParseError<I, E>()
}

fun <I, E> ParseError<I, E>.longestMatch(other: ParseError<I, E>): ParseError<I, E> = when {
    getErrorOffset() > other.getErrorOffset() -> this
    getErrorOffset() < other.getErrorOffset() -> other
    this is ParseError.Trivial && other is ParseError.Trivial -> {
        ParseError.Trivial(
            unexpected = unexpected ?: other.unexpected,
            expected = expected.union(other.expected),
            offset = offset
        )
    }
    this is ParseError.Trivial && other is ParseError.Fancy -> other
    this is ParseError.Fancy && other is ParseError.Trivial -> this
    this is ParseError.Fancy && other is ParseError.Fancy -> {
        ParseError.Fancy(errors.union(other.errors), offset)
    }
    else -> TODO("Should be unreachable")
}

sealed class FancyError<out E> {
    data class Message(val msg: String): FancyError<Nothing>()
    data class ErrorCustom<out E>(val err: E): FancyError<E>()
}

sealed class ErrorItem<out I> {
    data class Tokens<out I>(val head: I, val tail: List<I> = emptyList()): ErrorItem<I>()
    data class Label(val label: String): ErrorItem<Nothing>()
    object EndOfInput : ErrorItem<Nothing>()
}