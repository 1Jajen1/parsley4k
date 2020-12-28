package arrow

sealed class Either<out A, out B> {
    inline fun <C> fold(fA: (A) -> C, fB: (B) -> C): C = when (this) {
        is Left -> fA(a)
        is Right -> fB(b)
    }

    companion object {
        fun <A> left(a: A): Either<A, Nothing> = Left(a)
        fun <B> right(b: B): Either<Nothing, B> = Right(b)
    }

    data class Left<out A>(val a: A): Either<A, Nothing>()
    data class Right<out B>(val b: B): Either<Nothing, B>()
}

fun <A, B, C> Either<A, B>.map(f: (B) -> C): Either<A, C> = fold({ Either.left(it) }, { Either.right(f(it)) })
