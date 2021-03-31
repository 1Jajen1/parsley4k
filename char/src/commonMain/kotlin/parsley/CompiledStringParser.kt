package parsley

import parsley.backend.Instruction
import parsley.backend.ParseStatus
import parsley.backend.StringStackMachine

class CompiledStringParser<E, A>(val instr: Array<Instruction<Char, E>>): CompiledParser<CharArray, Char, E, A> {
    override fun parse(input: CharArray): ParseResult<CharArray, Char, E, A> {
        val machine = StringStackMachine(instr.copy()).also { it.acceptMoreInput = false }
        machine.input = input
        machine.execute()
        if (machine.warnBoxing) println("Parser compiled to an instruction set that used boxing operations. Check ### for a guide how to avoid this.")

        val remaining = machine.input.copyOfRange(machine.inputOffset, machine.input.size)

        return if (machine.status == ParseStatus.Ok)
            ParseResult.Done(machine.pop().unsafe(), remaining)
        else
            ParseResult.Failure(machine.finalError, remaining)
    }

    override fun parseStreaming(initial: CharArray): ParseResult<CharArray, Char, E, A> {
        val machine = StringStackMachine(instr.copy()).also { it.acceptMoreInput = true }

        machine.input = initial
        machine.execute()

        return toParseResult(machine)
    }

    private fun toParseResult(machine: StringStackMachine<E>): ParseResult<CharArray, Char, E, A> =
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

    private fun parsePartial(machine: StringStackMachine<E>, inp: CharArray?): ParseResult<CharArray, Char, E, A> {
        if (inp == null) machine.acceptMoreInput = false
        else machine.feed(inp)

        machine.reexecute()
        if (machine.warnBoxing) println("Parser compiled to an instruction set that used boxing operations. Check ### for a guide how to avoid this.")

        return toParseResult(machine)
    }

    override fun parseTest(input: CharArray, fromE: (E) -> String) = when (val res = parse(input)) {
        is ParseResult.Done -> println(res)
        is ParseResult.Partial -> throw IllegalStateException("CompiledParser.parseTest returned a partial parse result. This is a bug!")
        is ParseResult.Failure -> println(res.error.pretty(input, fromE))
    }
}
