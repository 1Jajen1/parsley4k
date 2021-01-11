package parsley.internal.backend

import parsley.ErrorItem
import parsley.Parser
import parsley.internal.backend.instructions.Apply
import parsley.internal.backend.instructions.Call
import parsley.internal.backend.instructions.End
import parsley.internal.backend.instructions.Exit
import parsley.internal.backend.instructions.Fail
import parsley.internal.backend.instructions.Flip
import parsley.internal.backend.instructions.Jump
import parsley.internal.backend.instructions.JumpOnFail
import parsley.internal.backend.instructions.JumpOnFailAndFailOnSuccess
import parsley.internal.backend.instructions.JumpOnRight
import parsley.internal.backend.instructions.JumpTable
import parsley.internal.backend.instructions.Label
import parsley.internal.backend.instructions.Many
import parsley.internal.backend.instructions.Many_
import parsley.internal.backend.instructions.PopHandler
import parsley.internal.backend.instructions.Push
import parsley.internal.backend.instructions.ResetOffset
import parsley.internal.backend.instructions.Satisfy
import parsley.internal.backend.instructions.Satisfy_
import parsley.internal.backend.instructions.SatisfyMany
import parsley.internal.backend.instructions.SatisfyManyAndMap
import parsley.internal.backend.instructions.SatisfyMany_
import parsley.internal.backend.instructions.Seek
import parsley.internal.backend.instructions.Tell
import parsley.internal.backend.optimise.toJumpTable
import parsley.internal.frontend.ParserF
import parsley.internal.unsafe

// TODO Use a better class to represent this triple
internal fun <I, E, A> Triple<Parser<I, E, A>, Map<Int, Parser<I, E, A>>, Int>.toProgram(
    vararg funcs: CodeGenFunc<I, E>
): Pair<Program<I, E>, Int> {
    val (mainP, subP, highest) = this
    val ctx = CodeGenContext<I, E>(highest)
    val mainM = mainP.codeGen(ctx, subP, *funcs)
    val subM = subP.mapValues { (_, p) -> p.codeGen(ctx, subP, *funcs) }.toMutableMap()

    val addedDiscarded = mutableSetOf<Int>()
    ctx.discard = true
    while (ctx.discardedSubs.isNotEmpty() && addedDiscarded.size != ctx.discardedSubs.size) {
        val (key, v) = ctx.discardedSubs.entries.first()
        addedDiscarded.add(key)
        subM[v] = subP[key]!!.codeGen(ctx, subP, *funcs)
    }
    return Pair(
        Program(mainM, subM),
        ctx.labelC
    )
}

@OptIn(ExperimentalStdlibApi::class)
internal fun <I, E, A> Parser<I, E, A>.codeGen(
    ctx: CodeGenContext<I, E> = CodeGenContext(0),
    subs: Map<Int, Parser<I, E, A>>,
    vararg funcs: CodeGenFunc<I, E>
): Method<I, E> {
    return DeepRecursiveFunction<ParserF<I, E, Any?>, Unit> { p ->
        (funcs.toList() + DefaultCodeGen()).forEach {
            if (it.run { apply(p, subs, ctx) }) return@DeepRecursiveFunction
        }
        throw IllegalStateException("No code generated for $p")
    }(this@codeGen.parserF).let {
        ctx.program.toList().let(::Method).also {
            ctx.program.clear()
        }
    }
}

internal class CodeGenContext<I, E>(var labelC: Int) {
    fun mkLabel(): Int = labelC++

    val program = mutableListOf<Instruction<I, E>>()
    operator fun plusAssign(instr: Instruction<I, E>): Unit {
        program += instr
    }

    operator fun plusAssign(xs: Iterable<Instruction<I, E>>): Unit {
        program += xs
    }

    var discard = false

    val discardedSubs = mutableMapOf<Int, Int>()
}

internal interface CodeGenFunc<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun <A> DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.apply(
        p: ParserF<I, E, A>,
        subs: Map<Int, Parser<I, E, A>>,
        ctx: CodeGenContext<I, E>
    ): Boolean
}

