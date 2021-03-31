package parsley

actual inline fun <A> Any?.unsafe(): A = unsafeCast<A>()
