package parsley

fun interface Predicate<in I> {
    operator fun invoke(i: I): Boolean
}

fun interface Function1<in A, out B> {
    operator fun invoke(a: A): B
}
