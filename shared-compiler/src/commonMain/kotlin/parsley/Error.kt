package parsley

sealed class ParseErrorT<out I, out E>(var offset: Int) {
    class Trivial<I>(
        offset: Int,
        var unexpected: ErrorItemT<I>?,
        var expected: Set<ErrorItem<I>>
    ) : ParseErrorT<I, Nothing>(offset)

    class Fancy<E>(offset: Int, val errors: MutableSet<FancyError<E>>) : ParseErrorT<Nothing, E>(offset)
}

interface ErrorItemT<out I> {
    fun toFinal(): ErrorItem<I> = when (this) {
        is Tokens -> ErrorItem.Tokens(head, tail.toList())
        is Label -> ErrorItem.Label(label)
        is EndOfInput -> ErrorItem.EndOfInput
        else -> TODO("")
    }

    class Tokens<I>(var head: I, val tail: MutableList<I>) : ErrorItemT<I>
    class Label(var label: String) : ErrorItemT<Nothing>
    object EndOfInput : ErrorItemT<Nothing>
}