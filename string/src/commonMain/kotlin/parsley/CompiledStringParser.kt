package parsley

import parsley.backend.Instruction
import parsley.backend.ParseStatus
import parsley.backend.StringStackMachine

class CompiledStringParser<E, A>(val instr: Array<Instruction<Char, E>>) {

    private fun Array<Instruction<Char, E>>.copy(): Array<Instruction<Char, E>> =
        copyOf().also { new ->
            new.forEachIndexed { i, instruction -> new[i] = instruction.copy() }
        }

    fun parse(input: CharArray): ParseResult<E, A> {
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

    fun parseStreaming(initial: CharArray): ParseResult<E, A> {
        val machine = StringStackMachine(instr.copy()).also { it.acceptMoreInput = true }

        machine.input = initial
        machine.execute()

        return toParseResult(machine)
    }

    private fun toParseResult(machine: StringStackMachine<E>): ParseResult<E, A> =
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

    private fun parsePartial(machine: StringStackMachine<E>, inp: CharArray): ParseResult<E, A> {
        if (inp.isEmpty()) machine.acceptMoreInput = false
        else machine.feed(inp)

        machine.reexecute()
        if (machine.warnBoxing) println("Parser compiled to an instruction set that used boxing operations. Check ### for a guide how to avoid this.")

        return toParseResult(machine)
    }
}

sealed class ParseResult<out E, out A> {
    class Done<A>(val result: A, val remaining: CharArray) : ParseResult<Nothing, A>()
    class Partial<E, A>(val f: (CharArray) -> ParseResult<E, A>) : ParseResult<E, A>()
    class Failure<E>(val error: ParseError<Char, E>, val remaining: CharArray) : ParseResult<E, Nothing>()

    fun pushChunk(chunk: CharArray): ParseResult<E, A> = when (this) {
        is Partial -> f(chunk)
        else -> this
    }

    fun pushEndOfInput(): ParseResult<E, A> = pushChunk(charArrayOf())

    inline fun <B> fold(
        onDone: (A, CharArray) -> B,
        onPartial: (f: (CharArray) -> ParseResult<E, A>) -> B,
        onFailure: (ParseError<Char, E>, CharArray) -> B
    ): B = when (this) {
        is Done -> onDone(result, remaining)
        is Partial -> onPartial(f)
        is Failure -> onFailure(error, remaining)
    }
}

internal expect inline fun CharArray.slice(start: Int, end: Int): CharArray

// extensions on top of default api
fun <E, A> CompiledStringParser<E, A>.parseOrNull(inp: CharArray): A? = when (val res = parse(inp)) {
    is ParseResult.Done -> res.result
    else -> null
}
