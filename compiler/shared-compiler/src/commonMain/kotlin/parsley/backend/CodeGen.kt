package parsley.backend

import parsley.AnyParser
import parsley.frontend.ParserF
import parsley.settings.CompilerSettings
import parsley.settings.SubParsers

@OptIn(ExperimentalStdlibApi::class)
class CodeGenStep<I, E>(
    private val f: suspend DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.(
        p: ParserF<I, E, Any?>,
        ctx: CodeGenContext<I, E>,
        settings: CompilerSettings<I, E>
    ) -> Boolean
) {
    operator fun component1() = f
}

interface CodeGenContext<I, E> {
    var discard: Boolean

    fun collect(): Method<I, E>

    operator fun plusAssign(instr: Instruction<I, E>): Unit
    fun mkLabel(): Int
    fun addSubParser(p: AnyParser<I, E>): Int
    fun getSubParser(label: Int): AnyParser<I, E>
    fun discardSubParser(label: Int): Int
    fun getSubParsers(): SubParsers<I, E>
}
