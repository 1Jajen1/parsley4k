package parsley.frontend

import parsley.AnyParser
import parsley.settings.CompilerSettings
import parsley.settings.SubParsers


sealed class OptimiseStage {
    object Normalise : OptimiseStage()
    object Simplifier : OptimiseStage()
    object FloatIn : OptimiseStage()
}

@OptIn(ExperimentalStdlibApi::class)
class TransformStep<I, E>(
    private val f: suspend DeepRecursiveScope<AnyParser<I, E>, AnyParser<I, E>>.(
        parser: AnyParser<I, E>,
        subParsers: SubParsers<I, E>,
        settings: CompilerSettings<I, E>
    ) -> AnyParser<I, E>
) {
    operator fun component1() = f
}

interface RelabelStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, ParserF<I, E, Any?>>.step(
        p: ParserF<I, E, Any?>,
        lbl: String?
    ): ParserF<I, E, Any?>?
}
