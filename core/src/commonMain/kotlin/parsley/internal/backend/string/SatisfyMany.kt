package parsley.internal.backend.string

import parsley.internal.backend.FuseMap
import parsley.internal.backend.Instruction
import parsley.internal.backend.ParseStatus
import parsley.internal.backend.Pushes
import parsley.internal.backend.StackMachine
import parsley.internal.frontend.CharPredicate
import parsley.internal.unsafe

internal class SatisfyManyChars<E>(val f: CharPredicate) : Instruction<Char, E>, Pushes, FuseMap<Char, E> {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val c = machine.takeP()
            // TODO vv This below boxes c
            if (f(c)) {
                machine.consume()
            } else {
                machine.dataStack.push(machine.substring(start))
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "SatisfyManyChars"
    override fun pushes(): Int = 1

    override fun fuseWith(f: (Any?) -> Any?): Instruction<Char, E> = SatisfyManyCharsAndMap(this.f, f.unsafe())
}

internal class SatisfyManyCharsAndMap<E>(val f: CharPredicate, val fa: (String) -> Any?) : Instruction<Char, E>,
    Pushes {
    override fun apply(machine: StackMachine<Char, E>) {
        val machine = machine.unsafe<StringStackMachine<E>>()
        val start = machine.inputOffset
        while (machine.hasMore()) {
            val c = machine.takeP()
            // TODO vv This below boxes c which costs us quite a bit
            if (f(c)) {
                machine.consume()
            } else {
                machine.dataStack.push(fa(machine.substring(start)))
                return
            }
        }
        machine.status = ParseStatus.NeedInput()
    }

    override fun toString(): String = "SatisfyManyCharsAndMap"
    override fun pushes(): Int = 1
}
