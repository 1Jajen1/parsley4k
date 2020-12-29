package parsley.string

import parsley.CompiledParser
import parsley.ParseResult
import parsley.Parser
import parsley.internal.backend.string.StringStackMachine
import parsley.internal.backend.toProgram
import parsley.internal.frontend.findLetBound
import parsley.internal.frontend.insertLets

class CompiledStringParser<E, A> internal constructor(override val machine: StringStackMachine<E>) : CompiledParser<Char, CharArray, E, A>() {
    override fun reset() {
        machine.input = charArrayOf()
        super.reset()
    }

    override fun getRemaining(): CharArray {
        return machine.input.sliceArray(machine.inputOffset until machine.input.size)
    }

    override fun setInput(arr: CharArray) {
        machine.input = arr
    }

    fun execute(str: String): ParseResult<Char, CharArray, E, A> = execute(str.toCharArray())
}

fun <E, A> Parser<Char, E, A>.compile(): CompiledStringParser<E, A> {
    val (bound, recs) = findLetBound()
    val prog = insertLets(bound, recs).toProgram()
    return CompiledStringParser(StringStackMachine(prog.toFinalProgram().also(::println)))
}
