package parsley.settings

import parsley.backend.BackendOptimiseStep
import parsley.backend.CodeGenStep
import parsley.backend.Instruction
import parsley.backend.Method
import parsley.collections.IntMap

data class BackendSettings<I, E>(
    val codegenSteps: List<CodeGenStep<I, E>>,
    val optimiseSteps: List<BackendOptimiseStep<I, E>>
)

fun <I, E> CompilerSettings<I, E>.addCodegenStep(step: CodeGenStep<I, E>): CompilerSettings<I, E> =
    copy(backend = backend.copy(codegenSteps = listOf(step) + backend.codegenSteps))

fun <I, E> CompilerSettings<I, E>.addOptimiseStep(step: BackendOptimiseStep<I, E>): CompilerSettings<I, E> =
    copy(backend = backend.copy(optimiseSteps = listOf(step) + backend.optimiseSteps))

