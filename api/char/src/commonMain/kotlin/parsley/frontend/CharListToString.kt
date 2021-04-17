package parsley.frontend

import pretty.Doc
import pretty.align
import pretty.group
import pretty.nest
import pretty.softLine
import pretty.text

class CharListToString<E>(p: ParserF<Char, E, List<Char>>) : Unary<Char, E, List<Char>, String>(p), DistributesOrElse {
    override fun small(): Boolean = inner.small()
    override fun copy(inner: ParserF<Char, E, List<Char>>): ParserF<Char, E, String> =
        CharListToString(inner)

    override fun pprint(): Doc<Nothing> = cons(inner, "AsString")
}