internal class DefaultCodeGen<I, E> : CodeGenFunc<I, E> {
    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun <A> DeepRecursiveScope<ParserF<I, E, Any?>, Unit>.apply(
        p: ParserF<I, E, A>,
        subs: Map<Int, Parser<I, E, A>>,
        ctx: CodeGenContext<I, E>
    ): Boolean {
        when (p) {
            is ParserF.Pure -> {
                if (!ctx.discard) ctx += Push(p.a)
            }
            is ParserF.Ap<I, E, *, A> -> {
                if (p.pF is ParserF.Pure) {
                    callRecursive(p.pA)
                    if (!ctx.discard) ctx += parsley.internal.backend.instructions.Map(p.pF.a.unsafe())
                } else {
                    callRecursive(p.pF)
                    callRecursive(p.pA)
                    if (!ctx.discard) ctx += Apply()
                }
            }
            is ParserF.ApL<I, E, A, *> -> {
                callRecursive(p.pA)
                val prev = ctx.discard
                ctx.discard = true
                callRecursive(p.pB)
                ctx.discard = prev
            }
            is ParserF.ApR<I, E, *, A> -> {
                val prev = ctx.discard
                ctx.discard = true
                callRecursive(p.pA)
                ctx.discard = prev
                callRecursive(p.pB)
            }
            is ParserF.Let -> {
                if (ctx.discard) {
                    val l = if (ctx.discardedSubs.containsKey(p.sub)) {
                        ctx.discardedSubs[p.sub]!!
                    } else {
                        val label = ctx.mkLabel()
                        ctx.discardedSubs[p.sub] = label
                        label
                    }
                    ctx += Call(p.recursive, l)
                } else {
                    ctx += Call(p.recursive, p.sub)
                }
            }
            is ParserF.Attempt -> {
                ctx += ResetOffset()
                callRecursive(p.p)
                ctx += PopHandler()
            }
            is ParserF.Alt -> {
                // First try to turn nested alts into a table lookup
                val (table, fallbackP) = p.toJumpTable(subs)

                suspend fun <A> DeepRecursiveScope<ParserF<I, E, A>, Unit>.asChoice(xs: List<ParserF<I, E, A>>): Unit =
                    when (xs.size) {
                        1 -> callRecursive(xs.first())
                        else -> {
                            val goodLabel = ctx.mkLabel()
                            val badLabel = ctx.mkLabel()
                            ctx += JumpOnFail(badLabel)
                            callRecursive(xs.first())
                            ctx += PopHandler()
                            ctx += Jump(goodLabel)
                            ctx += Label(badLabel)
                            asChoice(xs.drop(1))
                            ctx += Label(goodLabel)
                        }
                    }

                // Improve Jumps to the same branch
                if (table.isEmpty() || table.size == 1) {
                    asChoice(listOf(p.left, p.right))
                } else if (fallbackP.isNotEmpty()) {
                    val jmpTable = table.mapValues { ctx.mkLabel() }
                    val fallback = ctx.mkLabel()
                    val endL = ctx.mkLabel()
                    ctx += JumpOnFail(fallback)
                    ctx += JumpTable(jmpTable.toMutableMap())
                    table.forEach { (i, p) ->
                        ctx += Label(jmpTable[i]!!)
                        asChoice(p)
                        ctx += Jump(endL)
                    }
                    ctx += Label(fallback)
                    asChoice(fallbackP)
                    ctx += Label(endL)
                } else {
                    val jmpTable = table.mapValues { ctx.mkLabel() }
                    val endL = ctx.mkLabel()
                    ctx += JumpTable(jmpTable.toMutableMap())
                    table.forEach { (i, p) ->
                        ctx += Label(jmpTable[i]!!)
                        asChoice(p)
                        ctx += Jump(endL)
                    }
                    ctx += Label(endL)
                }
            }
            is ParserF.Empty -> {
                ctx += Fail()
            }
            is ParserF.Satisfy<*> -> {
                if (ctx.discard) {
                    ctx += Satisfy_(p.match.unsafe(), p.expected.unsafe())
                } else {
                    ctx += Satisfy(p.match.unsafe(), p.expected.unsafe())
                }
            }
            is ParserF.Single<*> -> {
                if (ctx.discard) {
                    ctx += Satisfy_({ it == p.i }, setOf(ErrorItem.Tokens(p.i.unsafe())))
                } else {
                    ctx += Satisfy({ it == p.i }, setOf(ErrorItem.Tokens(p.i.unsafe())))
                }
            }
            is ParserF.LookAhead -> {
                ctx += Tell()
                callRecursive(p.p)
                if (!ctx.discard) ctx += Flip()
                ctx += Seek()
            }
            is ParserF.NegLookAhead -> {
                val badLabel = ctx.mkLabel()
                ctx += JumpOnFailAndFailOnSuccess(badLabel)
                val prev = ctx.discard
                ctx.discard = true
                callRecursive(p.p)
                ctx.discard = prev
                ctx += PopHandler()
                ctx += Label(badLabel)
                if (!ctx.discard) ctx += Push(Unit)
            }
            is ParserF.Select<I, E, *, A> -> {
                val rightLabel = ctx.mkLabel()
                val prev = ctx.discard
                ctx.discard = false
                callRecursive(p.pEither)
                ctx.discard = prev
                ctx += JumpOnRight(rightLabel)
                callRecursive(p.pIfLeft)
                if (!ctx.discard) {
                    ctx += Flip()
                    ctx += Apply()
                }
                ctx += Label(rightLabel)
            }
            is ParserF.Many<I, E, Any?> -> {
                when {
                    p.p is ParserF.Single<*> -> {
                        if (ctx.discard) {
                            ctx += SatisfyMany_ { p.p.i.unsafe<I>() == it }
                        } else {
                            ctx += SatisfyMany { p.p.i.unsafe<I>() == it }
                        }
                    }
                    p.p is ParserF.Satisfy<*> -> {
                        if (ctx.discard) {
                            ctx += SatisfyMany_(p.p.match.unsafe())
                        } else {
                            ctx += SatisfyMany(p.p.match.unsafe())
                        }
                    }
                    p.p is ParserF.Ap<I, E, *, Any?> && p.p.pF is ParserF.Pure && p.p.pA is ParserF.Satisfy<*> -> {
                        if (ctx.discard) {
                            ctx += SatisfyMany_(p.p.pA.match.unsafe())
                        } else {
                            ctx += SatisfyManyAndMap(p.p.pA.match.unsafe(), p.p.pF.a.unsafe())
                        }
                    }
                    p.p is ParserF.Ap<I, E, *, Any?> && p.p.pF is ParserF.Pure && p.p.pA is ParserF.Single<*> -> {
                        if (ctx.discard) {
                            ctx += SatisfyMany_ { it == p.p.pA.i }
                        } else {
                            ctx += SatisfyManyAndMap({ it == p.p.pA.i }, p.p.pF.a.unsafe())
                        }
                    }
                    else -> {
                        val beginLabel = ctx.mkLabel()
                        val endLabel = ctx.mkLabel()
                        ctx += Label(beginLabel)
                        if (ctx.discard) ctx += Many_(endLabel)
                        else ctx += Many(endLabel)
                        callRecursive(p.p)
                        ctx += Jump(beginLabel)
                        ctx += Label(endLabel)
                    }
                }
            }
            is ParserF.Lazy -> throw IllegalStateException("Lazy should not be present at code gen state")
        }
        return true
    }
}

