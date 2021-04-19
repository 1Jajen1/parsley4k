package parsley.backend

import parsley.CompilerSettings
import parsley.Method
import parsley.collections.IntMap
import parsley.collections.IntSet
import parsley.frontend.ParserF

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> ParserF<I, E, Any?>.codeGen(
    subs: IntMap<ParserF<I, E, Any?>>,
    label: Int,
    codeGenSteps: Array<CodeGenStep<I, E>>,
    settings: CompilerSettings<I, E>
): Triple<Method<I, E>, IntMap<Method<I, E>>, Int> {
    val ctx = CodeGenContext(subs, label)

    // Generate main and sub parsers
    DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
        codeGenSteps.forEach { s ->
            s.run { if (step(p, ctx, settings)) return@DeepRecursiveFunction }
        }
        throw IllegalStateException("No step could generate code for $p")
    }(this)
    val main = ctx.collect()
    val sub = subs.mapValues { p ->
        DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
            codeGenSteps.forEach { s ->
                s.run { if (step(p, ctx, settings)) return@DeepRecursiveFunction }
            }
            throw IllegalStateException("No step could generate code for $p")
        }(p)
        ctx.collect()
    }

    // Generate discarded parsers
    // TODO This is utterly broken. Right now it does not account for if discardedSubParsers > 1 and if gen adds another one
    val proccessed = IntSet.empty()
    while (ctx.discardedSubParsers.isNotEmpty() && ctx.discardedSubParsers.size() != proccessed.size()) {
        val (key, value) = ctx.discardedSubParsers.find { k, _ -> k !in proccessed }
        proccessed.add(key)
        ctx.discard = true
        DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
            codeGenSteps.forEach { s ->
                s.run { if (step(p, ctx, settings)) return@DeepRecursiveFunction }
            }
        }(subs[key])
        sub[value] = ctx.collect()
    }

    return Triple(main, sub, ctx.highestL)
}

private inline fun <V> IntMap<V>.find(f: (Int, V) -> Boolean): Pair<Int, V> {
    val copy = copy()
    while (true) {
        val (k, v) = copy.first()
        copy.remove(k)
        if (f(k, v)) return k to v
    }
}

class CodeGenContext<I, E>(
    val subs: IntMap<ParserF<I, E, Any?>>,
    internal var highestL: Int
) {
    private var compiled = mutableListOf<Instruction<I, E>>()

    fun collect(): Method<I, E> = compiled.also {
        compiled = mutableListOf()
        discard = false
    }

    var discard = false

    internal val discardedSubParsers = IntMap.empty<Int>()

    fun mkLabel(): Int = highestL++

    operator fun plusAssign(i: Instruction<I, E>): Unit {
        compiled.add(i)
    }
}

inline fun <I, E> CodeGenContext<I, E>.withDiscard(v: Boolean = true, f: () -> Unit) {
    val prev = discard
    discard = v
    f()
    discard = prev
}

interface CodeGenStep<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.step(
        p: ParserF<I, E, Any?>,
        ctx: CodeGenContext<I, E>,
        settings: CompilerSettings<I, E>
    ): Boolean
}

