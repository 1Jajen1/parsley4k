package parsley.frontend

class CharListToString<E>(val p: ParserF<Char, E, List<Char>>) : ParserF<Char, E, String> {
    override fun toString(): String = "CharListToString($p)"
}