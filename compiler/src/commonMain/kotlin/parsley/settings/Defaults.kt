package parsley.settings

import parsley.backend.defaultCodeGen
import parsley.frontend.OptimiseStage
import parsley.frontend.defaultInsertLetStep
import parsley.frontend.defaultLetFinderStep
import parsley.frontend.simplifier.DefaultRelabelStep
import parsley.frontend.simplifier.constantFold
import parsley.frontend.simplifier.normalForm
import parsley.frontend.simplifier.relabel
import parsley.frontend.simplifier.rewrite

fun <I, E> defaultSettings(): CompilerSettings<I, E> = CompilerSettings(
    frontend = defaultFrontendSettings(),
    backend = defaultBackendSettings(),
    logging = defaultLogSettings()
)

fun <I, E> defaultFrontendSettings(): FrontendSettings<I, E> = FrontendSettings(
    letFinderSteps = listOf(defaultLetFinderStep()),
    insertLetSteps = listOf(defaultInsertLetStep()),
    optimiseSteps = mapOf(
        OptimiseStage.Normalise to listOf(relabel(), normalForm<I, E>()),
        OptimiseStage.Simplifier to listOf(relabel(), constantFold<I, E>(), rewrite<I, E>())
    ),
    relabelSteps = listOf(DefaultRelabelStep()),
    rebuildPredicate = RebuildPredicate()
)

fun <I, E> defaultBackendSettings(): BackendSettings<I, E> = BackendSettings(
    codegenSteps = listOf(defaultCodeGen()),
    optimiseSteps = listOf()
)
