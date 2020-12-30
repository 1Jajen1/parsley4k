package parsley.string

import parsley.CompiledParser
import parsley.ErrorItem
import parsley.ParseError
import parsley.ParseResult
import parsley.Parser
import parsley.internal.backend.CanFail
import parsley.internal.backend.CodeGen
import parsley.internal.backend.CodeGenContext
import parsley.internal.backend.CodeGenFunc
import parsley.internal.backend.Instruction
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.backend.string.MatchChar
import parsley.internal.backend.string.StringStackMachine
import parsley.internal.backend.toProgram
import parsley.internal.frontend.ParserF
import parsley.internal.frontend.findLetBound
import parsley.internal.frontend.insertLets
import parsley.internal.frontend.optimise

class CompiledStringParser<E, A> internal constructor(override val machine: StringStackMachine<E>) :
    CompiledParser<Char, CharArray, E, A>() {
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

internal class StringCodeGen<E> : CodeGenFunc<Char, E> {
    override fun <A> ParserF<Char, E, CodeGen<Char, E>, A>.apply(context: CodeGenContext): List<Instruction<Char, E>>? =
        when (this) {
            is ParserF.Single<*> -> listOf(MatchChar(this.i as Char))
            else -> null
        }
}

fun <E, A> Parser<Char, E, A>.compile(): CompiledStringParser<E, A> {
    val (bound, recs) = findLetBound()
    val (mainP, subs, highestLabel) = insertLets(bound, recs)

    val prog = Triple(mainP.optimise(), subs.mapValues { (_, v) -> v.optimise() }, highestLabel)
        .toProgram(StringCodeGen())
    return CompiledStringParser(StringStackMachine(prog.toFinalProgram().toTypedArray()))
}
