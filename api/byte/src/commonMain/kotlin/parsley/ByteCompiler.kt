package parsley

import parsley.backend.CodeGenContext
import parsley.backend.CodeGenStep
import parsley.backend.instructions.ByteArrToByteList
import parsley.backend.instructions.ByteEof
import parsley.backend.instructions.ByteJumpTable
import parsley.backend.instructions.Eof
import parsley.backend.instructions.JumpTable
import parsley.backend.instructions.MatchByteArr_
import parsley.backend.instructions.MatchManyByteN_
import parsley.backend.instructions.MatchManyByte_
import parsley.backend.instructions.MatchManyN_
import parsley.backend.instructions.MatchMany_
import parsley.backend.instructions.MkPair
import parsley.backend.instructions.PushByteArrayOf
import parsley.backend.instructions.PushChunkOf
import parsley.backend.instructions.Satisfy
import parsley.backend.instructions.SatisfyByte
import parsley.backend.instructions.SatisfyByteMany
import parsley.backend.instructions.SatisfyByteMany_
import parsley.backend.instructions.SatisfyByteMap
import parsley.backend.instructions.SatisfyByte_
import parsley.backend.instructions.SatisfyBytes_
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.SatisfyMap
import parsley.backend.instructions.SatisfyN_
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.Single
import parsley.backend.instructions.SingleByte
import parsley.backend.instructions.SingleByteMany
import parsley.backend.instructions.SingleByteMany_
import parsley.backend.instructions.SingleByte_
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.backend.instructions.SingleN_
import parsley.backend.instructions.Single_
import parsley.backend.instructions.convert
import parsley.collections.IntMap
import parsley.frontend.ByteListToArr
import parsley.frontend.OptimiseStep
import parsley.frontend.ParserF
import kotlin.jvm.JvmName

@JvmName("compileString")
@OptIn(ExperimentalStdlibApi::class)
fun <E, A> Parser<Byte, E, A>.compile(): CompiledByteParser<E, A> {
    val settings = defaultSettings<Byte, E>()
        //.copy(optimise = OptimiseSettings(analyseSatisfy = AnalyseSatisfy(generateSequence(Byte.MIN_VALUE) { c -> if (c != Byte.MAX_VALUE) c.inc() else null })))
        .addOptimiseStep(
            object : OptimiseStep<Byte, E> {
                override suspend fun DeepRecursiveScope<ParserF<Byte, E, Any?>, ParserF<Byte, E, Any?>>.step(
                    p: ParserF<Byte, E, Any?>,
                    subs: IntMap<ParserF<Byte, E, Any?>>,
                    settings: CompilerSettings<Byte, E>
                ): ParserF<Byte, E, Any?> {
                    return when {
                        p is parsley.frontend.Satisfy<*> && p.match is BytePredicate && settings.optimise.analyseSatisfy.f.any { true } -> {
                            val accepted = mutableSetOf<Byte>()
                            val rejected = mutableSetOf<Byte>()
                            for (c in settings.optimise.analyseSatisfy.f) {
                                if (p.match.unsafe<BytePredicate>().invokeP(c))
                                    accepted.add(c)
                                else rejected.add(c)
                            }
                            parsley.frontend.Satisfy(p.match.unsafe(), p.expected.unsafe(), accepted, rejected)
                        }
                        else -> p
                    }
                }
            }
        )
        .addCodegenStep(
            object : CodeGenStep<Byte, E> {
                override suspend fun DeepRecursiveScope<ParserF<Byte, E, Any?>, Unit>.step(
                    p: ParserF<Byte, E, Any?>,
                    ctx: CodeGenContext<Byte, E>
                ): Boolean {
                    return when (p) {
                        is ByteListToArr -> {
                            callRecursive(p.inner)
                            if (!ctx.discard) ctx += parsley.backend.instructions.ByteListToArr()
                            true
                        }
                        else -> false
                    }
                }
            }
        ).addOptimiseStep { s, l -> replaceInstructions(s, l) }
    return CompiledByteParser(
        parserF.compile(settings).toTypedArray()
    )
}


