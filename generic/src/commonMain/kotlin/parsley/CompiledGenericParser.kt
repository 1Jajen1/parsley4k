package parsley

import parsley.backend.AbstractStackMachine
import parsley.backend.Instruction
import parsley.backend.ParseStatus

fun <I, E, A> Parser<I, E, A>.compile(): CompiledGenericParser<I, E, A> =
    CompiledGenericParser(
        parserF.compile().toTypedArray()
            // .also { println(it.withIndex().map { (i, v) -> i to v }) }
    )

class CompiledGenericParser<I, E, A>(val instr: Array<Instruction<I, E>>): CompiledParser<Array<I>, I, E, A> {
    override fun parse(input: Array<I>): ParseResult<Array<I>, I, E, A> {
        val machine = GenericStackMachine(instr)
        machine.input = input
        machine.execute()

        val remaining = machine.input.copyOfRange(machine.inputOffset, machine.input.size)

        return if (machine.status == ParseStatus.Ok)
            ParseResult.Done(machine.pop().unsafe(), remaining)
        else
            ParseResult.Failure(machine.finalError, remaining)
    }

    override fun parseStreaming(initial: Array<I>): ParseResult<Array<I>, I, E, A> {
        val machine = GenericStackMachine(instr.copy()).also { it.acceptMoreInput = true }

        machine.input = initial
        machine.execute()

        return toParseResult(machine)
    }

    private fun toParseResult(machine: GenericStackMachine<I, E>): ParseResult<Array<I>, I, E, A> =
        when (machine.status) {
            ParseStatus.Ok -> {
                val rem = machine.input.copyOfRange(machine.inputOffset, machine.input.size)
                ParseResult.Done(machine.pop().unsafe(), rem)
            }
            ParseStatus.Err -> {
                val rem = machine.input.copyOfRange(machine.inputOffset, machine.input.size)
                ParseResult.Failure(machine.finalError, rem)
            }
            else -> ParseResult.Partial { parsePartial(machine, it) }
        }

    private fun parsePartial(machine: GenericStackMachine<I, E>, inp: Array<I>?): ParseResult<Array<I>, I, E, A> {
        if (inp == null) machine.acceptMoreInput = false
        else machine.feed(inp)

        machine.reexecute()
        return toParseResult(machine)
    }

    override fun parseTest(input: Array<I>, fromE: (E) -> String) = when (val res = parse(input)) {
        is ParseResult.Done -> println(res)
        is ParseResult.Partial -> throw IllegalStateException("CompiledParser.parseTest returned a partial parse result. This is a bug!")
        is ParseResult.Failure -> TODO("Generic parser failed, pretty printing is not yet enabled for generic parsing")
    }
}

internal class GenericStackMachine<I, E> internal constructor(instr: Array<Instruction<I, E>>) :
    AbstractStackMachine<I, E>(instr) {

    internal var input: Array<I> = emptyArray<Any?>().unsafe()

    fun feed(inp: Array<I>): Unit {
        input += inp
    }

    override fun hasMore(): Boolean = inputOffset < input.size
    override fun take(): I = input[inputOffset]
    override fun hasMore(n: Int): Boolean = inputOffset < input.size - (n - 1)
    override fun slice(start: Int, end: Int): Array<I> =
        input.slice(start until end).toTypedArray<Any?>().unsafe()
}
