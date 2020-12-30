package parsley.internal.backend

import arrow.Const
import arrow.ConstOf
import arrow.fix
import arrow.value
import parsley.ErrorItem
import parsley.ParseError
import parsley.Parser
import parsley.internal.backend.instructions.Apply
import parsley.internal.backend.instructions.Call
import parsley.internal.backend.instructions.End
import parsley.internal.backend.instructions.Exit
import parsley.internal.backend.instructions.Fail
import parsley.internal.backend.instructions.Flip
import parsley.internal.backend.instructions.IList
import parsley.internal.backend.instructions.Jump
import parsley.internal.backend.instructions.JumpOnFail
import parsley.internal.backend.instructions.JumpOnFailAndFailOnSuccess
import parsley.internal.backend.instructions.JumpOnFailPure
import parsley.internal.backend.instructions.JumpOnRight
import parsley.internal.backend.instructions.Label
import parsley.internal.backend.instructions.Many
import parsley.internal.backend.instructions.Pop
import parsley.internal.backend.instructions.PopHandler
import parsley.internal.backend.instructions.Push
import parsley.internal.backend.instructions.ResetOffset
import parsley.internal.backend.instructions.Satisfy
import parsley.internal.backend.instructions.Seek
import parsley.internal.backend.instructions.Tell
import parsley.internal.frontend.ParserF
import parsley.internal.frontend.cata
import parsley.internal.frontend.fix

internal typealias CodeGen<I, E> = ConstOf<List<Instruction<I, E>>>

// TODO Use a better class to represent this triple
internal fun <I, E, A> Triple<Parser<I, E, A>, Map<Int, Parser<I, E, A>>, Int>.toProgram(): Program<I, E> {
    val (mainP, subP, highest) = this
    val ctx = CodeGenContext(highest)
    return Program(mainP.codeGen(ctx), subP.mapValues { (_, p) -> p.codeGen(ctx) })
}

internal fun <I, E, A> Parser<I, E, A>.codeGen(
    ctx: CodeGenContext = CodeGenContext(0),
    vararg funcs: CodeGenFunc<I, E>
): Method<I, E> {
    return cata<I, E, A, CodeGen<I, E>> { p ->
        (funcs.toList() + DefaultCodeGen()).forEach {
            it.run { p.apply(ctx) }?.also { return@cata Const(it) }
        }
        throw IllegalStateException("No code generated for $p")
    }.fix().value().let(::Method)
}

internal class CodeGenContext(var labelC: Int) {
    fun mkLabel(): Int = labelC++
}

internal interface CodeGenFunc<I, E> {
    fun <A> ParserF<I, E, CodeGen<I, E>, A>.apply(context: CodeGenContext): List<Instruction<I, E>>?
}

internal class DefaultCodeGen<I, E> : CodeGenFunc<I, E> {
    override fun <A> ParserF<I, E, CodeGen<I, E>, A>.apply(context: CodeGenContext): List<Instruction<I, E>> =
        when (val p = this.fix()) {
            is ParserF.Pure -> {
                listOf(Push(p.a))
            }
            is ParserF.Ap<CodeGen<I, E>, *, *> -> {
                p.pF.value() + p.pA.value() + Apply()
            }
            is ParserF.ApL<CodeGen<I, E>, *, *> -> {
                p.pA.value() + p.pB.value() + Pop()
            }
            is ParserF.ApR<CodeGen<I, E>, *, *> -> {
                p.pA.value() + Pop() + p.pB.value()
            }
            is ParserF.Let -> {
                listOf(Call(p.recursive, p.sub))
            }
            is ParserF.Attempt -> {
                listOf(ResetOffset<I, E>()) + p.p.value() + PopHandler()
            }
            is ParserF.Alt -> {
                val goodLabel = context.mkLabel()
                val badLabel = context.mkLabel()
                listOf(JumpOnFail<I, E>(badLabel)) +
                        p.left.value() + PopHandler() + Jump(goodLabel) + Label(badLabel) +
                        p.right.value() + Label(goodLabel)
            }
            is ParserF.Empty -> {
                listOf(Fail(p.error))
            }
            is ParserF.Satisfy<*> -> {
                listOf(Satisfy(p.match as (I) -> Boolean, p.expected as Set<ErrorItem<I>>))
            }
            is ParserF.Single<*> -> {
                listOf(Satisfy({ it == p.i }, setOf(ErrorItem.Tokens(p.i)) as Set<ErrorItem<I>>))
            }
            is ParserF.LookAhead -> {
                listOf(Tell<I, E>()) + p.p.value() + Flip() + Seek()
            }
            is ParserF.NegLookAhead -> {
                val badLabel = context.mkLabel()
                listOf(JumpOnFailAndFailOnSuccess<I, E>(badLabel)) +
                        p.p.value() + PopHandler() +
                        Label(badLabel) + Push(Unit)
            }
            is ParserF.Select<CodeGen<I, E>, *, *> -> {
                val rightLabel = context.mkLabel()
                p.pEither.value() + JumpOnRight(rightLabel) + p.pIfLeft.value() + Flip() + Apply() + Label(rightLabel)
            }
            is ParserF.Many<CodeGen<I, E>, *> -> {
                val label = context.mkLabel()
                val endLabel = context.mkLabel()
                listOf(Push<I, E>(IList.Nil)) + Tell() + JumpOnFailPure(endLabel) +
                        Label(label) + p.p.value() + Many(label) +
                        Label(endLabel) + Seek() +
                        parsley.internal.backend.instructions.Map {
                            (it as IList<Any?>).toList()
                        }
            }
            is ParserF.Lazy -> throw IllegalStateException("Lazy should not be present at code gen state")
        }
}

internal data class Program<I, E>(val main: Method<I, E>, val sub: Map<Int, Method<I, E>>) {
    fun toFinalProgram(): List<Instruction<I, E>> {
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
            if (el is Jumps) {
                el.to = findLabel(el.to)
            }
        }
        return mutList
    }
}

internal inline class Method<I, E>(val instr: List<Instruction<I, E>>)