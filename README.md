Insight Coding Challenge
========================
Notes to grader
---------------
Hello, and thank you for being here to grade my submission! 

*Important note*: I modified `insight_testsuite/run_tests.sh` to `cp build.sbt` to `temp`.
My `run.sh` script simply calls `sbt run`, and I am new to the sbt tool (as I explain below,
this is my first real Scala project :] ).

I have tested the processing rate of the code. The program can iterate over 22554 tweets in 3 seconds.
From www.internetlivestats.com/twitter-statistics, I see there are ~6000 tweets generated per second
so my solution appears to be efficient enough to hook-up to the live stream. FYI, the "big-input" test
does not validate the solution to be correct; it is simply there for checking processing time.

One other detail: the Java/Scala formatted double defaults to rounding up. In the assignment's
`README.md` file, `5/3` is printed as `1.66`, whereas my program output as `1.67`. I hope this small
detail does not impact your ability to grade nor my score.

Thinking through the problem
----------------------------

The problem of computing the average degree across all nodes in a graph is
equivalent to computing the sum of the degrees of all nodes and the number of
nodes. However, the latter perspective motivates a "minimal work" approach that
will scale well.

For conciseness, we define `degreeSum` to be the sum of the degrees of all nodes
in the graph. Also we define `nodeCount` to be the number of nodes in the graph. 
Notice the following:

- Each (new edge increases / removed edge decreases) the `degreeSum` by two.
- Each (new node increases / removed node decreases) the `nodeCount` by one.

Therefore, we need not fully recompute the average degree each iteration. By 
maintaining state, we utilize to the previously computed value and analyze the
events triggered by the incoming tweet.

Each tweet can trigger modifications to the graph. These triggered changes have
three forms: removals of expired edges/nodes, additions of new edges/nodes, or
updates to existing edges/nodes. The "updates" simply reflect the new timestamp.

We are led to the following high level solution:

- For each streamed tweet:
    - If the new timestamp is more than a minute older than the current timestamp:
    	-  move onto the next tweet.
    - Otherwise, if the timestamp is greater than the current most recent timestamp:
    	- remove expired graph components and update the most recent timestamp.
    - Add new edges/nodes and update existing edges/nodes with a new timestamp, 
    and perform relevant accounting operations described above.

Implementing a solution
-----------------------
I decided to implement my solution in Scala. I have never written a full program in
Scala, but I recently started learning the language via Martin Odersky's book
"Programming in Scala, 2nd ed." (moving at my own pace, about 1/3 of the way through).
At work, I program in Java, so that is my goto language for these sorts of challenges,
since I have been learning on the job Java idioms and best practices. 

As much as I like Java, I find it very verbose. This is a blessing and a curse, because
I feel like I have a good idea of what the JVM is doing with my code, but I also loathe
having to generate so much boilerplate code. Scala on the other hand is very concise,
and it seems to be catching on as a choice language for distributed systems development. 

The only dependency my project has is the `json4s` library for easy JSON parsing.
This has the benefit of saving me a lot of IO programming and makes the code much cleaner.

The graph is defined inside the class `GraphState`. Referring back to my high level solution,
the method `GraphState.processHashtags(Set[String],Long)` takes as input a set of hashtags
and a timestamp (milliseconds since the epoch). Internally, it has two `ListBuffer` objects
which store the past minute's set of nodes and edges. Through careful insertion/deletion logic,
these lists remain in sorted order from oldest to newest. This provides an efficient way to
inspect which nodes/edges are expired. The function `GraphState.audit(Long)` performs this inspection.
It consists of first checking if the passed `Long` is more recent that the current timestamp.
If so, the `mostRecentTimestamp` is updated and two tail recursive functions are called.
These functions recursively step through the lists of nodes and edges, removing the head until
the recursion bottoms out or valid timestamp is reached.

If the received tweet is the latest one, the insertions are easy: we just append to the lists.
Otherwise, we collect the received hashtags into a list, walk backwards from the end of the list
until we find an older timestamp, and then insert all objects at once. We also take care to not 
remove an existing, more recent copy of a new node/edge encountered out of order.

Two hash-based collections are maintained to expedite the check for existing nodes (hashtags). Nodes
are implemented as a composite class of `String` and `Long` (hashtag and timestamp), and edges are
a composition of two `String`s and a `Long`. The `equals` and `hashCode` methods for these two classes
are overridden to ignore the timestamp, and in the case of edges to encode undirectedness.

And thats pretty much it! The node and edge lists have `length` accessor fields, and from those we
compute `averageDegree = (2.0 * edges.length) / nodes.length` (unless of course there are no nodes).

