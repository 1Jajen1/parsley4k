package parsley

import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.backend.ParseStatus

fun <I, E, A> Parser<I, E, A>.compile(): CompiledGenericParser<I, E, A> =
    CompiledGenericParser(
        parserF.compile().toTypedArray()
            //.also { println(it.withIndex().map { (i, v) -> i to v }) }
    )

class CompiledGenericParser<I, E, A>(val instr: Array<Instruction<I, E>>) {
    fun parse(input: Array<I>): A? {
        val machine = GenericStackMachine(instr)
        machine.input = input
        machine.execute()
        return if (machine.status == ParseStatus.Ok) machine.pop().unsafe()
        else null
    }
}

internal class GenericStackMachine<I, E> internal constructor(instr: Array<Instruction<I, E>>) :
    AbstractStackMachine<I, E>(instr) {

    internal var input: Array<I> = emptyArray<Any?>().unsafe()

    override fun hasMore(): Boolean = inputOffset < input.size
    override fun take(): I = input[inputOffset]
    override fun hasMore(n: Int): Boolean = inputOffset < input.size - (n - 1)
    override fun needInput() = fail()
}
