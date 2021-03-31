package parsley.backend.instructions

import parsley.CharPredicate
import parsley.CharTokensT
import parsley.ErrorItem
import parsley.ParseErrorT
import parsley.backend.StringStackMachine
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.unsafe

class SatisfyChars_<E>(val fArr: Array<CharPredicate>, eArr: Array<Set<ErrorItem<Char>>>) : Instruction<Char, E>,
    Errors<Char, E> {

    private var unexpected = CharTokensT(' ', charArrayOf())
    private var errRef = ParseErrorT<Char, E>(-1, unexpected, emptySet(), emptySet())
    override var error = errRef

    init {
        // TODO Handle relabel better
        val buf = mutableListOf<Char>()
        var onlySingleToken = true
        eArr.forEach {
            if (it.size == 1) {
                val fst = it.first()
                if (fst is ErrorItem.Tokens && fst.tail.isEmpty()) {
                    buf += fst.head
                } else onlySingleToken = false
            } else onlySingleToken = false
        }
        if (onlySingleToken)
            error = ParseErrorT(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))), emptySet())
    }

    private val sz = fArr.size

    // TODO Does this benefit if the for loop is implemented in StringStackMachine?
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore(sz)) {
            val start = machine.inputOffset
            for (f in fArr) {
                val c = machine.takeP()
                if (f(c)) machine.consume()
                else {
                    machine.inputOffset = start
                    unexpected.all = machine.takeP(start, start + sz)
                    return machine.failWith(error)
                }
            }
        } else machine.needInput()
    }

    override fun toString(): String = "SatisfyChars_($sz)"
}

class MatchString_<E>(val arr: CharArray, eArr: Array<Set<ErrorItem<Char>>>) : Instruction<Char, E>, Errors<Char, E> {

    private var unexpected = CharTokensT(' ', charArrayOf())
    private var errRef = ParseErrorT<Char, E>(-1, unexpected, emptySet(), emptySet())
    override var error = errRef

    init {
        // TODO Handle relabel better
        val buf = mutableListOf<Char>()
        var onlySingleToken = true
        eArr.forEach {
            if (it.size == 1) {
                val fst = it.first()
                if (fst is ErrorItem.Tokens && fst.tail.isEmpty()) {
                    buf += fst.head
                } else onlySingleToken = false
            } else onlySingleToken = false
        }
        if (onlySingleToken)
            error = ParseErrorT(-1, unexpected, setOf(ErrorItem.Tokens(buf.first(), buf.drop(1))), emptySet())
    }

    private val sz = arr.size

    // TODO Does this benefit if the for loop is implemented in StringStackMachine?
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore(sz)) {
            val start = machine.inputOffset
            for (i in 0 until sz) {
                val c = machine.takeP()
                if (c == arr[i]) machine.consume()
                else {
                    machine.inputOffset = start
                    unexpected.all = machine.takeP(start, start + sz)
                    return machine.failWith(error)
                }
            }
        } else machine.needInput()
    }

    override fun toString(): String = "MatchString_(${arr.concatToString()})"
}

class MatchManyChar_<E>(val path: Array<CharMatcher>) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        while (true) {
            if (machine.hasMore(path.size)) {
                path.forEachIndexed { ind, m ->
                    val i = machine.takeP()
                    if (m.invokeP(i)) {
                        machine.consume()
                    } else {
                        if (ind == 0) return@apply
                        else {
                            machine.fail()
                        }
                    }
                }
            } else return@apply
        }
    }

    override fun toString(): String = "MatchManyChar_(${path.toList()})"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

class MatchManyCharN_<E>(val paths: Array<Array<CharMatcher>>) : Instruction<Char, E>, Errors<Char, E> {
    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        loop@while (true) {
            pathL@for (p in paths) {
                if (machine.hasMore(p.size)) {
                    for (ind in p.indices) {
                        val m = p[ind]
                        val i = machine.takeP()
                        if (m.invokeP(i)) {
                            machine.consume()
                        } else {
                            if (ind != 0) return@apply machine.fail()
                            else continue@pathL
                        }
                    }
                    continue@loop
                }
            }
            return@apply
        }
    }

    override fun toString(): String = "MatchManyCharN_(${paths.map { it.toList() }})"

    private var unexpected = CharTokensT(' ', charArrayOf())
    override var error: ParseErrorT<Char, E> =
        ParseErrorT(-1, unexpected, emptySet(), emptySet())
}

sealed class CharMatcher : CharPredicate {
    class Sat(val f: CharPredicate, val expected: Set<ErrorItem<Char>>): CharMatcher()
    class El(val el: Char, val expected: Set<ErrorItem<Char>>): CharMatcher()
    object Eof: CharMatcher()

    override fun invoke(i: Char): Boolean = when (this) {
        is Sat -> f(i)
        is El -> el == i
        Eof -> false
    }

    override fun invokeP(i: Char): Boolean = when (this) {
        is Sat -> f(i)
        is El -> el == i
        Eof -> false
    }

    override fun toString(): String = when (this) {
        is Sat -> "Sat"
        is El -> "El($el)"
        Eof -> "Eof"
    }
}

fun Array<Matcher<Char>>.convert(): Array<CharMatcher> = map {
    when (it) {
        is Matcher.Sat -> CharMatcher.Sat(it.f.unsafe(), it.expected.unsafe())
        is Matcher.El -> CharMatcher.El(it.el, it.expected.unsafe())
        is Matcher.Eof -> CharMatcher.Eof
    }
}.toTypedArray()

fun Array<Array<Matcher<Char>>>.convert(): Array<Array<CharMatcher>> = map {
    it.convert()
}.toTypedArray()
