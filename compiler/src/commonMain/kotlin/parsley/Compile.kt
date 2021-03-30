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
import parsley.frontend.DefaultRelabelStep
import parsley.frontend.InsertLetStep
import parsley.frontend.LetBoundStep
import parsley.frontend.OptimiseStep
import parsley.frontend.ParserF
import parsley.frontend.RelabelStep
import parsley.frontend.findLetBound
import parsley.frontend.insertLets
import parsley.frontend.optimise

// TODO Parameterize steps
fun <I, E, A> ParserF<I, E, A>.compile(
    settings: CompilerSettings<I, E> = defaultSettings()
): Method<I, E> {
    if (settings.logging.printInitialAST) {
        println("Parser:AST:initial")
        println(this)
    }

    // Step 1: Replace let bound parsers. This prevents problems with recursion inside Lazy(...)
    var (mainP, subs, highestL) = preprocess(settings)

    if (settings.logging.printFixedAST) {
        println("Parser:AST:fixed")
        println("Main parser:")
        println(mainP)
        println("Sub parsers:")
        subs.forEach { key, parserF ->
            println("Parser label: $key")
            println(parserF)
        }
    }

    // Step 2: Optimise AST
    mainP = mainP.optimise(subs, settings.frontend.optimiseSteps, settings)
    subs = subs.mapValues { v -> v.optimise(subs, settings.frontend.optimiseSteps, settings) }

    if (settings.logging.printOptimisedAST) {
        println("Parser:AST:optimised")
        println("Main parser:")
        println(mainP)
        println("Sub parsers:")
        subs.forEach { key, parserF ->
            println("Parser label: $key")
            println(parserF)
        }
    }

    // Step 3: Code gen
    val (mainI, subI, l) = mainP.codeGen(subs, highestL, settings.backend.codegenSteps)

    if (settings.logging.printInitialInstr) {
        println("Parser:Instr:initial")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method.showInstructions())
        }
    }

    // Step 4: Optimise instructions
    // TODO Clean up into fuseable single steps
    inlinePass(mainI, subI)

    if (settings.logging.printInlinedInstr) {
        println("Parser:Instr:inline:1")
        println("Main method:")
        println(mainI.showInstructions())
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method.showInstructions())
        }
    }

    // TODO Concat like inlinePass
    mainI.rewriteRules()
    subI.forEach { _, v -> v.rewriteRules() }

    if (settings.logging.printRewrittenRulesInstr) {
        println("Parser:Instr:rewrite")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method.showInstructions())
        }
    }

    inlinePass(mainI, subI)

    if (settings.logging.printInlinedInstr) {
        println("Parser:Instr:inline:2")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method.showInstructions())
        }
    }

    settings.backend.optimiseSteps.fold(l) { acc, f -> f(mainI, subI, acc) }

    if (settings.logging.printOptimisedInstr) {
        println("Parser:Instr:optimise")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method.showInstructions())
        }
    }

    inlinePass(mainI, subI)

    if (settings.logging.printInlinedInstr) {
        println("Parser:Instr:inline:3")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method.showInstructions())
        }
    }

    // Step 5: Assemble final program
    return assemble(mainI, subI).also {
        if (settings.logging.printFinalInstr) {
            println("Parser:Instr:final")
            println(it.showInstructions())
        }
    }
}

fun <I, E> ParserF<I, E, Any?>.preprocess(settings: CompilerSettings<I, E>): Triple<ParserF<I, E, Any?>, IntMap<ParserF<I, E, Any?>>, Int> {
    val (letbound, recursives) = findLetBound(settings.frontend.letfinderSteps)
    return insertLets(letbound, recursives, settings.frontend.insertletSteps)
}

data class CompilerSettings<I, E>(
    val frontend: FrontendSettings<I, E>,
    val backend: BackendSettings<I, E>,
    val optimise: OptimiseSettings<I, E>,
    val logging: LogSettings
) {
    fun addLetFindStep(letStep: LetBoundStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(letfinderSteps = arrayOf(letStep) + frontend.letfinderSteps))

    fun addLetInsertStep(letStep: InsertLetStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(insertletSteps = arrayOf(letStep) + frontend.insertletSteps))

    fun addOptimiseStep(step: OptimiseStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(optimiseSteps = arrayOf(step) + frontend.optimiseSteps))

    fun addCodegenStep(step: CodeGenStep<I, E>): CompilerSettings<I, E> =
        copy(backend = backend.copy(codegenSteps = arrayOf(step) + backend.codegenSteps))

    fun addOptimiseStep(step: BackendOptimiseStep<I, E>): CompilerSettings<I, E> =
        copy(backend = backend.copy(optimiseSteps = arrayOf(step) + backend.optimiseSteps))

    fun addRelabelStep(step: RelabelStep<I, E>): CompilerSettings<I, E> =
        copy(frontend = frontend.copy(relabeSteps = arrayOf(step) + frontend.relabeSteps))

    fun hideWarnings(): CompilerSettings<I, E> =
        copy(logging = logging.copy(showWarnings = false))

    fun printAllStages(): CompilerSettings<I, E> =
        printInitialAST()
            .printFixedAST()
            .printOptimisedAST()
            .printInitialInstr()
            .printInlinedInstr()
            .printRewrittenInstr()
            .printOptimisedInstr()
            .printFinalInstr()

    fun printInitialAST(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printInitialAST = true))

    fun printFixedAST(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printFixedAST = true))

    fun printOptimisedAST(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printOptimisedAST = true))

    fun printInitialInstr(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printInitialInstr = true))

    fun printInlinedInstr(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printInlinedInstr = true))

    fun printRewrittenInstr(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printRewrittenRulesInstr = true))

    fun printOptimisedInstr(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printOptimisedInstr = true))

    fun printFinalInstr(): CompilerSettings<I, E> =
        copy(logging = logging.copy(printFinalInstr = true))
}

