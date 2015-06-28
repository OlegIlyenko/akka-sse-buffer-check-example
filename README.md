This is a demonstration of an issue that https://github.com/hseeberger/akka-sse/pull/46 solves.

run this to see how current implementation of `LineParser` behaves:

    sbt8 "runMain ClientAndServer"

This on will use the fix introduced in https://github.com/hseeberger/akka-sse/pull/46:

    sbt8 "runMain ClientAndServer with-fix"

