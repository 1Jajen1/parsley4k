package parsley

import parsley.backend.CodeGenStep
import parsley.backend.DefaultCodeGenStep
import parsley.backend.Instruction
import parsley.backend.assemble
import parsley.backend.codeGen
import parsley.backend.inlinePass
import parsley.backend.rewriteRules
import parsley.collections.IntMap
import parsley.frontend.DefaultInsertLetStep
import parsley.frontend.DefaultLetBoundStep
import parsley.frontend.DefaultOptimiseStep
import parsley.frontend.InsertLetStep
import parsley.frontend.LetBoundStep
import parsley.frontend.OptimiseStep
import parsley.frontend.ParserF
import parsley.frontend.findLetBound
import parsley.frontend.insertLets
import parsley.frontend.optimise

// TODO Parameterize steps
fun <I, E, A> ParserF<I, E, A>.compile(
    settings: CompilerSettings<I, E> = defaultSettings()
): Method<I, E> {
    // Step 1: Replace let bound parsers. This prevents problems with recursion inside Lazy(...)
    var (mainP, subs, highestL) = preprocess(settings)

    // Step 2: Optimise AST
    mainP = mainP.optimise(settings.frontend.optimiseSteps, settings)
    subs = subs.mapValues { v -> v.optimise(settings.frontend.optimiseSteps, settings) }

    // Step 3: Code gen
    val (mainI, subI, l) = mainP.codeGen(subs, highestL, settings.backend.codegenSteps)

    // Step 4: Optimise instructions
    // TODO Clean up into fuseable single steps
    inlinePass(mainI, subI)
    // TODO Concat like inlinePass
    mainI.rewriteRules()
    subI.forEach { _, v -> v.rewriteRules() }
    inlinePass(mainI, subI)
    settings.backend.optimiseSteps.fold(l) { acc, f -> f(mainI, subI, acc) }
    inlinePass(mainI, subI)

    // Step 5: Assemble final program
    return assemble(mainI, subI)
}

fun <I, E> ParserF<I, E, Any?>.preprocess(settings: CompilerSettings<I, E>): Triple<ParserF<I, E, Any?>, IntMap<ParserF<I, E, Any?>>, Int> {
    val (letbound, recursives) = findLetBound(settings.frontend.letfinderSteps)
    return insertLets(letbound, recursives, settings.frontend.insertletSteps)
}

data class CompilerSettings<I, E>(
    val frontend: FrontendSettings<I, E>,
    val backend: BackendSettings<I, E>,
    val optimise: OptimiseSettings<I, E>
) {
    fun addLetFindStep(letStep: LetBoundStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(letfinderSteps = frontend.letfinderSteps + letStep))

    fun addLetInsertStep(letStep: InsertLetStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(insertletSteps = frontend.insertletSteps + letStep))

    fun addOptimiseStep(step: OptimiseStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(optimiseSteps = frontend.optimiseSteps + step))

    fun addCodegenStep(step: CodeGenStep<I, E>): CompilerSettings<I, E> =
        copy(backend = backend.copy(codegenSteps = backend.codegenSteps + step))

    fun addOptimiseStep(step: BackendOptimiseStep<I, E>): CompilerSettings<I, E> =
        copy(backend = backend.copy(optimiseSteps = backend.optimiseSteps + step))
}

data class FrontendSettings<I, E>(
    val letfinderSteps: Array<LetBoundStep<I, E>>,
    val insertletSteps: Array<InsertLetStep<I, E>>,
    val optimiseSteps: Array<OptimiseStep<I, E>>
)

typealias Method<I, E> = MutableList<Instruction<I, E>>

typealias BackendOptimiseStep<I, E> =
        Method<I, E>.(IntMap<Method<I, E>>, Int) -> Int

data class BackendSettings<I, E>(
    val codegenSteps: Array<CodeGenStep<I, E>>,
    val optimiseSteps: Array<BackendOptimiseStep<I, E>>
)

data class OptimiseSettings<I, E>(
    val analyseSatisfy: AnalyseSatisfy<I> = AnalyseSatisfy()
)

