package parsley.backend

import parsley.StringStackMachine
import parsley.collections.IntMap
import parsley.unsafe

class CharJumpTable<E>(val map: IntMap<Int>) : Instruction<Char, E>, Jumps {
    override var to: Int = -1 // unused
    override fun onAssembly(f: (Int) -> Int): Boolean {
        map.onEach { _, v -> f(v) }
        return true
    }

    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val key = machine.takeP().toInt()
            if (key in map) machine.jump(map[key])
            else machine.fail()
        } else machine.needInput()
    }

    override fun toString(): String {
        val map = mutableListOf<Pair<Char, Int>>()
        this.map.onEach { k, v -> v.also { map.add(k.toChar() to v) } }
        return "CharJumpTable($map)"
    }
}
