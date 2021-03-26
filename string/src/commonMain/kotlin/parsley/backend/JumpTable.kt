package parsley.backend

import parsley.ErrorItemT
import parsley.ParseErrorT
import parsley.StringStackMachine
import parsley.collections.IntMap
import parsley.unsafe

class CharJumpTable<E>(val map: IntMap<Int>) : Instruction<Char, E>, Jumps, Errors<Char> {
    override var to: Int = -1 // unused

    private var unexpected = ErrorItemT.Tokens<Char>(null.unsafe(), mutableListOf())
    override var error: ParseErrorT.Trivial<Char> = ParseErrorT.Trivial(-1, unexpected, emptySet())

    override fun onAssembly(f: (Int) -> Int): Boolean {
        map.onEach { _, v -> f(v) }
        return true
    }

    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            val key = c.toInt()
            if (key in map) machine.jump(map[key])
            else {
                unexpected.head = c
                machine.failWith(error)
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String {
        val map = mutableListOf<Pair<Char, Int>>()
        this.map.onEach { k, v -> v.also { map.add(k.toChar() to v) } }
        return "CharJumpTable($map)"
    }
}
