/**
  * Created by AE on 4/1/16.
  */
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.collection.mutable
import java.io.{PrintWriter, File}

case class Node(hashtag: String, timestamp: Long) {
  override def equals(o: Any) = o match {
    case that: Node => that.hashtag.equals(this.hashtag)
    case _ => false
  }
  override def hashCode = hashtag.hashCode
}

case class Edge(v: String, w: String, timestamp: Long) {
  override def equals(o: Any) = o match {
    case that: Edge => (that.v.equals(this.v) && that.w.equals(this.w)) ||
                       (that.v.equals(this.w) && that.w.equals(this.v))
    case _ => false
  }
  override def hashCode = {
    if (v.compareTo(w) > 0)
      (v,w).hashCode()
    else
      (w,v).hashCode()
  }
}

class GraphState {
  // Constant for checking expiration
  val ONE_MINUTE: Long = 60000 // milliseconds

  // Mutable State
  private var mostRecentTimestamp: Long = -1
  private val edges = mutable.ListBuffer[Edge]()
  private val nodes = mutable.ListBuffer[Node]()

  // Fast lookup for existing nodes and edges
  val hashtagMap = mutable.HashMap[String,Node]()
  val edgeSet  = mutable.HashSet[Edge]()

  // Querying the graph
  def edgeCount = edges.length
  def nodeCount = nodes.length
  def averageDegree = {
    if (nodes.isEmpty) 0.0 else (2.0 * edgeCount) / nodeCount
  }

  // Accounting methods
  def auditEdges(): Unit = {
    if (edges.length < 1)
      return
    val e = edges.head
    if (mostRecentTimestamp - e.timestamp < ONE_MINUTE) {
      return
    } else {
      edges -= e
      edgeSet.remove(e)
    }
    auditEdges()
  }

  def auditNodes(): Unit = {
    if (nodes.length < 1)
      return
    val n = nodes.head
    if (mostRecentTimestamp - n.timestamp < ONE_MINUTE) {
      return
    } else {
      nodes -= n
      hashtagMap.remove(n.hashtag)
    }
    auditNodes()
  }

  def audit(epoch: Long) = {
    if (mostRecentTimestamp < epoch) {
      mostRecentTimestamp = epoch
      auditEdges()
      auditNodes()
    }
  }

  // These two methods are for when we get something out of order
  // It is called if the received tweet is not the most recent tweet
  def insertNodesCarefully(l: mutable.ListBuffer[Node], newNodes: mutable.ListBuffer[Node]) = {
    if (newNodes.length > 0) {
      val timestamp = newNodes(0).timestamp
      val index = (l.length - 1) - l.reverseIterator.indexWhere {
        (n: Node) => n.timestamp < timestamp
      }
      l.insert(index, newNodes:_*)
    }
  }
  def insertEdgesCarefully(l: mutable.ListBuffer[Edge], newEdges: mutable.ListBuffer[Edge]) = {
    if (newEdges.length > 0) {
      val timestamp = newEdges(0).timestamp
      val index = (l.length - 1) - l.reverseIterator.indexWhere {
        (e: Edge) => e.timestamp < timestamp
      }
      l.insert(index, newEdges:_*)
    }
  }

  def processHashtags(hashtags: Set[String], epoch: Long) : Unit = {
    // First, remove any expired graph elements
    audit(epoch)

    // Short circuit if no edges are present
    if (hashtags.size < 2) return

    // First deal with "in order" tweets
    if (mostRecentTimestamp == epoch) {
      for (s <- hashtags) {
        val n: Node = Node(s, epoch)
        if (hashtagMap contains s) {
          val previous: Node = hashtagMap(s)
          nodes -= previous
          nodes.append(n)
          hashtagMap += (s -> n)
        } else {
          nodes.append(n)
          hashtagMap += (s -> n)
        }
      }
      // Finally, add new edges and update timestamps
      val pairs = hashtags.toVector combinations 2
      for (pair <- pairs) {
        val e: Edge = Edge(pair(0), pair(1), epoch)
        edgeSet find (_ == e) match {
          case Some(previous: Edge) => {
            edges -= previous
            edges.append(e)
            edgeSet += e
          }
          case None => {
            edges.append(e)
            edgeSet += e
          }
        }
      }
    } else if (mostRecentTimestamp - epoch < ONE_MINUTE) {
      // This one is out of order and needs special insertion
      val newNodes = mutable.ListBuffer[Node]()
      val newEdges = mutable.ListBuffer[Edge]()
      for (s <- hashtags) {
        val n: Node = Node(s, epoch)
        if (hashtagMap contains s) {
          val previous: Node = hashtagMap(s)
          if (previous.timestamp < epoch) {
            nodes -= previous
            newNodes.append(n)
            hashtagMap.update(s, n)
          }
        } else {
          hashtagMap.put(s, n)
          newNodes.append(n)
        }
      }
      val pairs = hashtags.toVector combinations 2
      for (pair <- pairs) {
        val e: Edge = Edge(pair(0), pair(1), epoch)
        edgeSet find (_ == e) match {
          case Some(previous: Edge) => {
            if (previous.timestamp < epoch) {
              edges -= previous
              newEdges.append(e)
              edgeSet += e
            }
          }
          case None => {
            edgeSet += (e)
            newEdges.append(e)
          }
        }
      }
      insertNodesCarefully(nodes, newNodes)
      insertEdgesCarefully(edges, newEdges)
    }
  }
}

object Main extends App {
  // The JValue case class doesn't have a "has" method
  def has(value: JValue, childString: String): Boolean = {
    if ((value \ childString) != JNothing) {
      true
    } else {
      false
    }
  }

  val graph: GraphState = new GraphState()

  // Format for parsing timestamps
  val format = new java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy")
  val writer = new PrintWriter(new File("tweet_output/output.txt"))
  val input = scala.io.Source.fromFile("tweet_input/tweets.txt")

  val t0 = System.nanoTime()
  try {
    for (x <- input.getLines) {
      val obj = parse(x)
      // Only process lines that are tweets
      if ((has(obj, "entities"))) {
        // First we extract the timestamp
        val JString(createdAt) = obj \ "created_at"
        val d = format.parse(createdAt)
        val epoch: Long = d.getTime
        // Next we extract the hashtags, removing duplicate strings
        val JArray(hashtagArray) = obj \ "entities" \ "hashtags"
        val hashtags: Set[String] = (for {
          JObject(child) <- hashtagArray
          JField("text", JString(hashtag)) <- child
        } yield hashtag).toSet

        // Update state
        graph.processHashtags(hashtags, epoch)
        writer.println(f"${graph.averageDegree}%.2f")
      }
    }
  } finally {
    writer.close()
    input.close()
  }
  val t1 = System.nanoTime()
  println("Elapsed time: " + (t1 - t0) + "ns")
}
