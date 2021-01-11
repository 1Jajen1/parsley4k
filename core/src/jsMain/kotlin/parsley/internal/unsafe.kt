package parsley.internal

actual inline fun <A> Any?.unsafe(): A = unsafeCast<A>()
// TODO vv
actual inline fun CharArray.sliceArr(start: Int, end: Int): CharArray =
    sliceArray(start until end)