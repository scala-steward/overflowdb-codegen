import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import overflowdb.{BatchedUpdate, Config, Graph}
import testschema01._
import testschema01.nodes._
import testschema01.edges._
import testschema01.traversal._
import scala.jdk.CollectionConverters.IteratorHasAsScala
class Schema01Test extends AnyWordSpec with Matchers {
  import testschema01.traversal._
  "constants" in {
    PropertyNames.NAME shouldBe "NAME"
    Properties.ORDER.name shouldBe "ORDER"
    PropertyNames.ALL.contains("OPTIONS") shouldBe true
    Properties.ALL.contains(Properties.OPTIONS) shouldBe true

    NodeTypes.NODE1 shouldBe "NODE1"
    NodeTypes.ALL.contains(Node2.Label) shouldBe true

    Node1.Properties.Name.name shouldBe PropertyNames.NAME
    Node1.PropertyNames.Order shouldBe PropertyNames.ORDER
    Node1.Edges.Out shouldBe Array(Edge1.Label)
    Node1.Edges.In shouldBe Array(Edge2.Label)

    Edge2.Properties.Name.name shouldBe PropertyNames.NAME
  }

  "working with a concrete sample graph" can {
    val graph = TestSchema.empty.graph

    val node1a = graph.addNode(Node1.Label, Node1.PropertyNames.Name, "node 1a", PropertyNames.ORDER, 2)
    val node1b = graph.addNode(Node1.Label, Node1.PropertyNames.Name, "node 1b")
    val node2a = graph.addNode(Node2.Label, PropertyNames.NAME, "node 2a", PropertyNames.OPTIONS, Seq("opt1", "opt2"))
    val node2b = graph.addNode(Node2.Label, PropertyNames.NAME, "node 2b", PropertyNames.PLACEMENTS, Seq(5,1,7))
    node1a.addEdge(Edge1.Label, node2a)
    val edge2 = node2a.addEdge(Edge2.Label, node1a, PropertyNames.NAME, "edge 2", PropertyNames.ORDER, 3)
    val node3 = graph.addNode(Node3.Label).asInstanceOf[Node3]

    // TODO generate node type starters
    def node1Traversal = graph.nodes(Node1.Label).asScala.cast[Node1]
    def node2Traversal = graph.nodes(Node2.Label).asScala.cast[Node2]

    "lookup and traverse nodes/edges/properties" in {
      // generic traversal
      graph.nodes.asScala.property(Properties.NAME).toSetMutable shouldBe Set("node 1a", "node 1b", "node 2a", "node 2b")
      graph.edges.asScala.property(Properties.NAME).toSetMutable shouldBe Set("edge 2")
      node1Traversal.out.toList shouldBe Seq(node2a)
      node1Traversal.name.toSetMutable shouldBe Set("node 1a", "node 1b")
      node1Traversal.order.l shouldBe Seq(2)
      node2Traversal.options.l shouldBe Seq("opt1", "opt2")
      node2Traversal.placements.l shouldBe Seq(5, 1, 7)

      // domain specific property lookups (generated by codegen)
      val node1aSpecific = node1a.asInstanceOf[Node1]
      val node1bSpecific = node1b.asInstanceOf[Node1]
      val node2aSpecific = node2a.asInstanceOf[Node2]
      val node2bSpecific = node2b.asInstanceOf[Node2]
      val edge2Specific = edge2.asInstanceOf[Edge2]
      val name: String = node1aSpecific.name
      name shouldBe "node 1a"
      val o1: Option[Integer] = node1aSpecific.order
      node1aSpecific.order shouldBe Some(2)
      node1bSpecific.order shouldBe None
      val o2: Seq[String] = node2aSpecific.options
      node2aSpecific.options shouldBe Seq("opt1", "opt2")
      node2bSpecific.options shouldBe Seq.empty
      node2bSpecific.placements shouldBe IndexedSeq(5, 1, 7)
      edge2Specific.name shouldBe "edge 2"
      edge2Specific.order shouldBe Some(3)

      // domain specific traversals (generated by codegen)
      node1aSpecific.edge1Out.l shouldBe Seq(node2a)
      node1aSpecific._node2ViaEdge1Out.l shouldBe Seq(node2a)
      node1aSpecific._node2ViaEdge2In shouldBe node2a
    }

    "set properties" in {
      node1a.setProperty(Node1.Properties.Name.of("updated"))
      node1a.setProperty(Node1.Properties.Order.of(4))

      // TODO generate domain-specific setters in codegen
    }

    "use generated node starters" in {
      val domainspecific = new testschema01.TestSchema(graph)
      object TmpImplicit {
        implicit def toStarter(wrapped: testschema01.TestSchema): testschema01.GeneratedNodeStarterExt = new testschema01.GeneratedNodeStarterExt(wrapped)
      }
      import TmpImplicit._
      domainspecific.node1.toList shouldBe List(node1a, node1b)
      domainspecific.node1(".*1b").toList shouldBe List(node1b)

    }

    "generate NewNodes" in {
      val newNode2 = NewNode2()
        .name("name1")
        .node3(node3)
        .options(Seq("one", "two", "three"))
        .placements(Seq(1,2,3): Seq[Integer])

      val builder = new BatchedUpdate.DiffGraphBuilder
      builder.addNode(newNode2)
      BatchedUpdate.applyDiff(graph, builder)

      val node2 = node2Traversal.name("name1").next()
      val innerNode: Option[Node3] = node2.node3
      innerNode.get shouldBe node3
    }
  }
  "work around scala bug 4762, ie generate no extraneous fields" in {
    Class.forName("testschema01.nodes.Node1").getDeclaredFields.length shouldBe 0
  }

}
