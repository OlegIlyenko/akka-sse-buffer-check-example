This is a demonstration of an issue that https://github.com/hseeberger/akka-sse/pull/46 solves.

run this to see how current implementation of `LineParser` behaves:

    sbt "runMain ClientAndServer"
    
You will see `java.lang.IllegalStateException: maxSize of 8192 exceeded!`

This one will use the fix introduced in https://github.com/hseeberger/akka-sse/pull/46:

    sbt "runMain ClientAndServer with-fix"

No exception this time.