data class FrontendSettings<I, E>(
    val letfinderSteps: Array<LetBoundStep<I, E>>,
    val insertletSteps: Array<InsertLetStep<I, E>>,
    val optimiseSteps: Array<OptimiseStep<I, E>>,
    val relabeSteps: Array<RelabelStep<I, E>>
)

typealias Method<I, E> = MutableList<Instruction<I, E>>

private fun <I, E> Method<I, E>.showInstructions(): String =
    withIndex().map { (i, instr) -> "($i, $instr)" }.joinToString(", ")
        .let { "[$it]" }

typealias BackendOptimiseStep<I, E> =
        Method<I, E>.(IntMap<Method<I, E>>, Int) -> Int

data class BackendSettings<I, E>(
    val codegenSteps: Array<CodeGenStep<I, E>>,
    val optimiseSteps: Array<BackendOptimiseStep<I, E>>
)

data class OptimiseSettings<I, E>(
    val analyseSatisfy: AnalyseSatisfy<I> = AnalyseSatisfy()
)

data class LogSettings(
    val showWarnings: Boolean = true,
    val printInitialAST: Boolean = false,
    val printFixedAST: Boolean = false,
    val printOptimisedAST: Boolean = false,
    val printInitialInstr: Boolean = false,
    val printInlinedInstr: Boolean = false,
    val printRewrittenRulesInstr: Boolean = false,
    val printOptimisedInstr: Boolean = false,
    val printFinalInstr: Boolean = false
)

fun <I, E> defaultSettings(): CompilerSettings<I, E> = CompilerSettings(
    frontend = defaultFrontendSettings(),
    backend = defaultBackendSettings(),
    optimise = defaultOptimiseSettings(),
    logging = defaultLogSettings()
)

fun <I, E> defaultFrontendSettings(): FrontendSettings<I, E> = FrontendSettings(
    letfinderSteps = arrayOf(DefaultLetBoundStep()),
    insertletSteps = arrayOf(DefaultInsertLetStep()),
    optimiseSteps = arrayOf(DefaultOptimiseStep()),
    relabeSteps = arrayOf(DefaultRelabelStep())
)

fun <I, E> defaultBackendSettings(): BackendSettings<I, E> = BackendSettings(
    codegenSteps = arrayOf(DefaultCodeGenStep()),
    optimiseSteps = arrayOf()
)

fun <I, E> defaultOptimiseSettings(): OptimiseSettings<I, E> = OptimiseSettings()

fun defaultLogSettings(): LogSettings = LogSettings()

class AnalyseSatisfy<I>(val f: Sequence<I> = emptySequence())

/**
 * TODO:
 *
 * Optimisation ideas:
 * - Collect all key chars (Single and also Satisfy if O2 is applied) and preprocess with simd instructions
 *  - Key chars for json are n, t, f, 0-9, -, [, ], {, },  , \n, \r, \t, \,
 *  - Since we also know which of these are only ever skipped we can preprocess and remove them as well
 * - Radix tries in jump table instructions to further eliminate the need to then check prefixes
 *  - Idea:
 *   - Use IntMap and have values be further IntMaps. Use Char.toInt as key. Lookups should be almost constant
 *  - Needs good heuristic for how large these tries should be
 *  => Radix tries perform far worse than chained satisfy etc simply because function application is often (not always)
 *   significantly faster than dictionary lookups.
 * - Fuse Alt(Satisfy, Satisfy/Alt(Satisfy, Satisfy)) into one instruction. This may be beneficial if there are fast
 *  and incorrect check functions and fallback slow and correct ones. E.g. isLatin1 orElse isLetter where isLatin1
 *  uses a range check and isLetter a dict lookup.
 *  - Also even if this fast/slow thinking does not apply, making it one instruction still reduces overhead of doing
 *   stack and error recovery work (which is almost free so this is likely just a small win)
 */
/**
 * TODO List:
 * DONE:
 * - Split of string compilation from code gen and only replace instructions after the fact
 *  - Investigate if that causes any problems with optimization...
 * - Split into modules!
 * - Error messages
 * TODO:
 * - Investigate JumpGoodCatch vs JumpGood, Catch
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
 *  - recursive could maybe be just lazy. In fact I can probably add overloaded constructors accepting both
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
