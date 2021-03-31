package parsley.backend

internal class ByteArrayStackMachine<E> internal constructor(instr: Array<Instruction<Byte, E>>) :
    AbstractStackMachine<Byte, E>(instr) {

    internal var input: ByteArray = byteArrayOf()

    internal var warnBoxing = false
    override var acceptMoreInput: Boolean = true

    override fun hasMore(): Boolean = inputOffset < input.size
    override fun take(): Byte = input[inputOffset].also { warnBoxing = true }
    override fun hasMore(n: Int): Boolean = inputOffset < input.size - (n - 1)
    override fun slice(start: Int, end: Int): Array<Byte> =
        input.copyOfRange(start, end).toTypedArray().also { warnBoxing = true }

    fun takeP(): Byte = input[inputOffset]
    fun takeP(start: Int, end: Int): ByteArray = input.copyOfRange(start, end)

    fun feed(inp: ByteArray): Unit {
        input += inp
    }
}


