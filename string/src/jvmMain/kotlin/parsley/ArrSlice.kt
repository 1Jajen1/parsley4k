package parsley

internal actual inline fun CharArray.slice(start: Int, end: Int): CharArray {
    val sz = end - start
    val arr = CharArray(sz)
    System.arraycopy(this, start, arr, 0, sz)
    return arr
}