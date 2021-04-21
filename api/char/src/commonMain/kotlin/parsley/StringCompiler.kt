package parsley

import parsley.backend.BackendOptimiseStep
import parsley.backend.Method
import parsley.backend.instructions.CharEof
import parsley.backend.instructions.CharJumpTable
import parsley.backend.instructions.CharListToString
import parsley.backend.instructions.Eof
import parsley.backend.instructions.JumpTable
import parsley.backend.instructions.MatchManyCharN_
import parsley.backend.instructions.MatchManyChar_
import parsley.backend.instructions.MatchManyN_
import parsley.backend.instructions.MatchMany_
import parsley.backend.instructions.MatchString_
import parsley.backend.instructions.MkPair
import parsley.backend.instructions.PushChunkOf
import parsley.backend.instructions.PushStringOf
import parsley.backend.instructions.Satisfy
import parsley.backend.instructions.SatisfyChar
import parsley.backend.instructions.SatisfyCharMany
import parsley.backend.instructions.SatisfyCharMany_
import parsley.backend.instructions.SatisfyCharMap
import parsley.backend.instructions.SatisfyCharNoFail
import parsley.backend.instructions.SatisfyChar_
import parsley.backend.instructions.SatisfyChars_
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.SatisfyMap
import parsley.backend.instructions.SatisfyN_
import parsley.backend.instructions.SatisfyNoFail
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.Single
import parsley.backend.instructions.SingleChar
import parsley.backend.instructions.SingleCharMany
import parsley.backend.instructions.SingleCharMany_
import parsley.backend.instructions.SingleCharNoFail
import parsley.backend.instructions.SingleChar_
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.backend.instructions.SingleN_
import parsley.backend.instructions.SingleNoFail
import parsley.backend.instructions.Single_
import parsley.backend.instructions.StringToCharList
import parsley.backend.instructions.ToNative
import parsley.backend.instructions.convert
import parsley.collections.IntMap
import parsley.settings.CompilerSettings
import parsley.settings.RebuildPredicate
import parsley.settings.addOptimiseStep
import parsley.settings.defaultSettings
import parsley.settings.printFinalInstr
import parsley.settings.setRebuildPredicate
import kotlin.jvm.JvmName

fun <E> defaultStringSettings(): CompilerSettings<Char, E> =
    defaultSettings<Char, E>()
        .addOptimiseStep(BackendOptimiseStep { m, s, l -> m.replaceInstructions(s) })
        .setRebuildPredicate(RebuildPredicate { sing, sat ->
            val charArr = sing.map { it.i }.toCharArray()
            if (sat.all { it is CharPredicate }) CharPredicate { i -> charArr.contains(i) || sat.any { it.unsafe<CharPredicate>().invokeP(i) } }
            else CharPredicate { i -> charArr.contains(i) || sat.any { it.invoke(i) } }
        })
        // .printFinalInstr()

@JvmName("compileString")
@OptIn(ExperimentalStdlibApi::class)
fun <E, A> Parser<Char, E, A>.compile(): CompiledStringParser<E, A> {
    return CompiledStringParser(
        parserF.compile(defaultStringSettings()).instructions.toTypedArray()
    )
}

internal fun <E> Method<Char, E>.replaceInstructions(sub: IntMap<Method<Char, E>>): Unit {
    replaceMethod()
    sub.forEach { _, v -> v.replaceMethod() }
}

fun <E> Method<Char, E>.replaceMethod() {
    var curr = 0
    while (curr < size) {
        val el = get(curr)
        when {
            el is SingleNoFail -> {
                removeAt(curr)
                add(curr, SingleCharNoFail(el.i))
            }
            el is SatisfyNoFail && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharNoFail(el.f.unsafe()))
            }
            el is ToNative -> {
                removeAt(curr)
                add(curr, CharListToString())
            }
            el is JumpTable -> {
                removeAt(curr)
                val map = parsley.collections.IntMap.empty<Int>()
                el.table.forEach { (k, v) -> map[k.toInt()] = v }
                val table = CharJumpTable<E>(map, el.to)
                table.error.expected = el.error.expected
                add(curr, table)
            }
            el is SatisfyN_ && el.fArr.all { it is CharPredicate } -> {
                removeAt(curr)
                val new = SatisfyChars_<E>(el.fArr.unsafe(), el.eArr)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Satisfy && el.f is CharPredicate -> {
                removeAt(curr)
                val new = SatisfyChar<E>(el.f.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SatisfyMap && el.f is CharPredicate && el.g is CharFunc<Any?> -> {
                removeAt(curr)
                val new = SatisfyCharMap<E>(el.f.unsafe(), el.g.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Satisfy_ && el.f is CharPredicate -> {
                removeAt(curr)
                val new = SatisfyChar_<E>(el.f.unsafe())
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SatisfyMany && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharMany(el.f.unsafe()))
                if (curr + 1 < size) {
                    if (get(curr + 1) is ToNative) {
                        removeAt(curr + 1)
                    } else {
                        // TODO Is this reachable?
                        add(curr + 1, StringToCharList())
                    }
                }
            }
            el is SatisfyMany_ && el.f is CharPredicate -> {
                removeAt(curr)
                add(curr, SatisfyCharMany_(el.f.unsafe()))
            }
            el is Single -> {
                removeAt(curr)
                val new = SingleChar<E>(el.i)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is Single_ -> {
                removeAt(curr)
                val new = SingleChar_<E>(el.i)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SingleN_ -> {
                removeAt(curr)
                val arr = el.fArr.toList().toCharArray()
                val new = MatchString_<E>(arr, el.eArr)
                new.error.expected = el.error.expected
                add(curr, new)
            }
            el is SingleMany -> {
                removeAt(curr)
                add(curr, SingleCharMany(el.i))
                if (curr + 1 < size) {
                    if (get(curr + 1) is ToNative) {
                        removeAt(curr + 1)
                    } else {
                        // TODO Is this reachable?
                        add(curr + 1, StringToCharList())
                    }
                }
            }
            el is SingleMany_ -> {
                removeAt(curr)
                add(curr, SingleCharMany_(el.i))
            }
            el is PushChunkOf -> {
                removeAt(curr)
                add(curr, PushStringOf())

                val next = get(curr + 1)
                when (next) {
                    is MkPair -> {
                        if (curr < size - 1 && get(curr + 2) is ToNative) {
                            removeAt(curr + 2)
                        } else {
                            // TODO Is this reachable?
                            add(curr + 1, StringToCharList())
                        }
                    }
                    is ToNative -> {
                        removeAt(curr + 1)
                    }
                    else -> add(curr + 1, StringToCharList())
                }
            }
            el is MatchMany_ -> {
                removeAt(curr)
                add(curr, MatchManyChar_(el.path.convert()))
            }
            el is MatchManyN_ -> {
                removeAt(curr)
                add(curr, MatchManyCharN_(el.paths.convert()))
            }
            el is Eof -> {
                removeAt(curr)
                add(curr, CharEof())
            }
        }
        curr++
    }
}
