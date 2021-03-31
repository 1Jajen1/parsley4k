package parsley

fun interface Predicate<I> {
    operator fun invoke(i: I): Boolean
}
