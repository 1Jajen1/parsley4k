package parsley.backend.instructions

import parsley.ErrorItemT
import parsley.ParseErrorT
import parsley.backend.AbstractStackMachine
import parsley.backend.Errors
import parsley.backend.Instruction
import parsley.backend.Jumps
import parsley.backend.StringStackMachine
import parsley.collections.IntMap
import parsley.unsafe

class CharJumpTable<E>(val map: IntMap<Int>, override var to: Int) : Instruction<Char, E>, Jumps, Errors<Char, E> {

    private var unexpected = ErrorItemT.Tokens<Char>(null.unsafe(), mutableListOf())
    override var error: ParseErrorT<Char, E> = ParseErrorT(-1, unexpected, emptySet(), emptySet())

    override fun onAssembly(f: (Int) -> Int): Boolean {
        map.onEach { _, v -> f(v) }
        if (to != -1) to = f(to)
        return true
    }

    override fun apply(machine: AbstractStackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        if (machine.hasMore()) {
            val c = machine.takeP()
            val key = c.toInt()

            machine.addAsHint(error)
            if (key in map) machine.jump(map[key])
            else {
                if (to != -1) machine.jump(to)
                else {
                    unexpected.head = c
                    machine.addUnexpected(unexpected)
                    machine.fail()
                }
            }
        } else machine.needInput(error.expected)
    }

    override fun toString(): String {
        val map = mutableListOf<Pair<Char, Int>>()
        this.map.onEach { k, v -> v.also { map.add(k.toChar() to v) } }
        return "CharJumpTable $map"
    }

    override fun copy(): Instruction<Char, E> {
        val new = CharJumpTable<E>(map, to)

        return new
    }
}
