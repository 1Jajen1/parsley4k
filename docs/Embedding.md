Overview of parser embedding:

Applicative:
- Pure
- Ap
- ApL/ApR: Optimized version of ap that throws away one result. Since we have no function inspection we cannot determine this at compile time.

Selective:
- Select

Alternative:
- Empty: Note: `p <*> empty = empty` and `empty <*> p`. If this is not desired use Fail
- Alt

General:
- Satisfy
- Single: Technically `Single(i) = Satisfy { it == i }` but that is much harder to optimize.
- LookAhead: Run a parser and reset the offset.
- NegLookAhead: Run a parser, fail if it succeeds, recover if not and reset the offset.
- Attempt: Backtrack on failure.
- Lazy: Solution to recursive and reused parsers. Generated implicitly, tho using `recursive` is required for recursive parsers.
- Eof: Check if we have consumed all input

Intrinsic:
- Many: More efficient `many` than defining it in terms of `alt`
- ChunkOf: Get the span of input that the underlying parser consumed.
- MatchOf: Same as ChunkOf but also returns the result of the underlying parser. This can not efficiently implement `chunkOf` because we cannot relate the discarding of only part of the result to discarding the entire underlying parser.

Error:
- Label: Apply (or hide) a label to the error message of the underlying parser (if it has not consumed any elements)
- Catch: Recover from failure and present the result or failure in an `Either<ParseError<I, E>, A>`
- Fail: Fail with a preset error. Unlike Empty Fail will never be aggressively optimized => `p <*> Fail != Fail` but `p <*> Empty = Empty`
- FailTop: Fail with the error from the top of the data stack. Handled like Fail. (Maybe those should be combined since we can optimise )

String:
- CharListToString: Satisfy the type checker by wrapping parsers that produce `List<Char>` but require `String` instead. The actual conversion is optimized away most of the time.
