package parsley.internal

import com.jakewharton.confundus.unsafeCast

actual inline fun <A> Any?.unsafe(): A = unsafeCast()
actual inline fun CharArray.sliceArr(start: Int, end: Int): CharArray {
    val arr = CharArray(end - start)
    System.arraycopy(this, start, arr, 0, arr.size)
    return arr
}