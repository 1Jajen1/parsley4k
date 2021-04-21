package parsley.backend

import parsley.AnyParser
import parsley.collections.IntMap
import parsley.collections.IntSet
import parsley.frontend.ParserF
import parsley.settings.CompilerSettings
import parsley.settings.SubParsers

@OptIn(ExperimentalStdlibApi::class)
fun <I, E> ParserF<I, E, Any?>.codeGen(
    subs: SubParsers<I, E>,
    label: Int,
    codeGenSteps: List<CodeGenStep<I, E>>,
    settings: CompilerSettings<I, E>
): Triple<Method<I, E>, IntMap<Method<I, E>>, Int> {
    val ctx = CodeGenContextImpl(subs, label)

    // Generate main and sub parsers
    DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
        codeGenSteps.forEach { (s) ->
            if (s(p, ctx, settings)) return@DeepRecursiveFunction
        }
        throw IllegalStateException("No step could generate code for $p")
    }(this)
    val main = ctx.collect()
    val visited = IntSet.empty()
    val sub = IntMap.empty<Method<I, E>>()
    while (visited.size() != subs.size()) {
        subs.forEach { i, p ->
            if (i !in visited) {
                visited.add(i)
                DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
                    codeGenSteps.forEach { (s) ->
                        if (s(p, ctx, settings)) return@DeepRecursiveFunction
                    }
                    throw IllegalStateException("No step could generate code for $p")
                }(p)
                sub[i] = ctx.collect()
            }
        }
    }

    // Generate discarded parsers
    // TODO This is utterly broken. Right now it does not account for if discardedSubParsers > 1 and if gen adds another one
    val proccessed = IntSet.empty()
    while (ctx.discardedSubParsers.isNotEmpty() && ctx.discardedSubParsers.size() != proccessed.size()) {
        val (key, value) = ctx.discardedSubParsers.find { k, _ -> k !in proccessed }
        proccessed.add(key)
        ctx.discard = true
        DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
            codeGenSteps.forEach { (s) ->
                if (s(p, ctx, settings)) return@DeepRecursiveFunction
                throw IllegalStateException("No step could generate code for $p")
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

class CodeGenContextImpl<I, E>(
    val subs: SubParsers<I, E>,
    var highestL: Int
) : CodeGenContext<I, E> {
    private var compiled = mutableListOf<Instruction<I, E>>()

    override fun collect(): Method<I, E> = Method(compiled.also {
        compiled = mutableListOf()
        discard = false
    })

    override var discard = false

    val discardedSubParsers = IntMap.empty<Int>()

    override fun mkLabel(): Int = highestL++

    override operator fun plusAssign(i: Instruction<I, E>): Unit {
        compiled.add(i)
    }

    override fun addSubParser(p: AnyParser<I, E>) =
        mkLabel().also { subs[it] = p }

    // TODO lookup discarded one here as well?
    override fun getSubParser(label: Int): AnyParser<I, E> = subs[label]

    override fun discardSubParser(label: Int): Int =
        if (label in discardedSubParsers) discardedSubParsers[label]
        else mkLabel().also { discardedSubParsers[label] = it }

    override fun getSubParsers(): SubParsers<I, E> = subs
}

inline fun <I, E> CodeGenContext<I, E>.withDiscard(v: Boolean = true, f: () -> Unit) {
    val prev = discard
    discard = v
    f()
    discard = prev
}
