package parsley

sealed class ParseError<out I, out E> {
    abstract var offset: Int

    data class Trivial<out I>(
        val unexpected: ErrorItem<I>? = null,
        val expected: Set<ErrorItem<I>> = setOf(),
        override var offset: Int
    ): ParseError<I, Nothing>()
    data class Fancy<out I, out E>(
        val errors: Set<FancyError<E>>,
        override var offset: Int
    ): ParseError<I, E>()
}

fun <I, E> ParseError<I, E>.longestMatch(other: ParseError<I, E>): ParseError<I, E> = when {
    offset > other.offset -> this
    offset < other.offset -> other
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