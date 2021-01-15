package parsley.backend

import parsley.Method
import parsley.backend.instructions.Call
import parsley.backend.instructions.Jump
import parsley.backend.instructions.Label
import parsley.backend.instructions.SatisfyN_
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.SingleN_
import parsley.backend.instructions.Single_
import parsley.collections.IntMap
import parsley.collections.IntSet
import parsley.unsafe

fun <I, E> Method<I, E>.rewriteRules(): Method<I, E> {
    var i = 0
    while (i < size - 1) {
        val e1 = get(i)
        val e2 = get(i + 1)
        when {
            e1 is Satisfy_ && e2 is Satisfy_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SatisfyN_(arrayOf(e1.f, e2.f)))
            }
            e1 is SatisfyN_ && e2 is Satisfy_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SatisfyN_(e1.fArr + e2.f))
            }
            e1 is Single_ && e2 is Single_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SingleN_(arrayOf<Any?>(e1.i, e2.i).unsafe()))
            }
            e1 is SingleN_ && e2 is Single_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SingleN_(e1.fArr + e2.i))
            }
            e1 is Jump && e2 is Label && e1.to == e2.id -> {
                removeAt(i--)
            }
            e1 is FuseMap<*, *> && e2 is parsley.backend.instructions.Map -> {
                removeAt(i)
                removeAt(i)
                add(i--, e1.fuse(e2.f).unsafe())
            }
        }
        i++
    }
    return this
}

fun <I, E> inlinePass(
    main: Method<I, E>,
    subs: IntMap<Method<I, E>>
): Unit {
    val toInline = subs.copy().apply { filter { _, v -> v.checkInline() } }.keySet()
    main.inline(toInline, subs)
    subs.forEach { _, v -> v.inline(toInline, subs) }
}

// TODO Better heuristics
fun <I, E> List<Instruction<I, E>>.checkInline(): Boolean =
    size < 5

fun <I, E> Method<I, E>.inline(
    toInline: IntSet,
    subs: IntMap<Method<I, E>>
): Unit {
    val mutlist = this
    var curr = 0
    while (curr < mutlist.size) {
        val el = mutlist[curr++]
        if (el is Call && toInline.contains(el.to)) {
            mutlist.removeAt(--curr)
            mutlist.addAll(curr, subs[el.to])
        }
    }
}