internal fun <E> Method<Byte, E>.replaceInstructions(sub: IntMap<Method<Byte, E>>, l: Int): Int {
    replaceMethod()
    sub.forEach { _, v -> v.replaceMethod() }
    return l
}

fun <E> Method<Byte, E>.replaceMethod() {
    var curr = 0
    while (curr < size) {
        val el = get(curr)
        when {
            el is JumpTable -> {
                removeAt(curr)
                val map = parsley.collections.IntMap.empty<Int>()
                el.table.forEach { (k, v) -> map[k.toInt()] = v }
                val table = ByteJumpTable<E>(map, el.to)
                table.error.expected = el.error.expected
                add(curr, table)
            }
            el is SatisfyN_ && el.fArr.all { it is BytePredicate } -> {
                removeAt(curr)
                val new = SatisfyBytes_<E>(el.fArr.unsafe(), el.eArr)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Satisfy && el.f is BytePredicate -> {
                removeAt(curr)
                val new = SatisfyByte<E>(el.f.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SatisfyMap && el.f is BytePredicate && el.g is ByteFunc<Any?> -> {
                removeAt(curr)
                val new = SatisfyByteMap<E>(el.f.unsafe(), el.g.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Satisfy_ && el.f is BytePredicate -> {
                removeAt(curr)
                val new = SatisfyByte_<E>(el.f.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SatisfyMany && el.f is BytePredicate -> {
                removeAt(curr)
                add(curr, SatisfyByteMany(el.f.unsafe()))
                if (curr + 1 < size) {
                    if (get(curr + 1) is parsley.backend.instructions.ByteListToArr) {
                        removeAt(curr + 1)
                    } else {
                        add(curr + 1, ByteArrToByteList())
                    }
                }
            }
            el is SatisfyMany_ && el.f is BytePredicate -> {
                removeAt(curr)
                add(curr, SatisfyByteMany_(el.f.unsafe()))
            }
            el is Single -> {
                removeAt(curr)
                val new = SingleByte<E>(el.i)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Single_ -> {
                removeAt(curr)
                val new = SingleByte_<E>(el.i)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SingleN_ -> {
                removeAt(curr)
                val arr = el.fArr.toList().toByteArray()
                val new = MatchByteArr_<E>(arr, el.eArr)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SingleMany -> {
                removeAt(curr)
                add(curr, SingleByteMany(el.i))
                if (curr + 1 < size) {
                    if (get(curr + 1) is parsley.backend.instructions.ByteListToArr) {
                        removeAt(curr + 1)
                    } else {
                        add(curr + 1, ByteArrToByteList())
                    }
                }
            }
            el is SingleMany_ -> {
                removeAt(curr)
                add(curr, SingleByteMany_(el.i))
            }
            el is PushChunkOf -> {
                removeAt(curr)
                add(curr, PushByteArrayOf())

                val next = get(curr + 1)
                when (next) {
                    is MkPair -> {
                        if (curr < size - 1 && get(curr + 2) is parsley.backend.instructions.ByteListToArr) {
                            removeAt(curr + 2)
                        } else {
                            add(curr + 1, ByteArrToByteList())
                        }
                    }
                    is parsley.backend.instructions.ByteListToArr -> {
                        removeAt(curr + 1)
                    }
                    else -> add(curr + 1, ByteArrToByteList())
                }
            }
            el is MatchMany_ -> {
                removeAt(curr)
                add(curr, MatchManyByte_(el.path.convert()))
            }
            el is MatchManyN_ -> {
                removeAt(curr)
                add(curr, MatchManyByteN_(el.paths.convert()))
            }
            el is Eof -> {
                removeAt(curr)
                add(curr, ByteEof())
            }
        }
        curr++
    }
}
