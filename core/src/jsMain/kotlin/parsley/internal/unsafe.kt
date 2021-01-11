package parsley.internal

actual inline fun <A> Any?.unsafe(): A = unsafeCast<A>()