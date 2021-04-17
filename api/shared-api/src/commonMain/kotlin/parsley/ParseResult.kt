package parsley

sealed class ParseResult<IArr, out I, out E, out A> {
    class Done<IArr, A>(val result: A, val remaining: IArr) : ParseResult<IArr, Nothing, Nothing, A>()
    class Partial<IArr, I, E, A>(val f: (IArr?) -> ParseResult<IArr, I, E, A>) : ParseResult<IArr, I, E, A>()
    class Failure<IArr, I, E>(val error: ParseError<I, E>, val remaining: IArr) : ParseResult<IArr, I, E, Nothing>()

    fun pushChunk(chunk: IArr?): ParseResult<IArr, I, E, A> = when (this) {
        is Partial -> f(chunk)
        else -> this
    }

    fun pushEndOfInput(): ParseResult<IArr, I, E, A> = pushChunk(null)

    inline fun <B> fold(
        onDone: (A, IArr) -> B,
        onPartial: (f: (IArr) -> ParseResult<IArr, I, E, A>) -> B,
        onFailure: (ParseError<I, E>, IArr) -> B
    ): B = when (this) {
        is Done -> onDone(result, remaining)
        is Partial -> onPartial(f)
        is Failure -> onFailure(error, remaining)
    }
}