internal data class Program<I, E>(val main: Method<I, E>, val sub: Map<Int, Method<I, E>>) {
    fun toFinalProgram(): List<Instruction<I, E>> {
        // remove dead code
        val seen = mutableSetOf<Int>()
        val done = mutableSetOf<Int>()
        main.instr.forEach { if (it is Call) seen.add(it.to) }
        while (seen.size > 0) {
            val next = seen.first()
            seen.remove(next)
            sub[next]!!.instr.forEach { if (it is Call && done.contains(it.to).not()) seen.add(it.to) }
            done.add(next)
        }

        val sub = sub.filterKeys { done.contains(it) }

        // splice together the program
        val mutList = mutableListOf<Instruction<I, E>>()
        mutList.addAll(main.instr)
        mutList += End()
        sub.forEach { (key, method) ->
            mutList += Label(key)
            mutList.addAll(method.instr)
            mutList += Exit()
        }
        // replace labels
        val jumpTable = mutableMapOf<Int, Int>()
        var curr = mutList.size
        val processed = mutableSetOf<Instruction<I, E>>()
        fun findLabel(i: Int): Int {
            if (jumpTable.containsKey(i)) return jumpTable[i]!!

            var ci = 0
            var labels = 0
            do {
                val el = mutList[ci++]
                when {
                    el is Label && el.nr != i -> labels++
                    el is Label && el.nr == i -> {
                        if (ci < curr) curr--

                        val id = ci - 1 - labels
                        jumpTable[i] = id
                        mutList.removeAt(ci - 1)
                        return id
                    }
                }
            } while (ci < mutList.size)
            throw IllegalStateException("Label was not found! This is a codegen bug")
        }
        while (curr > 0) {
            val el = mutList[--curr]
            if (processed.contains(el)) continue
            processed.add(el)
            if (el is Jumps) {
                el.to = findLabel(el.to)
            } else if (el is JumpTable) {
                el.to.keys.forEach { k ->
                    el.to[k] = findLabel(el.to[k]!!)
                }
            }
        }
        return mutList
    }
}

internal inline class Method<I, E>(val instr: List<Instruction<I, E>>)