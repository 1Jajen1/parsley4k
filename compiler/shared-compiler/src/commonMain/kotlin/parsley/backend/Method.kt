package parsley.backend

class Method<I, E>(val instructions: MutableList<Instruction<I, E>>) {

    operator fun get(i: Int): Instruction<I, E> = instructions[i]
    operator fun set(i: Int, new: Instruction<I, E>): Unit {
        instructions[i] = new
    }
    fun removeAt(i: Int): Unit {
        instructions.removeAt(i)
    }

    inline fun modify(ind: Int, f: (Instruction<I, E>) -> Instruction<I, E>): Unit {
        val i = instructions.removeAt(ind)
        instructions.add(ind, f(i))
    }

    inline fun forEach(f: (Instruction<I, E>) -> Unit): Unit {
        instructions.forEach(f)
    }

    fun add(i: Int, new: Instruction<I, E>): Unit {
        instructions.add(i, new)
    }

    val size
        get() = instructions.size

    override fun toString(): String =
        instructions.withIndex().map { (i, instr) -> "($i, $instr)" }.joinToString(", ")
            .let { "[$it]" }
}
