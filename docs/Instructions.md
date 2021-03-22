
Overview of instructions and optimized versions:

Primitive:
Push, Pop, Flip, Apply, JumpIfRight, Call, Jump, Label, Fail, Return, PushHandler, InputCheck, Catch, JumpGood,
 ResetOffsetOnFail, ResetOffset, ResetOnFailAndFailOnOk, Satisfy

Optimised/Instrinsic:
Map:
 - Ap(Pure(f), p)
 - Avoids one instruction and two stack operations: Push from Pure and second pop in Apply

FailIfLeft:
 - Select(p, Fail)
 - Avoids one instruction (Fail) and one push to the stack.

JumpTable:
 - Alt(p, q) where p and q have distinct leading Single/Satisfy instructions.
 - Avoids complex pushHandler/catch machinery at the cost of one Map lookup.
 - In the future this could do a lot more... TODO
  - Specialise to versions that:
   - Consume the first character
   - Reject tables instead of, or together with, accept tables

RecoverWith:
 - Alt(p, Pure(x))
 - Avoids one branch (JumpGood, Catch, Push = RecoverWith)

JumpGoodAttempt:
 - Alt(Attempt(p), q)
 - Avoids the additional InputCheck + ResetOffsetOnFail instructions, since we already have InputCheck for the Alt

RecoverWithAttempt:
 - Alt(Attempt(p), q)
 - Basically the optimisation of RecoverWith together with JumpGoodAttempt

Single:
 - Satisfy { it == c }
 - Included as primitive because its easier to analyse and thus perform further optimisations.
  - This can be circumvented if we know the entire domain of the function (and it is small enough to run every function)

_ (Single_, Satisfy_, Many_ etc.)
 - ApR(p, q) -> p's result is not needed
 - ApL(p, q) -> q's result is not needed
 - Version of that does not push to the stack
 - Avoids redundant pushes to the stack since we know the results are not needed. This can avoid a lot of work!

SatisfyMap/SingleMap:
 - Ap(Pure(f), Single/Satisfy)
 - Avoids the two stack interactions from Map, but more importantly this can avoid boxing if the input is primitive
   (Only in special cases, see the specialised variants for more info)

SatisfyN_/Single_:
 - ApR(ApR(Single, Single), p)
 - Avoids a chain of instructions, but also performs a size check before matching, which can prevent matching altogether
  if not enough input is available

Many:
 - replaces the implementation in terms of Alt for a more direct loop
 - Avoids the cost of a recursive parsing loop over the input by providing a tail-recursive loop
  - This is also included as a primitive because without function inspection we cannot reliably perform this
    optimisation... And even if, this is way easier
 - This is still quite slow because it needs to manage a few offsets to safeguard its internal state

Many_
 - As per usual this skips pushing to stack, but in doing so it can also avoid a lot the internal state management
  that Many needs to do.

SatisfyMany/SingleMany:
 - Many(Satisfy/Single)
 - Avoids the complex machinery from Many and uses just one while loop over the input. Also avoids the input checking
  because we at most consume one element and thus never fail the no-input-consumed-on-failure check

SatisfyMany_/SingleMany_
 - Even cheaper version of SatisfyMany/SingleMany because it has no accumulator

Char specialised:

CharListToString/StringToCharList:
 - Many like instructions are specialised to return Strings so we need to convert them back into Char lists if needed
 - These two cancel each other out, so if the parser does use the String this has no cost

CharJumpTable:
 - Uses a lookup table that accepts Int as a primitive and together with Char.toInt avoids boxing
     - IntMap also needs no hash function since it is a based on a radix trie

SatisfyChars_:
 - SatisyN_
 - Avoids boxing

MatchString_:
 - SingleN_
 - Avoids boxing

SatisfyChar/SingleChar/SatisfyChar_/SingleChar_/SatisfyMap/SingleMap
 - Satisfy/Satisfy_/SatisfyMap
 - Avoids boxing, except in SatisfyChar which boxes when pushing to the stack

SatisfyCharMany/SingleCharMany
 - SatisfyMany/SingleMany
 - Avoids boxing, but also avoids using an accumulator as it just tracks the start offset and then
  slices with the current offset as the end

SatisfyCharMany_/SingleCharMany_
 - SatisfyMany_/SingleCharMany_
 - Avoids boxing
