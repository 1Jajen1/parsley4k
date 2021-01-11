package parsley.internal

expect inline fun <A> Any?.unsafe(): A

expect inline fun CharArray.sliceArr(start: Int, end: Int): CharArray
