package parsley

import com.jakewharton.confundus.unsafeCast

actual inline fun <A> Any?.unsafe(): A = unsafeCast()