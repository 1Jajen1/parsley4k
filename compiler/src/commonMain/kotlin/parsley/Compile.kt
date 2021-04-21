package parsley

import parsley.backend.Method
import parsley.backend.assemble
import parsley.backend.codeGen
import parsley.backend.inlinePass
import parsley.backend.rewriteRules
import parsley.frontend.ParserF
import parsley.frontend.findLetBound
import parsley.frontend.insertLets
import parsley.frontend.optimise
import parsley.settings.CompilerSettings
import parsley.settings.SubParsers
import parsley.settings.defaultSettings

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
    subs = subs.mapValues { v -> v.optimise(subs, settings.frontend.optimiseSteps, settings) }.let(::SubParsers)

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
    val (mainI, subI, l) = mainP.codeGen(subs, highestL, settings.backend.codegenSteps, settings)

    if (settings.logging.printInitialInstr) {
        println("Parser:Instr:initial")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method)
        }
    }

    // Step 4: Optimise instructions
    // TODO Clean up into fuseable single steps
    inlinePass(mainI, subI)

    if (settings.logging.printInlinedInstr) {
        println("Parser:Instr:inline:1")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method)
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
            println(method)
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
            println(method)
        }
    }

    settings.backend.optimiseSteps.fold(l) { acc, (f) ->
        var nAcc = acc
        f(mainI, subI) { ++nAcc }
        nAcc
    }

    if (settings.logging.printOptimisedInstr) {
        println("Parser:Instr:optimise")
        println("Main method:")
        println(mainI)
        println("Sub methods:")
        subI.forEach { key, method ->
            println("Method label: $key")
            println(method)
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
            println(method)
        }
    }

    // Step 5: Assemble final program
    return assemble(mainI, subI).also {
        if (settings.logging.printFinalInstr) {
            println("Parser:Instr:final")
            println(it)
        }
    }
}

fun <I, E> ParserF<I, E, Any?>.preprocess(settings: CompilerSettings<I, E>): Triple<ParserF<I, E, Any?>, SubParsers<I, E>, Int> {
    val (letbound, recursives) = findLetBound(settings.frontend.letFinderSteps)
    return insertLets(letbound, recursives, settings.frontend.insertLetSteps)
}

/**
 * Clean up rules: Add rules object (recursive as we want to group them sometimes) and generate that from settings.
 * Do the same for code gen, so that we have one code gen function per path we can take (and groups as well if needed)
 * Better failure case analysis. Figure out what the behavior of an each alt in a failure case is and gen accordingly
 */

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
