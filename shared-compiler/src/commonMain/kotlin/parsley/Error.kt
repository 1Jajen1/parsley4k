package parsley

class ParseErrorT<I, E>(
    var offset: Int,
    var unexpected: ErrorItemT<I>?,
    var expected: Set<ErrorItem<I>>,
    var errors: Set<FancyError<E>>
) {
    fun toFinal(): ParseError<I, E> = ParseError(offset, unexpected?.toFinal(), expected, errors)

    companion object {
        fun <I, E> fromFinal(p: ParseError<I, E>): ParseErrorT<I, E> =
            ParseErrorT(p.offset, p.unexpected?.toTemplate(), p.expected, p.errors)
    }
}

interface ErrorItemT<out I> {
    fun toFinal(): ErrorItem<I> = when (this) {
        is Tokens -> ErrorItem.Tokens(head, tail.toList())
        is Label -> ErrorItem.Label(label)
        is EndOfInput -> ErrorItem.EndOfInput
        else -> TODO("")
    }

    fun length(): Int = when (this) {
        is Tokens -> 1 + tail.size
        is Label -> 0
        EndOfInput -> 0
        else -> TODO()
    }

    class Tokens<I>(var head: I, val tail: MutableList<I>) : ErrorItemT<I>
    class Label(var label: String) : ErrorItemT<Nothing>
    object EndOfInput : ErrorItemT<Nothing>
}

fun <I> ErrorItem<I>.toTemplate(): ErrorItemT<I> = when (this) {
    is ErrorItem.Tokens -> ErrorItemT.Tokens(head, tail.toMutableList())
    is ErrorItem.Label -> ErrorItemT.Label(label)
    ErrorItem.EndOfInput -> ErrorItemT.EndOfInput
}