fun <I, E> defaultSettings(): CompilerSettings<I, E> = CompilerSettings(
    frontend = defaultFrontendSettings(),
    backend = defaultBackendSettings(),
    optimise = defaultOptimiseSettings()
)

fun <I, E> defaultFrontendSettings(): FrontendSettings<I, E> = FrontendSettings(
    letfinderSteps = arrayOf(DefaultLetBoundStep()),
    insertletSteps = arrayOf(DefaultInsertLetStep()),
    optimiseSteps = arrayOf(DefaultOptimiseStep())
)

fun <I, E> defaultBackendSettings(): BackendSettings<I, E> = BackendSettings(
    codegenSteps = arrayOf(DefaultCodeGenStep()),
    optimiseSteps = arrayOf()
)

fun <I, E> defaultOptimiseSettings(): OptimiseSettings<I, E> = OptimiseSettings()

class AnalyseSatisfy<I>(val f: Sequence<I> = emptySequence())

/**
 * TODO:
 *
 * Optimisation ideas:
 * General:
 * EASY:
 * - SatisfyN_, SingleN_ are a slight improvement due to length checking and one instruction, next is Many_(SatisfyN_)
 *  which could be one instruction as well, just needs to two loops
 * - JumpTables could generate Consume(n: Int) instructions instead of the first Single/Satisfy, or just skip them
 *  altogether and consume inside the JumpTable.
 *  - Consuming is fine if we have no fallback or don't jump straight into Many
 *   - If we have a fallback we need to generate a Unconsume(n: Int) instruction that moves the input offset back n steps
 *   - If we jump into Many with discard the consumed input does not matter
 *   - If we jump into Many without discard we cannot consume in the JumpTable
 * Char/String:
 * HARD:
 * - Generate sets of accepted and rejected chars from (Char) -> Boolean
 *  - This can then be used by jump tables and/or be used to generate fused satisfy instructions
 *   - Alt(Satisfy, Single) may be collapsed to just Satisfy with no loss of information and a fast CharSet lookup
 *   - This lookup could be faster than the initial satisfy, but may also not be, so enable only if aggressive optimization is used
 * - Collect all key chars (Single and also Satisfy if O2 is applied) and preprocess with simd like instructions
 *  - Key chars for json are n, t, f, 0-9, -, [, ], {, },  , \n, \r, \t, \,
 *  - Since we also know which of these are only ever skipped we can preprocess and remove them as well
 * - Radix tries in jump table instructions to further eliminate the need to then check prefixes
 *  - Idea:
 *   - Use IntMap and have values be further IntMaps. Use Char.toInt as key. Lookups should be almost constant
 *  - Needs good heuristic for how large these tries should be
 */
/**
 * TODO List:
 * DONE:
 * - Split of string compilation from code gen and only replace instructions after the fact
 *  - Investigate if that causes any problems with optimization...
 * - Split into modules!
 * TODO:
 * - Add Byte parser
 * - Benchmark Generic vs Byte vs Char
 * - Optimise compilation pipeline:
 *  - Benchmark compilation, profile and improve it.
 *  - But first I probably need to restructure it a bit
 * - Further optimise generated instructions
 *  - Benchmark different simple parsers
 *  - Setup ci and benchmark comparisons
 * - Provide a nicer api
 *  - Investigate dsl like betterParser although I do believe that is over the top
 *  - recursive could maybe be just lazy
 * - Error messages
 *  - AFTER benchmark pipeline is set up to spot regressions early
 * - Streaming parsing
 *  - Offer some built in streaming apis to cover most normal use cases:
 *   - likely needs to be platform specific...
 *  - Idea:
 *   - When needInput() is called: reset the program counter and throw a constant exception
 *      then when catching, return a parse result indicating we are not done.
 *   - Offer push(input) to add input to the parser
 *    - This needs some analysis as to where the old string can be discarded and where it has to be retained (and to which point)
 *   - Stateful methods like SatisfyMany may need to store their state in a local var or reset the input they consumed and state they changed
 */

