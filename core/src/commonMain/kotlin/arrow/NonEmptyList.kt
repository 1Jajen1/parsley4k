package arrow

data class NonEmptyList<out A>(val head: A, val tail: List<A> = emptyList())
