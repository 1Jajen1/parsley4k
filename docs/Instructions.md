
Overview of instructions and optimized versions:

Control flow:

General:
- Call: Call a subroutine parser. Adjusts the program counter and pushes the current pc onto the return stack
- Return: Return from a subroutine parser. Pops the return stack and jumps to that location.
- Jump: Jump to any point in the program
- Label: Target points for any jumping instruction. Removed on assembly.
- Fail: Fail a parser branch. Can have a special error attached.
- PushHandler: Push the location of a handler onto the handler stack. This is where we will jump to should any instruction call fail.
- InputCheck: Push the location of a handler and the current input offset onto stacks. The location is where we will jump back to on calls to fail.

Selective:
- JumpIfRight: Jump to a point in the program if the top stack element is `Either.right`. Replaces the top stack element with the content of either `Either.Left(x)` or `Either.Right(x)`. This is the most general instruction for `Select(x)`
- FailIfLeft: Fail if the top stack element is Either.Left, otherwise replace the top element with the content of Either.Right. Can have a special error attached. Specialized version of `Select(Fail)`
- FailIfLeftTop: Fail if the top stack element is `Either.Left(err)` and fail with err, otherwise replace the top element with the content of `Either.Right`. Specialized version of `Select(FailTop)` generated when using `handleEither`.

Alt:
- Catch: Recover from failure if, and only if, we have not advanced/changed the input offset.
- JumpGood: Jump to an instruction and also clean up for the handler that we are jumping over. This is usually generated with InputCheck and a form of Catch, and thus we need to pop the inputCheckStack and the handlerStack.
- JumpTable: Take, but not consume, one input element and perform a dictionary lookup. Should that succeed, jump to that point.
- RecoverWith: Catch but with a fixed element to push onto the stack. Shorthand for `Alt(p, Pure(x))`
- JumpGoodAttempt: Jump to an instruction if the parser has not failed. If it failed, reset the input offset and recover. Special version for `Alt(Attempt(p), q)`
- RecoverWithAttempt: Recover with, but also reset the input offset. `Alt(Attempt(p), Pure(x))`
- RecoverAttempt: Catch, but also reset the input offset: `Alt(Attempt(p), q)`

Attempt:
- ResetOffsetOnFail: Reset the offset to the top of the input check stack on failure and then call fail again.

LookAhead:
- ResetOffset: Reset the offset after parsing regardless of state.

NegLookAhead:
- ResetOnFailAndFailOnOk: Reset the offset after parsing. If the parser failed, we recover, if it succeeded we fail.

Data stack:
- Push: Push an element onto the stack.
- Pop: Pop an element from the stack. Should not show up in parsers very often since there are discard variants of most instructions that avoid pushing in the first place.
- Apply: Pop the top two elements from the stack, assume the second one to be a function and apply the first one to it. Push the result back onto the stack.
- Map: Special version of `Apply(Pure(f), x)` where the function is a known constant and does not need to be on the stack.
- Flip: Reverse order of the top two elements on the stack. Useful when generating code for Select.
- MkPair: Take the top two elements of the stack, put them in a Pair and put them back. Useful to avoid too many partial functions as would be required when normally doing this with just Apply.
    - This is currently only used for the `matchOf` combinators code gen and not exposed any further.

Input:
- Eof: Fail if the parser has more elements, otherwise succeed.
- Satisfy: Take one element, run the predicate and if successful consume and push that element.
- Satisfy_: Satisfy but without push
- Single: Take one element, compare to given and if equal consume and push.
- Single_: Single but without push
- SatisfyN_: Fused Satisfy_ performs a length check before running each predicate.
- SingleN_: Fused Satisfy_ performs a length check before running each predicate.
- SatisfyMap: Fused Satisfy and Map. Avoids pushing to the stack just to run Map afterwards. Tho its more useful when specializing to primitives as it allows avoiding boxing the primitive.
- SingleMap: Fused Single and Map. Pushes the result of `f(el)` from `Single(el), Map(f)` to the stack directly. This is a constant, `f` is not reapplied at all.

Intrinsics:
- PushChunkOf: Push the input sliced from the start of the inner parser to the end. Basically runs the inner parser only as validation and provides the input slice.
- Many: Stateful instruction that collects the top of the stack on each invocation (with matching return stack offset. Trick used to "know" which state to use at which depth) and pushes that list when the parser failed without consuming beyond the last successful parse. This is a bit confusing thanks to having to keep state in an instruction, but its more efficient than the recursive definition of `Many` in terms of `Alt`.
- Many_: Same as Many but with much lower overhead since it works as a stateless instruction that just changes/checks the input offset.
- SatisfyMany: Efficient version of `Many(Satisfy)`, it loops input directly rather than using the machine to do the looping. This requires *much* less effort and is thus a lot faster.
- SatisfyMany_: Discard version of SatisfyMany, effectively implemented like `takeWhile`.
- SingleMany, SingleMany_: Like SatisfyMany/SatisfyMany_ but for `Many(Single)`

String specialized versions:

General:
- CharListToString and StringToCharList: Introduced because the api for String parsers uses `String` in place of `List<Char>` and thus if the generic and string api is used interchangeably we need to do implicit conversions. If only the string api is used, these instructions are removed. Note: Kotlin gives precedence to the more specific function if two overloaded functions exist in the same package. Thus using the generic api can generally only happen if you provide explicit type information, avoid doing so!

Alt:
- CharJumpTable: JumpTable but avoids boxing.

Input:
- CharEof: Eof but avoids boxing when creating the error message.
- SatisfyChar: Satisfy but avoids boxing.
- SatisfyChar_: Satisfy_ but avoids boxing.
- SatisfyCharMap: SatisfyMap but avoids boxing.
- SingleChar: Single but avoids boxing.
- SingleChar_: Single_ but avoids boxing.
- SingleCharMap: SingleMap but avoids boxing.
- SatisfyChars_: SatisfyN_ but avoids boxing.
- MatchString_: SingleN_ but avoids boxing.

Intrinsics:
- PushStringOf: PushChunkOf but avoids boxing.
- SatisfyCharMany: SatisfyMany but avoids boxing.
- SatisfyCharMany_: SatisfyMany_ but avoids boxing.
- SingleCharMany: SingleMany but avoids boxing.
- SingleCharMany_: SingleMany_ but avoids boxing.

