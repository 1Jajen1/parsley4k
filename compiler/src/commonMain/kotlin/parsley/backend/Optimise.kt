package parsley.backend

import parsley.Method
import parsley.backend.instructions.Apply
import parsley.backend.instructions.Call
import parsley.backend.instructions.Jump
import parsley.backend.instructions.Label
import parsley.backend.instructions.Map
import parsley.backend.instructions.Push
import parsley.backend.instructions.SatisfyMany
import parsley.backend.instructions.SatisfyMany_
import parsley.backend.instructions.SatisfyN_
import parsley.backend.instructions.Satisfy_
import parsley.backend.instructions.Single
import parsley.backend.instructions.SingleMany
import parsley.backend.instructions.SingleMany_
import parsley.backend.instructions.SingleN_
import parsley.backend.instructions.Single_
import parsley.collections.IntMap
import parsley.collections.IntSet
import parsley.unsafe

// TODO add support for MatchN
fun <I, E> Method<I, E>.rewriteRules(): Method<I, E> {
    var i = 0
    while (i < size - 1) {
        val e1 = get(i)
        val e2 = get(i + 1)
        when {
            e1 is Satisfy_ && e2 is Satisfy_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SatisfyN_(arrayOf(e1.f, e2.f), arrayOf(e1.error.expected, e2.error.expected)))
            }
            e1 is SatisfyN_ && e2 is Satisfy_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SatisfyN_(e1.fArr + e2.f, arrayOf(*e1.eArr, e2.error.expected)))
            }
            e1 is Single_ && e2 is Single_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SingleN_(arrayOf<Any?>(e1.i, e2.i).unsafe(), arrayOf(e1.error.expected, e2.error.expected)))
            }
            e1 is SingleN_ && e2 is Single_ -> {
                removeAt(i)
                removeAt(i)
                add(i--, SingleN_(e1.fArr + e2.i, arrayOf(*e1.eArr, e2.error.expected)))
            }
            e1 is Jump && e2 is Label && e1.to == e2.id -> {
                removeAt(i--)
            }
            e1 is FuseMap<*, *> && e2 is Map -> {
                removeAt(i)
                removeAt(i)
                add(i--, e1.fuse(e2.f).unsafe())
            }
            // TODO check again sometime if we can't detect and apply this in the ast earlier
            e1 is Single && e2 is Map -> {
                removeAt(i)
                removeAt(i)
                add(i, Push(e2.f(e1.i)))
                val new = Single_<I, E>(e1.i, emptySet())
                new.error = e1.error
                add(i--, new)
            }
            e1 is Push && e2 is Single && i < size - 2 && get(i + 2) is Apply -> {
                removeAt(i)
                removeAt(i)
                removeAt(i)
                add(i, Push(e1.el.unsafe<(Any?) -> Any?>()(e2.i)))
                val new = Single_<I, E>(e2.i, emptySet())
                new.error = e2.error
                add(i, new)
                i -= 2
            }
            // TODO Check if this can be done on ast level
            e1 is SingleMany && e2 is SingleMany && e1.i == e2.i -> {
                removeAt(i--)
            }
            e1 is SingleMany_ && e2 is SingleMany_ && e1.i == e2.i -> {
                removeAt(i--)
            }
            e1 is SatisfyMany && e2 is SatisfyMany && e1.f == e2.f -> {
                removeAt(i--)
            }
            e1 is SatisfyMany_ && e2 is SatisfyMany_ && e1.f == e2.f -> {
                removeAt(i--)
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