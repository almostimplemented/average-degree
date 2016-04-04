Insight Coding Challenge
========================

Thinking through the problem
----------------------------

The problem of computing the average degree across all nodes in a graph is
equivalent to computing the sum of the degrees of all nodes and the number of
nodes. However, the latter perspective motivates a "minimal work" approach that
will scale well.

For conciseness, we define `degreeSum` to be the sum of the degrees of all nodes in the graph. Also we define `nodeCount` to be the number of nodes in the graph. Notice the following:

- Each (new edge increases / removed edge decreases) the `degreeSum` by two.
- Each (new node increases / removed node decreases) the `nodeCount` by one.

Therefore, we need not fully recompute the average degree each iteration. We utilize to the previously computed value and analyze the events triggered by the incoming tweet.

Each tweet can trigger modifications to the graph. These triggered changes have
three forms: removals of expired edges/nodes, additions of new edges/nodes, or
updates to existing edges/nodes. The "updates" simply reflect the new timestamp.

We are led to the following high level solution:

- For each streamed tweet:
    - If the new timestamp is more than an hour older than the current timestamp:
    	-  move onto the next tweet.
    - Otherwise, if the timestamp is greater than the current most recent timestamp:
    	- remove expired graph components and update the most recent timestamp.
    - Add new edges/nodes and update existing edges/nodes with a new timestamp, and perform relevant accounting operations described above.

Implementing a solution
-----------------------
I decided to implement my solution in Scala. I have never written a full program in Scala, but I recently started learning the language via Martin Odersky's book "Programming in Scala, 2nd ed." (moving at my own pace, about 1/3 of the way through). At work, I program in Java, so that is my goto language for these sorts of challenges, since I have been learning on the job Java idioms and best practices. 

As much as I like Java, I find it very verbose. This is a blessing and a curse, because I feel like I have a good idea of what the JVM is doing with my code, but I also loathe having to generate so much boilerplate code. Scala on the other hand is very concise, and it seems to be catching on as a choice language for distributed systems development. 