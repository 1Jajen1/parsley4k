package parsley.internal.frontend

import arrow.Const
import arrow.ConstOf
import arrow.Either
import arrow.value
import parsley.Parser
import parsley.ParserOf
import parsley.attempt
import parsley.fix
import parsley.lookAhead
import parsley.negLookAhead

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
        is ParserF.Single<*> -> "Single(${it.i})"
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
                    callRecursive(nS to pF.f.invoke().fix())
                }
            }
        }
    }(emptySet<Parser<I, E, Any?>>() to this)
    return Pair(refCount.filter { (_, v) -> v > 1 }.keys, recursives)
}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> Parser<I, E, A>.insertLets(
    letBound: Set<Parser<I, E, Any?>>,
    recursive: Set<Parser<I, E, Any?>>
): Triple<Parser<I, E, A>, Map<Int, Parser<I, E, Any?>>, Int> {
    var labelC = 0
    val subParsers = mutableMapOf<Int, Parser<I, E, Any?>>()
    val handled = mutableMapOf<Parser<I, E, Any?>, Int>()

    val nP = DeepRecursiveFunction<Parser<I, E, Any?>, Parser<I, E, Any?>> { p ->
        val bound = letBound.contains(p)
        val rec = recursive.contains(p)

        suspend fun DeepRecursiveScope<Parser<I, E, Any?>, Parser<I, E, Any?>>.new(): Parser<I, E, Any?> =
            when (val pF = p.parserF) {
                is ParserF.Ap<ParserOf<I, E>, *, *> -> {
                    val l = callRecursive(pF.pF.fix())
                    val r = callRecursive(pF.pA.fix())
                    ParserF.Ap(l as Parser<I, E, (Any?) -> Any?>, r)
                }
                is ParserF.ApL<ParserOf<I, E>, *, *> -> {
                    val l = callRecursive(pF.pA.fix())
                    val r = callRecursive(pF.pB.fix())
                    ParserF.ApL(l, r)
                }
                is ParserF.ApR<ParserOf<I, E>, *, *> -> {
                    val l = callRecursive(pF.pA.fix())
                    val r = callRecursive(pF.pB.fix())
                    ParserF.ApR(l, r)
                }
                is ParserF.Alt -> {
                    val l = callRecursive(pF.left.fix())
                    val r = callRecursive(pF.right.fix())
                    ParserF.Alt(l, r)
                }
                is ParserF.Select<ParserOf<I, E>, *, *> -> {
                    val e = callRecursive(pF.pEither.fix())
                    val l = callRecursive(pF.pIfLeft.fix())
                    ParserF.Select(
                        e as Parser<I, E, Either<Any?, Any?>>,
                        l as Parser<I, E, (Any?) -> Any?>
                    )
                }
                is ParserF.LookAhead -> {
                    ParserF.LookAhead(callRecursive(pF.p.fix()))
                }
                is ParserF.NegLookAhead -> {
                    ParserF.NegLookAhead(callRecursive(pF.p.fix()))
                }
                is ParserF.Attempt -> {
                    ParserF.Attempt(callRecursive(pF.p.fix()))
                }
                is ParserF.Lazy -> {
                    if (handled.containsKey(p).not())
                        callRecursive(pF.f.invoke().fix()).parserF
                    else pF
                }
                else -> pF
            }.let(::Parser)

        if (bound) {
            val newLabel =
                if (handled.containsKey(p)) handled[p]!!
                else {
                    labelC++.also {
                        handled[p] = it
                        subParsers[it] = new()
                    }
                }
            return@DeepRecursiveFunction Parser(ParserF.Let(rec, newLabel))
        } else new()
    }(this)

    return Triple(nP as Parser<I, E, A>, subParsers, labelC)
}