package tech.ankush.spark

import org.apache.spark.SparkContext._
import org.apache.spark.graphx._
import org.apache.spark.rdd._
import scala.io.Source
import scala.math.abs
import org.graphstream.graph.{Graph => GraphStream}
import org.graphstream.graph.implementations._
import org.jfree.chart.axis.ValueAxis
import breeze.linalg._
import breeze.plot._
import breeze.linalg.SparseVector

// Facebook Network Edge loader

object SNAPVizSpark {
	def main(args: Array[String]): Unit = {
    // Creates a SparkSession.
    val spark = SparkSession
      .builder
      .appName(s"${this.getClass.getSimpleName}")
      .getOrCreate()

			val conf = new SparkConf()
									.setAppName("SNAP Data Viz")
									.setMaster("local")
									.set("spark.driver.memory", "2G")

		val sc = new SparkContext(conf)
    val sc = spark.sparkContext
		// Configure the program

		//Configure Dataset Location
		val FB_DATA="Dataset/facebook/";
		val TW_DATA="Dataset/twitter/";

		//Load FacebookCombinedGraph
		val FacebookCombinedGraph = GraphLoader.edgeListFile(sc,FB_DATA+"facebook_combined.txt", numEdgePartitions = 32);
		println("FacebookCombinedGraph Loaded");
		println("Number of vertices : " + FacebookCombinedGraph.vertices.count());
		println("Number of edges : " + FacebookCombinedGraph.edges.count());
		println("Triangle counts :" + FacebookCombinedGraph.connectedComponents.triangleCount().vertices.collect().mkString("\n"))

		//Load TwitterCombinedGraph
		val TwitterCombinedGraph = GraphLoader.edgeListFile(sc,TW_DATA+"twitter_combined.txt", numEdgePartitions = 32);
		println("TwitterCombinedGraph Loaded");
		println("Number of vertices : " + TwitterCombinedGraph.vertices.count());
		println("Number of edges : " + TwitterCombinedGraph.edges.count());
		println("Triangle counts :" + TwitterCombinedGraph.connectedComponents.triangleCount().vertices.collect().mkString("\n"));

		//FacebookCombinedGraph Visualisation
		val fbGraphStream: SingleGraph = new SingleGraph("EgoSocial");
		fbGraphStream.addAttribute ("ui.stylesheet","url(file:///Users/ankushchauhan/adb_community_detection/style/fbGraphStyleSheet)");
		fbGraphStream.addAttribute("ui.quality");
		fbGraphStream.addAttribute("ui.antialias");
		for ((id,_) <- FacebookCombinedGraph.vertices.collect()) {
			val node = fbGraphStream.addNode(id.toString).asInstanceOf[SingleNode]
		}
		for (Edge(x,y,_) <- FacebookCombinedGraph.edges.collect()) {
			val edge = fbGraphStream.addEdge(x.toString ++ y.toString,
			x.toString, y.toString,
			true).
			asInstanceOf[AbstractEdge]
		}
		fbGraphStream.display()

		//TwitterCombinedGraph Visualisation
		val twGraphStream: SingleGraph = new SingleGraph("EgoSocial");
		twGraphStream.addAttribute ("ui.stylesheet","url(file:///Users/ankushchauhan/adb_community_detection/style/twGraphStyleSheet)");
		twGraphStream.addAttribute("ui.quality");
		twGraphStream.addAttribute("ui.antialias");
		for ((id,_) <- TwitterCombinedGraph.vertices.collect()) {
			val node = twGraphStream.addNode(id.toString).asInstanceOf[SingleNode]
		}
		for (Edge(x,y,_) <- TwitterCombinedGraph.edges.collect()) {
			val edge = twGraphStream.addEdge(x.toString ++ y.toString,
			x.toString, y.toString,
			true).
			asInstanceOf[AbstractEdge]
		}
		twGraphStream.display()


// Load Vertice Data -- Incomplete Parser

FacebookCombinedGraph.vertices.foreach(v => println(v))

FacebookCombinedGraph.degrees.
map(t=> (t._2,t._1)).
groupByKey.map(t =>(t._1,t._2.size)).
sortBy(_._1).collect()

type Feature = breeze.linalg.SparseVector[Int]

//Only loads one ego network
val egoNetID = 0
val featureMap: Map[Long, Feature] =
Source.fromFile(FB_DATA+egoNetID+".feat").
getLines().
map{line =>
  val row = line split ' '
  val key = abs(row.head.hashCode.toLong)
  val feat = SparseVector(row.tail.map(_.toInt))
  (key, feat)
}.toMap

val edges: RDD[Edge[Int]] =
sc.textFile(FB_DATA+egoNetID+".edges").
map {line =>
val row = line split ' '
val srcId = abs(row(0).hashCode.toLong)
val dstId = abs(row(1).hashCode.toLong)
val srcFeat = featureMap(srcId)
val dstFeat = featureMap(dstId)
val numCommonFeats = srcFeat dot dstFeat
  Edge(srcId, dstId, numCommonFeats)
}

val vertices:  RDD[(VertexId, Feature)] =
	sc.textFile(FB_DATA+egoNetID+".edges").
		map{line =>
			val key = abs(line.hashCode.toLong)
			(key, featureMap(key))
}


val egoNetwork: Graph[Int,Int] = Graph.fromEdges(edges, 1)
egoNetwork.edges.filter(_.attr == 3).count()
egoNetwork.edges.filter(_.attr == 2).count()
egoNetwork.edges.filter(_.attr == 1).count()



		// Function for computing degree distribution

    val nn = FacebookCombinedGraph.numVertices
    val egoDegreeDistribution = degreeHistogram(FacebookCombinedGraph).map({case
      (d,n) => (d,n.toDouble/nn)})
    val f = Figure()
    val p1 = f.subplot(2,1,0)
    val x = new DenseVector(egoDegreeDistribution map (_._1.toDouble))
    val y = new DenseVector(egoDegreeDistribution map (_._2))

    p1.xlabel = "Degrees"
    p1.ylabel = "Distribution"
    p1 += plot(x, y)
    p1.title = "Degree distribution of social ego network"
    val p2 = f.subplot(2,1,1)
    val egoDegrees = FacebookCombinedGraph.degrees.map(_._2).collect()
    p1.xlabel = "Degrees"
    p1.ylabel = "Histogram of node degrees"
    p2 += hist(egoDegrees, 10)

		def degreeHistogram(net: Graph[Int, Int]): Array[(Int, Int)] =
		FacebookCombinedGraph.degrees.map(t => (t._2,t._1)).
		groupByKey.map(t => (t._1,t._2.size)).
		sortBy(_._1).collect()

		spark.stop
		()sc.stop()
	}
}
