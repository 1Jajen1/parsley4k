package parsley.internal.backend.optimise

import parsley.internal.backend.FuseMap
import parsley.internal.backend.Instruction
import parsley.internal.backend.Method
import parsley.internal.backend.Program
import parsley.internal.backend.Pushes
import parsley.internal.backend.instructions.Apply
import parsley.internal.backend.instructions.Call
import parsley.internal.backend.instructions.Map
import parsley.internal.backend.instructions.Push
import parsley.internal.unsafe

internal fun <I, E> Program<I, E>.optimise(highestLabel: Int): Program<I, E> {
    var (mainP, subs) = this//.also { it.toFinalProgram().also(::println) }
    var labelC = highestLabel
    // 1. Instruction level optimisation: Iterate through all and offer them to matchers which modify them
    // 1.1. Zap all occurrences of Pop. TODO During codegen!
    // 1.2. Replace Tell and Seek where possible. Since we almost always know how much we consume.
    // 1.3. Zap all error throwing methods where possible (inside many for example)
    // 1.4. Remove useless jumps
    // 2. Method level optimisation: Inlining
    // 3. Whole program optimisation: Dead code removal
    inlinePass(mainP, subs).also { (m, s) ->
        mainP = m
        subs = s
    }

    // fuse instructions
    mainP = mainP.fuseInstructions()
    subs = subs.mapValues { (_, m) -> m.fuseInstructions() }

    inlinePass(mainP, subs).also { (m, s) ->
        mainP = m
        subs = s
    }

    return Program(mainP, subs)//.also { it.toFinalProgram().also(::println) }
}

internal fun <I, E> inlinePass(
    main: Method<I, E>,
    subs: kotlin.collections.Map<Int, Method<I, E>>
): Pair<Method<I, E>, kotlin.collections.Map<Int, Method<I, E>>> {
    val toInline = subs.filter { (_, v) -> v.checkInline() }.keys
    val main = main.instr.inline(toInline, subs)
    val subs = subs.mapValues { (_, v) -> v.instr.inline(toInline, subs) }
    return main to subs
}

internal fun <I, E> List<Instruction<I, E>>.inline(
    toInline: Set<Int>,
    subs: kotlin.collections.Map<Int, Method<I, E>>
): Method<I, E> {
    val mutlist = toMutableList()
    var curr = 0
    while (curr < mutlist.size) {
        val el = mutlist[curr++]
        if (el is Call && toInline.contains(el.to)) {
            mutlist.removeAt(--curr)
            mutlist.addAll(curr, subs[el.to]!!.instr)
        }
    }
    return Method(mutlist)
}

internal fun <I, E> Method<I, E>.checkInline(): Boolean =
    instr.size <= 2 // TODO

internal fun <I, E> Method<I, E>.fuseInstructions(): Method<I, E> {
    val xs = instr.toMutableList()

    var curr = 0
    while (curr < xs.size - 2) {
        val e1 = xs[curr]
        val e2 = xs[curr + 1]
        val e3 = xs[curr + 2]
        when {
            e3 is Map -> {
                if (e2 is FuseMap<*, *>) {
                    xs.removeAt(curr + 1)
                    xs.removeAt(curr + 1)
                    xs.add(curr + 1, e2.fuseWith(e3.f.unsafe()).unsafe())
                }
            }
            e1 is Push && (e2 is Pushes || e2 is Call) && e3 is Apply -> {
                // Additional things to consider if e2 is special
                if (e2 is FuseMap<*, *>) {
                    xs.removeAt(curr)
                    xs.removeAt(curr)
                    xs.removeAt(curr)
                    xs.add(curr, e2.fuseWith(e1.el.unsafe()).unsafe())
                }
            }
        }
        curr++
    }

    return Method(xs)
}
