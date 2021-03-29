package parsley.frontend

class CharListToString<E>(p: ParserF<Char, E, List<Char>>) : Unary<Char, E, List<Char>, String>(p) {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<Char, E, List<Char>>): ParserF<Char, E, String> =
        CharListToString(inner)
    override fun toString(): String = "CharListToString($inner)"
}