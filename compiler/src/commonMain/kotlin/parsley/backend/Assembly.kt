package parsley.backend

import parsley.backend.instructions.Call
import parsley.backend.instructions.Label
import parsley.backend.instructions.Return
import parsley.collections.IntMap
import parsley.collections.IntSet
import parsley.unsafe

fun <I, E> assemble(
    main: Method<I, E>,
    sub: IntMap<Method<I, E>>
): Method<I, E> {
    // inline single calls
    while (true) {
        val calls = IntMap.empty<Int>()
        main.forEach { if (it is Call) calls[it.to] = 1 }
        sub.forEach { _, m ->
            m.forEach {
                if (it is Call) {
                    if (it.to in calls) calls[it.to] = calls[it.to] + 1
                    else calls[it.to] = 1
                }
            }
        }
        calls.filterValues { it == 1 }
        main.inline(calls.keySet(), sub)
        sub.forEach { _, it -> it.inline(calls.keySet(), sub) }

        if (calls.size() == 0) break
    }

    // remove dead code
    val seen = IntSet.empty()
    val done = IntSet.empty()
    main.forEach {
        if (it is Call) seen.add(it.to)
    }
    while (seen.isNotEmpty()) {
        val next = seen.first()
        seen.remove(next)
        sub[next].forEach {
            if (it is Call && done.contains(it.to).not()) seen.add(it.to)
        }
        done.add(next)
    }

    sub.filterKeys { done.contains(it) }

    // Inline single top level Call if necessary
    val main = if (main.size == 1 && main[0] is Call) {
        val to = main[0].unsafe<Call<I, E>>().to
        sub[to].apply { add(0, Label(to)) }.also { sub.remove(to) }
    } else main

    // splice together the program
    val mutList = mutableListOf<Instruction<I, E>>()
    mutList.addAll(main.instructions)
    mutList += Return()
    sub.forEach { key, method ->
        mutList += Label(key)
        mutList.addAll(method.instructions)
        mutList += Return()
    }
    // replace labels
    val jumpTable = IntMap.empty<Int>()
    var curr = mutList.size
    val processed = mutableSetOf<Instruction<I, E>>()
    fun findLabel(i: Int): Int {
        if (i in jumpTable) return jumpTable[i]

        var ci = 0
        var labels = 0
        do {
            val el = mutList[ci++]
            when {
                el is Label && el.id != i -> labels++
                el is Label && el.id == i -> {
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
            if (el.onAssembly { findLabel(it) }) continue
            el.to = findLabel(el.to)
        }
    }
    mutList.removeAll { it is Label }
    return Method(mutList)
}