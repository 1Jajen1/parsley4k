package parsley.internal.frontend

import arrow.Const
import arrow.ConstOf
import arrow.value
import parsley.Parser
import parsley.ParserOf
import parsley.fix

fun <I, E, A> Parser<I, E, A>.show(): String = cata<I, E, A, ConstOf<String>> {
    when (it) {
        is ParserF.Pure -> "Pure(${it.a})"
        is ParserF.Ap<ConstOf<String>, *, *> -> "${it.pF.value()} <*> ${it.pA.value()}"
        is ParserF.ApL<ConstOf<String>, *, *> -> "${it.pA.value()} <* ${it.pB.value()}"
        is ParserF.ApR<ConstOf<String>, *, *> -> "${it.pA.value()} *> ${it.pB.value()}"
        is ParserF.Select<ConstOf<String>, *, *> -> "${it.pEither.value()} <*? ${it.pIfLeft.value()}"
        is ParserF.Alt -> "${it.left.value()} <|> ${it.right.value()}"
        is ParserF.Empty -> "Empty"
        is ParserF.Satisfy<*> -> "Match(${it.expected})"
        is ParserF.Single<*> -> "Single(${it.i}, ${it.expected})"
        is ParserF.LookAhead -> "LookAhead(${it.p.value()})"
        is ParserF.NegLookAhead -> "NegLookAhead(${it.p.value()})"
        is ParserF.Attempt -> "Attempt(${it.p.value()})"
        is ParserF.Let -> "Let(${it.recursive}, ${it.sub})"
        is ParserF.Lazy -> "Lazy(...)"
    }.let(::Const)
}.value()

// TODO Recursion scheme?
@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> Parser<I, E, A>.findLetBound(): Pair<Set<Parser<I, E, Any?>>, Set<Parser<I, E, Any?>>> {
    val refCount = mutableMapOf<Parser<I, E, Any?>, Int>()
    val recursives = mutableSetOf<Parser<I, E, Any?>>()
    DeepRecursiveFunction<Pair<Set<Parser<I, E, Any?>>, Parser<I, E, Any?>>, Unit> { (seen, p) ->
        refCount[p].also { refCount[p] = it?.let { it + 1 } ?: 1 }

        if (seen.contains(p)) {
            recursives.add(p)
            return@DeepRecursiveFunction
        }

        if (refCount[p]!! == 1) {
            val nS = seen + p
            when (val pF = p.parserF) {
                is ParserF.Ap<ParserOf<I, E>, *, *> -> {
                    callRecursive(nS to pF.pF.fix())
                    callRecursive(nS to pF.pA.fix())
                }
                is ParserF.ApL<ParserOf<I, E>, *, *> -> {
                    callRecursive(nS to pF.pA.fix())
                    callRecursive(nS to pF.pB.fix())
                }
                is ParserF.ApR<ParserOf<I, E>, *, *> -> {
                    callRecursive(nS to pF.pA.fix())
                    callRecursive(nS to pF.pB.fix())
                }
                is ParserF.Alt -> {
                    callRecursive(nS to pF.left.fix())
                    callRecursive(nS to pF.right.fix())
                }
                is ParserF.Select<ParserOf<I, E>, *, *> -> {
                    callRecursive(nS to pF.pEither.fix())
                    callRecursive(nS to pF.pIfLeft.fix())
                }
                is ParserF.LookAhead -> {
                    callRecursive(nS to pF.p.fix())
                }
                is ParserF.NegLookAhead -> {
                    callRecursive(nS to pF.p.fix())
                }
                is ParserF.Attempt -> {
                    callRecursive(nS to pF.p.fix())
                }
                is ParserF.Lazy -> {
                    callRecursive(nS to pF.f().fix())
                }
            }
        }
    }(emptySet<Parser<I, E, Any?>>() to this)
    return refCount.filter { (_, v) -> v > 1 }.keys to recursives
}

internal fun <I, E, A> Parser<I, E, A>.insertLets(
    letBound: Set<Parser<I, E, Any?>>,
    recursive: Set<Parser<I, E, Any?>>
): Triple<Parser<I, E, A>, Map<Int, Parser<I, E, Any?>>, Int> {
    var labelC = 0
    val subParsers = mutableMapOf<Int, Parser<I, E, Any?>>()
    val handled = mutableMapOf<Parser<I, E, Any?>, Int>()

    val nP = cata<I, E, A, ParserOf<I, E>> { p ->
        val p = Parser(if (p is ParserF.Lazy) p.f().fix().parserF else p)
        val bound = letBound.contains(p)
        val rec = recursive.contains(p)

        return@cata if (bound) {
            val newLabel = if (handled.containsKey(p))
                handled[p]!!
            else
                labelC++.also {
                    subParsers[it] = p
                    handled[p] = it
                }
            Parser(ParserF.Let(rec, newLabel))
        } else p
    }.fix()

    return Triple(nP, subParsers, labelC)
}