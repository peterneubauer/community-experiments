/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher

import internal.commands._
import org.junit.Assert._
import java.lang.String
import scala.collection.JavaConverters._
import org.junit.matchers.JUnitMatchers._
import org.neo4j.graphdb.{Path, Relationship, Direction, Node}
import org.junit.{Ignore, Test}
import org.neo4j.index.lucene.ValueContext
import org.neo4j.test.ImpermanentGraphDatabase

class ExecutionEngineTest extends ExecutionEngineHelper {

  @Test def shouldGetReferenceNode() {
    val query = Query.
      start(NodeById("n", Literal(0))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)
    assertEquals(List(refNode), result.columnAs[Node]("n").toList)
  }

  @Test def shouldGetRelationshipById() {
    val n = createNode()
    val r = relate(n, refNode, "KNOWS")

    val query = Query.
      start(RelationshipById("r", Literal(0))).
      returns(ReturnItem(Entity("r"), "r"))

    val result = execute(query)
    assertEquals(List(r), result.columnAs[Relationship]("r").toList)
  }

  @Test def shouldFilterOnGreaterThan() {
    val result = parseAndExecute("start node=node(0) where 0<1 return node")

    assertEquals(List(refNode), result.columnAs[Node]("node").toList)
  }

  @Test def shouldFilterOnRegexp() {
    val n1 = createNode(Map("name" -> "Andres"))
    val n2 = createNode(Map("name" -> "Jim"))

    val query = Query.
      start(NodeById("node", n1.getId, n2.getId)).
      where(RegularExpression(Property("node", "name"), Literal("And.*"))).
      returns(ReturnItem(Entity("node"), "node"))

    val result = execute(query)
    assertEquals(List(n1), result.columnAs[Node]("node").toList)
  }

  @Test def shouldBeAbleToUseParamsInPatternMatchingPredicates() {
    val n1 = createNode()
    val n2 = createNode()
    relate(n1, n2, "A", Map("foo" -> "bar"))

    val result = parseAndExecute("start a=node(1) match a-[r]->b where r.foo =~ {param} return b", "param" -> "bar")

    assertEquals(List(n2), result.columnAs[Node]("b").toList)
  }

  @Test def shouldGetOtherNode() {
    val node: Node = createNode()

    val query = Query.
      start(NodeById("node", node.getId)).
      returns(ReturnItem(Entity("node"), "node"))

    val result = execute(query)
    assertEquals(List(node), result.columnAs[Node]("node").toList)
  }

  @Test def shouldGetRelationship() {
    val node: Node = createNode()
    val rel: Relationship = relate(refNode, node, "yo")

    val query = Query.
      start(RelationshipById("rel", rel.getId)).
      returns(ReturnItem(Entity("rel"), "rel"))

    val result = execute(query)
    assertEquals(List(rel), result.columnAs[Relationship]("rel").toList)
  }

  @Test def shouldGetTwoNodes() {
    val node: Node = createNode()

    val query = Query.
      start(NodeById("node", refNode.getId, node.getId)).
      returns(ReturnItem(Entity("node"), "node"))

    val result = execute(query)
    assertEquals(List(refNode, node), result.columnAs[Node]("node").toList)
  }

  @Test def shouldGetNodeProperty() {
    val name = "Andres"
    val node: Node = createNode(Map("name" -> name))

    val query = Query.
      start(NodeById("node", node.getId)).
      returns(ReturnItem(Property("node", "name"), "node.name"))

    val result = execute(query)
    val list = result.columnAs[String]("node.name").toList
    assertEquals(List(name), list)
  }

  @Test def shouldFilterOutBasedOnNodePropName() {
    val name = "Andres"
    val start: Node = createNode()
    val a1: Node = createNode(Map("name" -> "Someone Else"))
    val a2: Node = createNode(Map("name" -> name))
    relate(start, a1, "x")
    relate(start, a2, "x")

    val query = Query.
      start(NodeById("start", start.getId)).
      matches(RelatedTo("start", "a", "rel", "x", Direction.BOTH)).
      where(Equals(Property("a", "name"), Literal(name))).
      returns(ReturnItem(Entity("a"), "a"))

    val result = execute(query)
    assertEquals(List(a2), result.columnAs[Node]("a").toList)
  }

  @Test def shouldFilterBasedOnRelPropName() {
    val start: Node = createNode()
    val a: Node = createNode()
    val b: Node = createNode()
    relate(start, a, "KNOWS", Map("name" -> "monkey"))
    relate(start, b, "KNOWS", Map("name" -> "woot"))

    val query = Query.
      start(NodeById("start", start.getId)).
      matches(RelatedTo("start", "a", "r", "KNOWS", Direction.BOTH)).
      where(Equals(Property("r", "name"), Literal("monkey"))).
      returns(ReturnItem(Entity("a"), "a"))

    val result = execute(query)
    assertEquals(List(a), result.columnAs[Node]("a").toList)
  }

  @Test def shouldOutputTheCartesianProductOfTwoNodes() {
    val n1: Node = createNode()
    val n2: Node = createNode()

    val query = Query.
      start(NodeById("n1", n1.getId), NodeById("n2", n2.getId)).
      returns(ReturnItem(Entity("n1"), "n1"), ReturnItem(Entity("n2"), "n2"))

    val result = execute(query)

    assertEquals(List(Map("n1" -> n1, "n2" -> n2)), result.toList)
  }

  @Test def shouldGetNeighbours() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    relate(n1, n2, "KNOWS")

    val query = Query.
      start(NodeById("n1", n1.getId)).
      matches(RelatedTo("n1", "n2", "rel", "KNOWS", Direction.OUTGOING)).
      returns(ReturnItem(Entity("n1"), "n1"), ReturnItem(Entity("n2"), "n2"))

    val result = execute(query)

    assertEquals(List(Map("n1" -> n1, "n2" -> n2)), result.toList)
  }

  @Test def shouldGetTwoRelatedNodes() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    val n3: Node = createNode()
    relate(n1, n2, "KNOWS")
    relate(n1, n3, "KNOWS")

    val query = Query.
      start(NodeById("start", n1.getId)).
      matches(RelatedTo("start", "x", "rel", "KNOWS", Direction.OUTGOING)).
      returns(ReturnItem(Entity("x"), "x"))

    val result = execute(query)

    assertEquals(List(Map("x" -> n2), Map("x" -> n3)), result.toList)
  }

  @Test def executionResultTextualOutput() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    val n3: Node = createNode()
    relate(n1, n2, "KNOWS")
    relate(n1, n3, "KNOWS")

    val query = Query.
      start(NodeById("start", n1.getId)).
      matches(RelatedTo("start", "x", "rel", "KNOWS", Direction.OUTGOING)).
      returns(ReturnItem(Entity("x"), "x"), ReturnItem(Entity("start"), "start"))

    val result = execute(query)

    val textOutput = result.dumpToString()
  }

  @Test def doesNotFailOnVisualizingEmptyOutput() {
    val query = Query.
      start(NodeById("start", refNode.getId)).
      where(Equals(Literal(1), Literal(0))).
      returns(ReturnItem(Entity("start"), "start"))

    val result = execute(query)
  }

  @Test def shouldGetRelatedToRelatedTo() {
    val n1: Node = createNode()
    val n2: Node = createNode()
    val n3: Node = createNode()
    relate(n1, n2, "KNOWS")
    relate(n2, n3, "FRIEND")

    val query = Query.
      start(NodeById("start", n1.getId)).
      matches(
      RelatedTo("start", "a", "rel", "KNOWS", Direction.OUTGOING),
      RelatedTo("a", "b", "rel2", "FRIEND", Direction.OUTGOING)).
      returns(ReturnItem(Entity("b"), "b"))

    val result = execute(query)

    assertEquals(List(Map("b" -> n3)), result.toList)
  }

  @Test def shouldFindNodesByExactIndexLookup() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    val value = "andres"
    indexNode(n, idxName, key, value)

    val query = Query.
      start(NodeByIndex("n", idxName, Literal(key), Literal(value))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldFindNodesByIndexQuery() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    val value = "andres"
    indexNode(n, idxName, key, value)

    val query = Query.
      start(NodeByIndexQuery("n", idxName, Literal(key + ":" + value))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldFindNodesByIndexParameters() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    indexNode(n, idxName, key, "Andres")

    val query = Query.
      start(NodeByIndex("n", idxName, Literal(key), ParameterExpression("value"))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query, "value" -> "Andres")

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldFindNodesByIndexWildcardQuery() {
    val n = createNode()
    val idxName = "idxName"
    val key = "key"
    val value = "andres"
    indexNode(n, idxName, key, value)

    val query = Query.
      start(NodeByIndexQuery("n", idxName, Literal(key + ":andr*"))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(Map("n" -> n)), result.toList)
  }

  @Test def shouldHandleOrFilters() {
    val n1 = createNode(Map("name" -> "boy"))
    val n2 = createNode(Map("name" -> "girl"))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId)).
      where(Or(
      Equals(Property("n", "name"), Literal("boy")),
      Equals(Property("n", "name"), Literal("girl")))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(n1, n2), result.columnAs[Node]("n").toList)
  }


  @Test def shouldHandleNestedAndOrFilters() {
    val n1 = createNode(Map("animal" -> "monkey", "food" -> "banana"))
    val n2 = createNode(Map("animal" -> "cow", "food" -> "grass"))
    val n3 = createNode(Map("animal" -> "cow", "food" -> "banana"))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId)).
      where(Or(
      And(
        Equals(Property("n", "animal"), Literal("monkey")),
        Equals(Property("n", "food"), Literal("banana"))),
      And(
        Equals(Property("n", "animal"), Literal("cow")),
        Equals(Property("n", "food"), Literal("grass"))))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(n1, n2), result.columnAs[Node]("n").toList)
  }

  @Test def shouldBeAbleToOutputNullForMissingProperties() {
    val query = Query.
      start(NodeById("node", 0)).
      returns(ReturnItem(Nullable(Property("node", "name")), "node.name?"))

    val result = execute(query)
    assertEquals(List(Map("node.name?" -> null)), result.toList)
  }

  @Test def testOnlyIfPropertyExists() {
    createNode(Map("prop" -> "A"))
    createNode()

    val result = parseAndExecute("start a=node(1,2) where a.prop? = 'A' return a")

    assert(2 === result.toSeq.length)
  }

  @Test def shouldHandleComparisonBetweenNodeProperties() {
    //start n = node(1,4) match (n) --> (x) where n.animal = x.animal return n,x
    val n1 = createNode(Map("animal" -> "monkey"))
    val n2 = createNode(Map("animal" -> "cow"))
    val n3 = createNode(Map("animal" -> "monkey"))
    val n4 = createNode(Map("animal" -> "cow"))

    relate(n1, n2, "A")
    relate(n1, n3, "A")
    relate(n4, n2, "A")
    relate(n4, n3, "A")

    val query = Query.
      start(NodeById("n", n1.getId, n4.getId)).
      matches(RelatedTo("n", "x", "rel", Seq(), Direction.OUTGOING, false, True())).
      where(Equals(Property("n", "animal"), Property("x", "animal"))).
      returns(ReturnItem(Entity("n"), "n"), ReturnItem(Entity("x"), "x"))

    val result = execute(query)

    assertEquals(List(
      Map("n" -> n1, "x" -> n3),
      Map("n" -> n4, "x" -> n2)), result.toList)

    assertEquals(List("n", "x"), result.columns)
  }

  @Test def comparingNumbersShouldWorkNicely() {
    val n1 = createNode(Map("x" -> 50))
    val n2 = createNode(Map("x" -> 50l))
    val n3 = createNode(Map("x" -> 50f))
    val n4 = createNode(Map("x" -> 50d))
    val n5 = createNode(Map("x" -> 50.toByte))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId, n4.getId, n5.getId)).
      where(LessThan(Property("n", "x"), Literal(100))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(n1, n2, n3, n4, n5), result.columnAs[Node]("n").toList)
  }

  @Test def comparingStringAndCharsShouldWorkNicely() {
    val n1 = createNode(Map("x" -> "Anders"))
    val n2 = createNode(Map("x" -> 'C'))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId)).
      where(And(
      LessThan(Property("n", "x"), Literal("Z")),
      LessThan(Property("n", "x"), Literal('Z')))).
      returns(ReturnItem(Entity("n"), "n"))

    val result = execute(query)

    assertEquals(List(n1, n2), result.columnAs[Node]("n").toList)
  }

  @Test def shouldBeAbleToCountNodes() {
    val a = createNode() //start a = (0) match (a) --> (b) return a, count(*)
    val b = createNode()
    relate(refNode, a, "A")
    relate(refNode, b, "A")

    val query = Query.
      start(NodeById("a", refNode.getId)).
      matches(RelatedTo("a", "b", "rel", Seq(), Direction.OUTGOING, false, True())).
      aggregation(CountStar()).
      returns(ReturnItem(Entity("a"), "a"), ReturnItem(CountStar(), "count(*)"))

    val result = execute(query)

    assertEquals(List(Map("a" -> refNode, "count(*)" -> 2)), result.toList)
  }

  @Test def shouldReturnTwoSubgraphsWithBoundUndirectedRelationship() {
    val a = createNode("a")
    val b = createNode("b")
    relate(a, b, "rel", "r")

    val result = parseAndExecute("start r=rel(0) match a-[r]-b return a,b")

    assertEquals(List(Map("a" -> a, "b" -> b), Map("a" -> b, "b" -> a)), result.toList)
  }

  @Test def shouldAcceptSkipZero() {
    val result = parseAndExecute("start n=node(0) where 1 = 0 return n skip 0")

    assertEquals(List(), result.columnAs[Node]("n").toList)
  }

  @Test def shouldReturnTwoSubgraphsWithBoundUndirectedRelationshipAndOptionalRelationship() {
    val a = createNode("a")
    val b = createNode("b")
    val c = createNode("c")
    relate(a, b, "rel", "r")
    relate(b, c, "rel", "r2")

    val result = parseAndExecute("start r=rel(0) match a-[r]-b-[?]-c return a,b,c")

    assertEquals(List(Map("a" -> a, "b" -> b, "c" -> c), Map("a" -> b, "b" -> a, "c" -> null)), result.toList)
  }

  @Test def shouldLimitToTwoHits() {
    createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      limit(2).
      returns(ReturnItem(Entity("start"), "start"))

    val result = execute(query)

    assertEquals("Result was not trimmed down", 2, result.size)
  }

  @Test def shouldStartTheResultFromSecondRow() {
    val nodes = createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      orderBy(SortItem(Property("start", "name"), true)).
      skip(2).
      returns(ReturnItem(Entity("start"), "start"))

    val result = execute(query)

    assertEquals(nodes.drop(2).toList, result.columnAs[Node]("start").toList)
  }

  @Test def shouldStartTheResultFromSecondRowByParam() {
    val nodes = createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      orderBy(SortItem(Property("start", "name"), true)).
      skip("skippa").
      returns(ReturnItem(Entity("start"), "start"))

    val result = execute(query, "skippa" -> 2)

    assertEquals(nodes.drop(2).toList, result.columnAs[Node]("start").toList)
  }

  @Test def shouldGetStuffInTheMiddle() {
    val nodes = createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      orderBy(SortItem(Property("start", "name"), true)).
      limit(2).
      skip(2).
      returns(ReturnItem(Entity("start"), "start"))

    val result = execute(query)

    assertEquals(nodes.slice(2, 4).toList, result.columnAs[Node]("start").toList)
  }

  @Test def shouldGetStuffInTheMiddleByParam() {
    val nodes = createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(NodeById("start", nodeIds: _*)).
      orderBy(SortItem(Property("start", "name"), true)).
      limit("l").
      skip("s").
      returns(ReturnItem(Entity("start"), "start"))

    val result = execute(query, "l" -> 2, "s" -> 2)

    assertEquals(nodes.slice(2, 4).toList, result.columnAs[Node]("start").toList)
  }

  @Test def shouldSortOnAggregatedFunction() {
    createNode(Map("name" -> "andres", "division" -> "Sweden", "age" -> 33))
    createNode(Map("name" -> "michael", "division" -> "Germany", "age" -> 22))
    createNode(Map("name" -> "jim", "division" -> "England", "age" -> 55))
    createNode(Map("name" -> "anders", "division" -> "Sweden", "age" -> 35))

    val result = parseAndExecute("start n=node(1,2,3,4) return n.division, max(n.age) order by max(n.age) ")

    assertEquals(List("Germany", "Sweden", "England"), result.columnAs[String]("n.division").toList)
  }

  @Test def shouldSortOnAggregatedFunctionAndNormalProperty() {
    val n1 = createNode(Map("name" -> "andres", "division" -> "Sweden"))
    val n2 = createNode(Map("name" -> "michael", "division" -> "Germany"))
    val n3 = createNode(Map("name" -> "jim", "division" -> "England"))
    val n4 = createNode(Map("name" -> "mattias", "division" -> "Sweden"))

    val query = Query.
      start(NodeById("n", n1.getId, n2.getId, n3.getId, n4.getId)).
      aggregation(CountStar()).
      orderBy(
      SortItem(CountStar(), false),
      SortItem(Property("n", "division"), true)).
      returns(ReturnItem(Property("n", "division"), "n.division"), ReturnItem(CountStar(), "count(*)"))

    val result = execute(query)

    assertEquals(List(
      Map("n.division" -> "Sweden", "count(*)" -> 2),
      Map("n.division" -> "England", "count(*)" -> 1),
      Map("n.division" -> "Germany", "count(*)" -> 1)), result.toList)
  }

  @Test def magicRelTypeWorksAsExpected() {
    createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val query = Query.
      start(NodeById("n", 1)).
      matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING, false, True())).
      where(Equals(RelationshipTypeFunction(Entity("r")), Literal("KNOWS"))).
      returns(ReturnItem(Entity("x"), "x"))

    val result = execute(query)

    assertEquals(List(node("B")), result.columnAs[Node]("x").toList)
  }

  @Test def magicRelTypeOutput() {
    createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val query = Query.
      start(NodeById("n", 1)).
      matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING, false, True())).
      returns(ReturnItem(RelationshipTypeFunction(Entity("r")), "type(r)"))

    val result = execute(query)

    assertEquals(List("KNOWS", "HATES"), result.columnAs[String]("type(r)").toList)
  }

  @Test def shouldAggregateOnProperties() {
    val n1 = createNode(Map("x" -> 33))
    val n2 = createNode(Map("x" -> 33))
    val n3 = createNode(Map("x" -> 42))

    val query = Query.
      start(NodeById("node", n1.getId, n2.getId, n3.getId)).
      aggregation(CountStar()).
      returns(ReturnItem(Property("node", "x"), "node.x"), ReturnItem(CountStar(), "count(*)"))

    val result = execute(query)

    assertThat(result.toList.asJava, hasItems[Map[String, Any]](Map("node.x" -> 33, "count(*)" -> 2), Map("node.x" -> 42, "count(*)" -> 1)))
  }

  @Test def shouldCountNonNullValues() {
    createNode(Map("y" -> "a", "x" -> 33))
    createNode(Map("y" -> "a"))
    createNode(Map("y" -> "b", "x" -> 42))

    val result = parseAndExecute("start n=node(1,2,3) return n.y, count(n.x?)")

    assertThat(result.toList.asJava,
      hasItems[Map[String, Any]](
        Map("n.y" -> "a", "count(n.x?)" -> 1),
        Map("n.y" -> "b", "count(n.x?)" -> 1)))
  }

  @Test def shouldSumNonNullValues() {
    createNode(Map("y" -> "a", "x" -> 33))
    createNode(Map("y" -> "a"))
    createNode(Map("y" -> "a", "x" -> 42))

    val result = parseAndExecute("start n = node(1,3) return n.y, sum(n.x)")

    assertThat(result.toList.asJava,
      hasItems[Map[String, Any]](Map("n.y" -> "a", "sum(n.x)" -> 75)))
  }

  @Test def shouldWalkAlternativeRelationships() {
    val nodes: List[Node] = createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val query = Query.
      start(NodeById("n", 1)).
      matches(RelatedTo("n", "x", "r", Seq(), Direction.OUTGOING, false, True())).
      where(Or(Equals(RelationshipTypeFunction(Entity("r")), Literal("KNOWS")), Equals(RelationshipTypeFunction(Entity("r")), Literal("HATES")))).
      returns(ReturnItem(Entity("x"), "x"))

    val result = execute(query)

    assertEquals(nodes.slice(1, 3), result.columnAs[Node]("x").toList)
  }

  @Test def shouldReturnASimplePath() {
    createNodes("A", "B")
    val r = relate("A" -> "KNOWS" -> "B")

    val query = Query.
      start(NodeById("a", 1)).
      namedPaths(NamedPath("p", RelatedTo("a", "b", "rel", Seq(), Direction.OUTGOING, false, True()))).
      returns(ReturnItem(Entity("p"), "p")) //  new CypherParser().parse("start a=(1) match p=(a-->b) return p")

    val result = execute(query)

    assertEquals(List(PathImpl(node("A"), r, node("B"))), result.columnAs[Path]("p").toList)
  }

  @Test def shouldReturnAThreeNodePath() {
    createNodes("A", "B", "C")
    val r1 = relate("A" -> "KNOWS" -> "B")
    val r2 = relate("B" -> "KNOWS" -> "C")


    val query = Query.
      start(NodeById("a", 1)).
      namedPaths(NamedPath("p",
      RelatedTo("a", "b", "rel1", Seq(), Direction.OUTGOING, false, True()),
      RelatedTo("b", "c", "rel2", Seq(), Direction.OUTGOING, false, True()))).
      returns(ReturnItem(Entity("p"), "p")) //  new CypherParser().parse("start a=(1) match p=(a-->b) return p")

    val result = execute(query)

    assertEquals(List(PathImpl(node("A"), r1, node("B"), r2, node("C"))), result.columnAs[Path]("p").toList)
  }

  @Test def shouldWalkAlternativeRelationships2() {
    val nodes: List[Node] = createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("A" -> "HATES" -> "C")

    val result = parseAndExecute("start n=node(1) match (n)-[r]->(x) where type(r)='KNOWS' or type(r) = 'HATES' return x")

    assertEquals(nodes.slice(1, 3), result.columnAs[Node]("x").toList)
  }

  @Test def shouldNotReturnAnythingBecausePathLengthDoesntMatch() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=node(1) match p = n-->x where length(p) = 10 return x")

    assertTrue("Result set should be empty, but it wasn't", result.isEmpty)
  }

  @Ignore
  @Test def statingAPathTwiceShouldNotBeAProblem() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=node(1) match x<--n, p = n-->x return p")

    assertEquals(1, result.toSeq.length)
  }

  @Test def shouldPassThePathLengthTest() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=node(1) match p = n-->x where length(p)=1 return x")

    assertTrue("Result set should not be empty, but it was", !result.isEmpty)
  }

  @Test def shouldReturnPathLength() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start n=node(1) match p = n-->x return length(p)")

    assertEquals(List(1), result.columnAs[Int]("length(p)").toList)
  }

  @Test def shouldBeAbleToFilterOnPathNodes() {
    val a = createNode(Map("foo" -> "bar"))
    val b = createNode(Map("foo" -> "bar"))
    val c = createNode(Map("foo" -> "bar"))
    val d = createNode(Map("foo" -> "bar"))

    relate(a, b, "rel")
    relate(b, c, "rel")
    relate(c, d, "rel")

    val query = Query.start(NodeById("pA", a.getId), NodeById("pB", d.getId)).
      namedPaths(NamedPath("p", VarLengthRelatedTo("x", "pA", "pB", Some(1), Some(5), "rel", Direction.OUTGOING))).
      where(AllInIterable(NodesFunction(Entity("p")), "i", Equals(Property("i", "foo"), Literal("bar")))).
      returns(ReturnItem(Entity("pB"), "pB"))

    val result = execute(query)

    assertEquals(List(d), result.columnAs[Node]("pB").toList)
  }

  @Test def shouldReturnRelationships() {
    val a = createNode(Map("foo" -> "bar"))
    val b = createNode(Map("foo" -> "bar"))
    val c = createNode(Map("foo" -> "bar"))

    val r1 = relate(a, b, "rel")
    val r2 = relate(b, c, "rel")

    val query = Query.start(NodeById("pA", a.getId)).
      namedPaths(NamedPath("p", VarLengthRelatedTo("x", "pA", "pB", Some(2), Some(2), "rel", Direction.OUTGOING))).
      returns(ReturnItem(RelationshipFunction(Entity("p")), "RELATIONSHIPS(p)"))

    val result = execute(query)

    assertEquals(List(r1, r2), result.columnAs[Node]("RELATIONSHIPS(p)").toList.head)
  }

  @Test def shouldReturnAVarLengthPath() {
    createNodes("A", "B", "C")
    val r1 = relate("A" -> "KNOWS" -> "B")
    val r2 = relate("B" -> "KNOWS" -> "C")

    val result = parseAndExecute("start n=node(1) match p=n-[:KNOWS*1..2]->x return p")

    val toList: List[Path] = result.columnAs[Path]("p").toList
    assertEquals(List(
      PathImpl(node("A"), r1, node("B")),
      PathImpl(node("A"), r1, node("B"), r2, node("C"))
    ), toList)
  }

  @Test def aVarLengthPathOfLengthZero() {
    createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")
    relate("B" -> "KNOWS" -> "C")

    val result = parseAndExecute("start a=node(1) match a-[*0..1]->b return a,b")

    assertEquals(
      Set(
        Map("a" -> node("A"), "b" -> node("B")),
        Map("a" -> node("A"), "b" -> node("A"))),
      result.toSet)
  }

  @Test def aNamedVarLengthPathOfLengthZero() {
    createNodes("A", "B", "C")
    val r1 = relate("A" -> "KNOWS" -> "B")
    val r2 = relate("B" -> "FRIEND" -> "C")

    val result = parseAndExecute("start a=node(1) match p=a-[:KNOWS*0..1]->b-[:FRIEND*0..1]->c return p,a,b,c")

    assertEquals(
      Set(
        PathImpl(node("A")),
        PathImpl(node("A"), r1, node("B")),
        PathImpl(node("A"), r1, node("B"), r2, node("C"))
      ),
      result.columnAs[Path]("p").toSet)
  }

  @Test def testZeroLengthVarLenPathInTheMiddle() {
    createNodes("A", "B", "C", "D", "E")
    relate("A" -> "CONTAINS" -> "B")
    relate("B" -> "FRIEND" -> "C")


    val result = parseAndExecute("start a=node(1) match a-[:CONTAINS*0..1]->b-[:FRIEND*0..1]->c return a,b,c")

    assertEquals(
      Set(
        Map("a" -> node("A"), "b" -> node("A"), "c" -> node("A")),
        Map("a" -> node("A"), "b" -> node("B"), "c" -> node("B")),
        Map("a" -> node("A"), "b" -> node("B"), "c" -> node("C"))),
      result.toSet)
  }

  @Test def shouldReturnAVarLengthPathWithoutMinimalLength() {
    createNodes("A", "B", "C")
    val r1 = relate("A" -> "KNOWS" -> "B")
    val r2 = relate("B" -> "KNOWS" -> "C")

    val result = parseAndExecute("start n=node(1) match p=n-[:KNOWS*..2]->x return p")

    assertEquals(List(
      PathImpl(node("A"), r1, node("B")),
      PathImpl(node("A"), r1, node("B"), r2, node("C"))
    ), result.columnAs[Path]("p").toList)
  }

  @Test def shouldReturnAVarLengthPathWithUnboundMax() {
    createNodes("A", "B", "C")
    val r1 = relate("A" -> "KNOWS" -> "B")
    val r2 = relate("B" -> "KNOWS" -> "C")

    val result = parseAndExecute("start n=node(1) match p=n-[:KNOWS*..]->x return p")

    assertEquals(List(
      PathImpl(node("A"), r1, node("B")),
      PathImpl(node("A"), r1, node("B"), r2, node("C"))
    ), result.columnAs[Path]("p").toList)
  }


  @Test def shouldHandleBoundNodesNotPartOfThePattern() {
    createNodes("A", "B", "C")
    relate("A" -> "KNOWS" -> "B")

    val result = parseAndExecute("start a=node(1), c = node(3) match a-->b return a,b,c").toList

    assert(List(Map("a" -> node("A"), "b" -> node("B"), "c" -> node("C"))) === result)
  }

  @Test def shouldReturnShortestPath() {
    createNodes("A", "B")
    val r1 = relate("A" -> "KNOWS" -> "B")

    val query = Query.
      start(NodeById("a", 1), NodeById("b", 2)).
      matches(ShortestPath("p", "a", "b", Seq(), Direction.BOTH, Some(15), false, true, None)).
      returns(ReturnItem(Entity("p"), "p"))

    val result = execute(query).toList.head("p").asInstanceOf[Path]

    val number_of_relationships_in_path = result.length()
    assert(number_of_relationships_in_path === 1)
    assert(result.startNode() === node("A"))
    assert(result.endNode() === node("B"))
    assert(result.lastRelationship() === r1)
  }

  @Test def shouldReturnShortestPathUnboundLength() {
    createNodes("A", "B")
    relate("A" -> "KNOWS" -> "B")

    val query = Query.
      start(NodeById("a", 1), NodeById("b", 2)).
      matches(ShortestPath("p", "a", "b", Seq(), Direction.BOTH, None, false, true, None)).
      returns(ReturnItem(Entity("p"), "p"))

    //Checking that we don't get an exception
    execute(query).toList
  }

  @Test def shouldBeAbleToTakeParamsInDifferentTypes() {
    createNodes("A", "B", "C", "D", "E")

    val query = Query.
      start(
      NodeById("pA", ParameterExpression("a")),
      NodeById("pB", ParameterExpression("b")),
      NodeById("pC", ParameterExpression("c")),
      NodeById("pD", ParameterExpression("0")),
      NodeById("pE", ParameterExpression("1"))).
      returns(
      ReturnItem(Entity("pA"), "pA"),
      ReturnItem(Entity("pB"), "pB"),
      ReturnItem(Entity("pC"), "pC"),
      ReturnItem(Entity("pD"), "pD"),
      ReturnItem(Entity("pE"), "pE"))

    val result = execute(query,
      "a" -> Seq[Long](1),
      "b" -> 2,
      "c" -> Seq(3L).asJava,
      "0" -> Seq(4).asJava,
      "1" -> List(5)
    )

    assertEquals(1, result.toList.size)
  }

  @Test(expected = classOf[ParameterWrongTypeException]) def parameterTypeErrorShouldBeNicelyExplained() {
    createNodes("A")

    val query = Query.
      start(NodeById("pA", ParameterExpression("a"))).
      returns(ReturnItem(Entity("pA"), "pA"))

    execute(query, "a" -> "Andres").toList
  }

  @Test def shouldBeAbleToTakeParamsFromParsedStuff() {
    createNodes("A")

    val query = new CypherParser().parse("start pA = node({a}) return pA")
    val result = execute(query, "a" -> Seq[Long](1))

    assertEquals(List(Map("pA" -> node("A"))), result.toList)
  }

  @Test def shouldBeAbleToTakeParamsForEqualityComparisons() {
    createNode(Map("name" -> "Andres"))

    val query = Query.
      start(NodeById("a", 1)).
      where(Equals(Property("a", "name"), ParameterExpression("name")))
      .returns(ReturnItem(Entity("a"), "a"))

    assert(0 === execute(query, "name" -> "Tobias").toList.size)
    assert(1 === execute(query, "name" -> "Andres").toList.size)
  }

  @Test def shouldHandlePatternMatchingWithParameters() {
    val a = createNode()
    val b = createNode(Map("name" -> "you"))
    relate(a, b, "KNOW")

    val result = parseAndExecute("start x  = node({startId}) match x-[r]-friend where friend.name = {name} return TYPE(r)", "startId" -> 1, "name" -> "you")

    assert(List(Map("TYPE(r)" -> "KNOW")) === result.toList)
  }

  @Test def twoBoundNodesPointingToOne() {
    val a = createNode("A")
    val b = createNode("B")
    val x1 = createNode("x1")
    val x2 = createNode("x2")

    relate(a, x1, "REL", "AX1")
    relate(a, x2, "REL", "AX2")

    relate(b, x1, "REL", "BX1")
    relate(b, x2, "REL", "BX2")

    val result = parseAndExecute( """
start a  = node({A}), b = node({B})
match a-[rA]->x<-[rB]->b
return x""", "A" -> 1, "B" -> 2)

    assert(List(x1, x2) === result.columnAs[Node]("x").toList)
  }


  @Test def threeBoundNodesPointingToOne() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")
    val x1 = createNode("x1")
    val x2 = createNode("x2")

    relate(a, x1, "REL", "AX1")
    relate(a, x2, "REL", "AX2")

    relate(b, x1, "REL", "BX1")
    relate(b, x2, "REL", "BX2")

    relate(c, x1, "REL", "CX1")
    relate(c, x2, "REL", "CX2")

    val result = parseAndExecute( """
start a  = node({A}), b = node({B}), c = node({C})
match a-[rA]->x, b-[rB]->x, c-[rC]->x
return x""", "A" -> 1, "B" -> 2, "C" -> 3)

    assert(List(x1, x2) === result.columnAs[Node]("x").toList)
  }

  @Test def threeBoundNodesPointingToOneWithABunchOfExtraConnections() {
    val a = createNode("a")
    val b = createNode("b")
    val c = createNode("c")
    val d = createNode("d")
    val e = createNode("e")
    val f = createNode("f")
    val g = createNode("g")
    val h = createNode("h")
    val i = createNode("i")
    val j = createNode("j")
    val k = createNode("k")

    relate(a, d)
    relate(a, e)
    relate(a, f)
    relate(a, g)
    relate(a, i)

    relate(b, d)
    relate(b, e)
    relate(b, f)
    relate(b, h)
    relate(b, k)

    relate(c, d)
    relate(c, e)
    relate(c, h)
    relate(c, g)
    relate(c, j)

    val result = parseAndExecute( """
start a  = node({A}), b = node({B}), c = node({C})
match a-->x, b-->x, c-->x
return x""", "A" -> 1, "B" -> 2, "C" -> 3)

    assert(List(d, e) === result.columnAs[Node]("x").toList)
  }

  @Test def shouldHandleCollaborativeFiltering() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")
    val d = createNode("D")
    val e = createNode("E")
    val f = createNode("F")

    relate(a, b, "knows", "rAB")
    relate(a, c, "knows", "rAC")
    relate(a, f, "knows", "rAF")

    relate(b, c, "knows", "rBC")
    relate(b, d, "knows", "rBD")
    relate(b, e, "knows", "rBE")

    relate(c, e, "knows", "rCE")

    val result = parseAndExecute( """
start a  = node(1)
match a-[r1:knows]->friend-[r2:knows]->foaf, a-[foafR?:knows]->foaf
where foafR is null
return foaf, count(*)
order by count(*)""")

    assert(Set(Map("foaf" -> d, "count(*)" -> 1), Map("foaf" -> e, "count(*)" -> 2)) === result.toSet)
  }

  @Test def shouldSplitOptionalMandatoryCleverly() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")

    relate(a, b, "knows", "rAB")
    relate(b, c, "knows", "rBC")

    val result = parseAndExecute( """
start a  = node(1)
match a-[r1?:knows]->friend-[r2:knows]->foaf
return foaf""")

    assert(List(Map("foaf" -> c)) === result.toList)
  }

  @Test(expected = classOf[ParameterNotFoundException]) def shouldComplainWhenMissingParams() {
    val query = Query.
      start(NodeById("pA", ParameterExpression("a"))).
      returns(ReturnItem(Entity("pA"), "pA"))

    execute(query).toList
  }

  @Test def shouldSupportSortAndDistinct() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")

    val result = parseAndExecute( """
start a  = node(1,2,3,1)
return distinct a
order by a.name""")

    assert(List(a, b, c) === result.columnAs[Node]("a").toList)
  }

  @Test def shouldHandleAggregationOnFunctions() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")
    relate(a, b, "X")
    relate(a, c, "X")

    val result = parseAndExecute( """
start a  = node(1)
match p = a -[*]-> b
return b, avg(length(p))""")

    assert(Set(b, c) === result.columnAs[Node]("b").toSet)
  }

  @Test def shouldHandleOptionalPaths() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")
    val r = relate(a, b, "X")

    val result = parseAndExecute( """
start a  = node(1), x = node(2,3)
match p = a -[?]-> x
return x, p""")

    assert(List(
      Map("x" -> b, "p" -> PathImpl(a, r, b)),
      Map("x" -> c, "p" -> null)
    ) === result.toList)
  }

  @Test def shouldHandleOptionalPathsFromGraphAlgo() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")
    val r = relate(a, b, "X")

    val result = parseAndExecute( """
start a  = node(1), x = node(2,3)
match p = shortestPath(a -[?*]-> x)
return x, p""")

    assert(List(
      Map("x" -> b, "p" -> PathImpl(a, r, b)),
      Map("x" -> c, "p" -> null)
    ) === result.toList)
  }

  @Test def shouldHandleOptionalPathsFromACombo() {
    val a = createNode("A")
    val b = createNode("B")
    val r = relate(a, b, "X")

    val result = parseAndExecute( """
start a  = node(1)
match p = a-->b-[?*]->c
return p""")

    assert(List(
      Map("p" -> null)
    ) === result.toList)
  }

  @Test def shouldHandleOptionalPathsFromVarLengthPath() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")
    val r = relate(a, b, "X")

    val result = parseAndExecute( """
start a  = node(1), x = node(2,3)
match p = a -[?*]-> x
return x, p""")

    assert(List(
      Map("x" -> b, "p" -> PathImpl(a, r, b)),
      Map("x" -> c, "p" -> null)
    ) === result.toList)
  }

  @Test def shouldSupportMultipleRegexes() {
    val a = createNode(Map("name" -> "Andreas"))

    val result = parseAndExecute( """
start a  = node(1)
where a.name =~ /And.*/ AND a.name =~ /And.*/
return a""")

    assert(List(a) === result.columnAs[Node]("a").toList)
  }

  @Test def shouldSupportColumnRenaming() {
    val a = createNode(Map("name" -> "Andreas"))

    val result: ExecutionResult = parseAndExecute( """
start a  = node(1)
return a as OneLove""")

    assert(List(a) === result.columnAs[Node]("OneLove").toList)
  }

  @Test def shouldSupportColumnRenamingForAggregatesAsWell() {
    val a = createNode(Map("name" -> "Andreas"))

    val result = parseAndExecute( """
start a  = node(1)
return count(*) as OneLove""")

    assert(List(1) === result.columnAs[Node]("OneLove").toList)
  }

  @Ignore("Should be supported, but doesn't work")
  @Test def shouldBeAbleToQueryNumericIndexes() {
    val a = createNode("x" -> 5)

    inTx(() => {
      val idx = graph.index().forNodes("numericIndex")
      idx.add(a, "number", ValueContext.numeric(13))
    })


    val result = parseAndExecute( """
start a  = node:numericIndex(number = 13)
return a""")

    assert(List(a) === result.columnAs[Node]("a").toList)
  }

  @Test(expected = classOf[SyntaxException]) def shouldNotSupportSortingOnThingsAfterDistinctHasRemovedIt() {
    createNode("name" -> "A", "age" -> 13)
    createNode("name" -> "B", "age" -> 12)
    createNode("name" -> "C", "age" -> 11)

    val result = parseAndExecute( """
start a  = node(1,2,3,1)
return distinct a.name
order by a.age""")

    result.toList
  }

  @Test def shouldSupportOrderingByAPropertyAfterBeingDistinctified() {
    val a = createNode("name" -> "A")
    val b = createNode("name" -> "B")
    val c = createNode("name" -> "C")

    relate(a, b)
    relate(a, c)

    val result = parseAndExecute( """
start a  = node(1)
match a-->b
return distinct b
order by b.name""")

    assert(List(b, c) === result.columnAs[Node]("b").toList)
  }

  @Test def shouldBeAbleToRunCoalesce() {
    createNode("name" -> "A")

    val result = parseAndExecute( """
start a  = node(1)
return coalesce(a.title?, a.name?)""")

    assert(List(Map("coalesce(a.title?, a.name?)" -> "A")) === result.toList)
  }

  @Test def shouldReturnAnInterableWithAllRelationshipsFromAVarLength() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val r1 = relate(a, b)
    val r2 = relate(b, c)

    val result = parseAndExecute( """
start a  = node(1)
match a-[r*2]->c
return r""")

    assert(List(Map("r" -> List(r1, r2))) === result.toList)
  }

  @Test def shouldHandleAllShortestPaths() {
    createDiamond()

    val result = parseAndExecute( """
start a  = node(1), d = node(4)
match p = allShortestPaths( a-[*]->d )
return p""")

    assert(2 === result.toList.size)
  }

  @Test def shouldCollectLeafs() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    val rab = relate(a, b)
    val rac = relate(a, c)
    val rcd = relate(c, d)

    val result = parseAndExecute( """
start root  = node(1)
match p = root-[*]->leaf
where not(leaf-->())
return p, leaf
                                  """)

    assert(List(
      Map("leaf" -> b, "p" -> PathImpl(a, rab, b)),
      Map("leaf" -> d, "p" -> PathImpl(a, rac, c, rcd, d))
    ) === result.toList)
  }

  @Ignore
  @Test def shouldCollectLeafsAndDoOtherMatchingAsWell() {
    val root = createNode()
    val leaf = createNode()
    val stuff1 = createNode()
    val stuff2 = createNode()
    relate(root, leaf)
    relate(leaf, stuff1)
    relate(leaf, stuff2)

    val result = parseAndExecute( """
start root = node(1)
match allLeafPaths( root-->leaf ), leaf <-- stuff
return leaf, stuff
                                  """)

    assert(List(
      Map("leaf" -> leaf, "stuff" -> stuff1),
      Map("leaf" -> leaf, "stuff" -> stuff2)
    ) === result.toList)
  }

  @Test def shouldExcludeConnectedNodes() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    relate(a, b)

    val result = parseAndExecute( """
start a  = node(1), other = node(2,3)
where not(a-->other)
return other
                                  """)

    assert(List(Map("other" -> c)) === result.toList)
  }

  @Test def shouldHandleCheckingThatANodeDoesNotHaveAProp() {
    val result = parseAndExecute("start a=node(0) where not has(a.propertyDoesntExist) return a")
    assert(List(Map("a" -> refNode)) === result.toList)
  }

  @Test def shouldHandleAggregationAndSortingOnSomeOverlappingColumns() {
    createNode("COL1" -> "A", "COL2" -> "A", "num" -> 1)
    createNode("COL1" -> "B", "COL2" -> "B", "num" -> 2)

    val result = parseAndExecute( """
start a  = node(1,2)
return a.COL1, a.COL2, avg(a.num)
order by a.COL1
                                  """)

    assert(List(
      Map("a.COL1" -> "A", "a.COL2" -> "A", "avg(a.num)" -> 1),
      Map("a.COL1" -> "B", "a.COL2" -> "B", "avg(a.num)" -> 2)
    ) === result.toList)
  }

  @Test def shouldThrowNiceErrorMessageWhenPropertyIsMissing() {
    val query = new CypherParser().parse("start n=node(0) return n.A_PROPERTY_THAT_IS_MISSING")

    val exception = intercept[EntityNotFoundException](execute(query).toList)

    assert(exception.getMessage === "The property 'A_PROPERTY_THAT_IS_MISSING' does not exist on Node[0]")
  }

  @Test def shouldAllowAllPredicateOnArrayProperty() {
    val a = createNode("array" -> Array(1, 2, 3, 4))

    val result = parseAndExecute("start a = node(1) where any(x in a.array where x = 2) return a")

    assert(List(Map("a" -> a)) === result.toList)
  }

  @Test def shouldAllowStringComparisonsInArray() {
    val a = createNode("array" -> Array("Cypher duck", "Gremlin orange", "I like the snow"))

    val result = parseAndExecute("start a = node(1) where single(x in a.array where x =~ /.*the.*/) return a")

    assert(List(Map("a" -> a)) === result.toList)
  }

  @Test def shouldBeAbleToCompareWithTrue() {
    val a = createNode("first" -> true)

    val result = parseAndExecute("start a = node(1) where a.first = true return a")

    assert(List(Map("a" -> a)) === result.toList)
  }

  @Test def shouldNotThrowExceptionWhenStuffIsMissing() {
    val a = createNode()
    val b = createNode()
    relate(a, b)
    val result = parseAndExecute( """START n=node(1)
MATCH n-->x0-[?]->x1
WHERE has(x1.type) AND x1.type="http://dbpedia.org/ontology/Film" AND has(x1.label) AND x1.label="Reservoir Dogs"
RETURN x0.name?
                                  """)
    assert(List() === result.toList)
  }

  @Test def shouldBeAbleToHandleMultipleOptionalRelationshipsAndMultipleStartPoints() {
    val a = createNode("A")
    val b = createNode("B")
    val z = createNode("Z")
    val x = createNode("X")
    val y = createNode("Y")

    relate(a, z, "X", "rAZ")
    relate(a, x, "X", "rAX")
    relate(b, x, "X", "rBX")
    relate(b, y, "X", "rBY")

    val result = parseAndExecute( """START a=node(1), b=node(2) match a-[r1?]->x<-[r2?]-b return x""")
    assert(List(x, y, z) === result.columnAs[Node]("x").toList)
  }

  private def createTriangle(number: Int): (Node, Node, Node) = {
    val z = createNode("Z" + number)
    val x = createNode("X" + number)
    val y = createNode("Y" + number)
    relate(z, x, "X", "ZX")
    relate(x, y, "X", "ZY")
    relate(y, z, "X", "YZ")
    (z, x, y)
  }

  @Ignore
  @Test def shouldHandleReallyWeirdOptionalPatterns() {
    val a = createNode("A")
    val b = createNode("B")
    val c = createNode("C")

    val (z1, x1, y1) = createTriangle(1)
    val (z2, x2, y2) = createTriangle(2)
    val (z3, x3, y3) = createTriangle(3)
    val (z4, x4, y4) = createTriangle(4)

    relate(a, x1, "X", "AX")
    relate(b, z1, "X", "AZ")

    relate(a, x2, "X", "AX")
    relate(c, y2, "X", "CY")

    relate(b, z3, "X", "BZ")

    relate(a, x4, "X", "AX")
    relate(b, z4, "X", "BZ")
    relate(c, y4, "X", "CY")

    val result = parseAndExecute( """START a=node(1), b=node(2), c=node(3) match a-[?]-x-->y-[?]-c, b-[?]-z<--y, z-->x return x""")
    assert(List(x1, x2, x3, x4) === result.columnAs[Node]("x").toList)
  }

  @Test def shouldFindNodesBothDirections() {
    val a = createNode()
    relate(a, refNode, "Admin")
    val result = parseAndExecute( """start n = node(0) match (n) -[:Admin]- (b) return id(n), id(b)""")
    assert(List(Map("id(n)" -> 0, "id(b)" -> 1)) === result.toList)

    val result2 = parseAndExecute( """start n = node(1) match (n) -[:Admin]- (b) return id(n), id(b)""")
    assert(List(Map("id(n)" -> 1, "id(b)" -> 0)) === result2.toList)
  }

  @Test def shouldToStringArraysPrettily() {
    createNode("foo" -> Array("one", "two"))

    val result = parseAndExecute( """start n = node(1) return n.foo""")


    val string = result.dumpToString()

    assertThat(string, containsString( """["one","two"]"""))
  }

  @Test def shouldAllowOrderingOnAggregateFunction() {
    createNode()

    val result = parseAndExecute("start n = node(0) match (n)-[:KNOWS]-(c) return n, count(c) as cnt order by cnt")
    assert(List() === result.toList)
  }

  @Test def shouldNotAllowOrderingOnNodes() {
    createNode()

    intercept[SyntaxException](parseAndExecute("start n = node(0,1) return n order by n").toList)
  }

  @Test def shouldIgnoreNodesInParameters() {
    val a = createNode()
    relate(refNode, a, "X")

    val result = parseAndExecute("start c = node(1) match (n)--(c) return n", "self" -> refNode)
    assert(1 === result.size)
  }

  @Test def shouldReturnDifferentResultsWithDifferentParams() {
    val a = createNode()

    val b = createNode()
    relate(a, b)

    relate(refNode, a, "X")

    assert(1 === parseAndExecute("start a = node({a}) match a-->b return b", "a" -> a).size)
    assert(0 === parseAndExecute("start a = node({a}) match a-->b return b", "a" -> b).size)
  }

  @Test def shouldHandleParametersNamedAsIdentifiers() {
    createNode("bar" -> "Andres")

    val result = parseAndExecute("start foo=node(1) where foo.bar = {foo} return foo.bar", "foo" -> "Andres")
    assert(List(Map("foo.bar" -> "Andres")) === result.toList)
  }

  @Test def shouldHandleRelationshipIndexQuery() {
    val a = createNode()
    val b = createNode()
    val r = relate(a, b)
    indexRel(r, "relIdx", "key", "value")


    val result = parseAndExecute("start r=relationship:relIdx(key='value') return r")
    assert(List(Map("r" -> r)) === result.toList)
  }

  @Test def shouldHandleComparisonsWithDifferentTypes() {
    createNode("belt" -> 13)

    val result = parseAndExecute("start n=node(1) where n.belt = 'white' OR n.belt = false return n")
    assert(List() === result.toList)
  }

  @Test def shouldGetAllNodes() {
    val a = createNode()
    val b = createNode()

    val result = parseAndExecute("start n=node(*) return n")
    assert(List(refNode, a, b) === result.columnAs[Node]("n").toList)
  }

  @Test def shouldAllowComparisonsOfNodes() {
    val a = createNode()

    val result = parseAndExecute("start a=node(0,1),b=node(1,0) where a <> b return a,b")
    assert(List(Map("a" -> refNode, "b" -> a), Map("b" -> refNode, "a" -> a)) === result.toList)
  }

  @Test def arithmeticsPrecedenceTest() {
    val result = parseAndExecute("start a = NODE(0) return 12/4*3-2*4")
    assert(List(Map("12/4*3-2*4" -> 1)) === result.toList)
  }

  @Test def arithmeticsPrecedenceWithParenthesisTest() {
    val result = parseAndExecute("start a = NODE(0) return 12/4*(3-2*4)")
    assert(List(Map("12/4*(3-2*4)" -> -15)) === result.toList)
  }

  @Test def shouldAllowAddition() {
    createNode("age" -> 36)

    val result = parseAndExecute("start a=node(1) return a.age + 5 as newAge")
    assert(List(Map("newAge" -> 41)) === result.toList)
  }

  @Test def shouldSolveSelfreferencingPattern() {
    val a = createNode()
    val b = createNode()
    val c = createNode()

    relate(a, b)
    relate(b, c)

    val result = parseAndExecute("start a=node(1) match a-->b, b-->b return b")
    assert(List() === result.toList)
  }

  @Test def shouldSolveSelfreferencingPattern2() {
    val a = createNode()
    val b = createNode()

    val r = relate(a, a)
    relate(a, b)

    val result = parseAndExecute("start a=node(1) match a-[r]->a return r")
    assert(List(Map("r" -> r)) === result.toList)
  }

  @Test def absFunction() {
    val result = parseAndExecute("start a=node(0) return abs(-1)")
    assert(List(Map("abs(-1)" -> 1)) === result.toList)
  }

  @Test def shouldHandleAllOperatorsWithNull() {
    val a = createNode()

    val result = parseAndExecute("start a=node(1) where a.x? =~ /.*?blah.*?/ and a.x? = 13 and a.x? <> 13 and a.x? > 13 return a")
    assert(List(Map("a" -> a)) === result.toList)
  }

  @Test def shouldBeAbleToDoDistinctOnNull() {
    val a = createNode()

    val result = parseAndExecute("start a=node(1) match a-[?]->b return count(distinct b)")
    assert(List(Map("count(distinct b)" -> 0)) === result.toList)
  }

  @Test def exposesIssue198() {
    createNode()

    parseAndExecute("start a=node(*) return a, count(*) order by COUNT(*)").toList
  }

  @Test def shouldAggregateOnArrayValues() {
    createNode("color" -> Array("red"))
    createNode("color" -> Array("blue"))
    createNode("color" -> Array("red"))

    val result = parseAndExecute("start a=node(1,2,3) return distinct a.color, count(*)").toList
    result.foreach(x => {
      val c = x("a.color").asInstanceOf[Array[_]]
      if (c.deep == Array("red").deep)
        assertEquals(2L, x("count(*)"))
      else if (c.deep == Array("blue").deep)
        assertEquals(1L, x("count(*)"))
      else fail("wut?")
    })
  }

  @Test def functions_should_return_null_if_they_get_null_in() {
    createNode()

    val result = parseAndExecute("start a=node(1) match p=a-[r?]->() return length(p), id(r), type(r), nodes(p), rels(p)").toList

    assert(List(Map("length(p)" -> null, "id(r)" -> null, "type(r)" -> null, "nodes(p)" -> null, "rels(p)" -> null)) === result)
  }

  @Test def aggregates_in_aggregates_should_fail() {
    createNode()

    intercept[SyntaxException](parseAndExecute("start a=node(1) return count(count(*))").toList)
  }

  @Test def aggregates_inside_normal_functions_should_work() {
    createNode()

    val result = parseAndExecute("start a=node(1) return length(collect(a))").toList
    assert(List(Map("length(collect(a))" -> 1)) === result)
  }

  @Test def aggregates_should_be_possible_to_use_with_arithmetics() {
    createNode()

    val result = parseAndExecute("start a=node(1) return count(*) * 10").toList
    assert(List(Map("count(*) * 10" -> 10)) === result)
  }

  @Test def aggregates_should_be_possible_to_order_by_arithmetics() {
    createNode()
    createNode()
    createNode()

    val result = parseAndExecute("start a=node(1),b=node(2,3) return count(a) * 10 + count(b) * 5 as X order by X").toList
    assert(List(Map("X" -> 30)) === result)
  }

  @Test def tests_that_filterfunction_works_as_expected() {
    val a = createNode("foo" -> 1)
    val b = createNode("foo" -> 3)
    val r = relate(a, b, "rel", Map("foo" -> 2))

    val result = parseAndExecute("start a=node(1) match p=a-->() return filter(x in p : x.foo = 2)").toList

    val resultingCollection = result.head("filter(x in p : x.foo = 2)").asInstanceOf[Seq[_]].toList

    assert(List(r) == resultingCollection)
  }

  @Test def expose_problem_with_aliasing() {
    createNode("nisse")
    parseAndExecute("start n=node(1) return n.name, count(*) as foo order by n.name")
  }

  @Test def start_with_node_and_relationship() {
    val a = createNode()
    val b = createNode()
    val r = relate(a, b)
    val result = parseAndExecute("start a=node(1), r=relationship(0) return a,r").toList

    assert(List(Map("a" -> a, "r" -> r)) === result)
  }

  @Test def relationship_predicate_with_multiple_rel_types() {
    val a = createNode()
    val b = createNode()
    val x = createNode()

    relate(a, x, "A")
    relate(b, x, "B")

    val result = parseAndExecute("start a=node(1,2) where a-[:A|B]->() return a").toList

    assert(List(Map("a" -> a), Map("a" -> b)) === result)
  }

  @Test def nullable_var_length_path_should_work() {
    createNode()
    val b = createNode()

    val result = parseAndExecute("start a=node(1), b=node(2) match a-[r?*]-b where r is null and a <> b return b").toList

    assert(List(Map("b" -> b)) === result)
  }

  @Test def first_piped_query_woot() {
    val a = createNode("foo" -> 42)
    createNode("foo" -> 49)

    // START x = node(1) WITH x WHERE x.foo = 42 RETURN x
    val secondQ = Query.
      start().
      where(Equals(Property("x", "foo"), Literal(42))).
      returns(ReturnItem(Entity("x"), "x"))

    val q = Query.
      start(NodeById("x", 1, 2)).
      tail(secondQ).
      returns(ReturnItem(Entity("x"), "x"))

    val result = execute(q, "wut?" -> "wOOt!")


    assert(List(Map("x" -> a)) === result.toList)
  }

  @Test def second_piped_query_woot() {
    // START x = node(1) WITH count(*) as apa WHERE apa = 1 RETURN x
    val secondQ = Query.
      start().
      where(Equals(Entity("apa"), Literal(1))).
      returns(ReturnItem(Entity("apa"), "apa"))

    val q = Query.
      start(NodeById("x", 0)).
      tail(secondQ).
      aggregation(CountStar()).
      returns(ReturnItem(CountStar(), "apa"))

    val result = execute(q, "wut?" -> "wOOt!")

    assert(List(Map("apa" -> 1)) === result.toList)
  }

  @Test def listing_rel_types_multiple_times_should_not_give_multiple_returns() {
    val a = createNode()
    val b = createNode()
    relate(a, b, "REL")

    val result = parseAndExecute("start a=node(1) match a-[:REL|REL]-b return b").toList

    assert(List(Map("b" -> b)) === result)
  }

  @Test def should_throw_on_missing_indexes() {
    intercept[MissingIndexException](parseAndExecute("start a=node:missingIndex(key='value') return a").toList)
    intercept[MissingIndexException](parseAndExecute("start a=node:missingIndex('value') return a").toList)
    intercept[MissingIndexException](parseAndExecute("start a=relationship:missingIndex(key='value') return a").toList)
    intercept[MissingIndexException](parseAndExecute("start a=relationship:missingIndex('value') return a").toList)
  }

  @Test def distinct_on_nullable_values() {
    createNode("name" -> "Florescu")
    createNode()
    createNode()

    val result = parseAndExecute("start a=node(1,2,3) return distinct a.name?").toList
    assert(result === List(Map("a.name?" -> "Florescu"), Map("a.name?" -> null)))
  }

  @Test def createEngineWithSpecifiedParserVersion() {
    val db = new ImpermanentGraphDatabase(Map[String, String]("cypher_parser_version" -> "1.6").asJava)
    val engine = new ExecutionEngine(db)

    try {
      // This syntax is valid today, but should give an exception in 1.5
      engine.execute("create a")
    } catch {
      case x: SyntaxException =>
      case _ => fail("expected exception")
    } finally {
      db.shutdown()
    }
  }

  @Test def different_results_on_ordered_aggregation_with_limit() {
    val root = createNode()
    val n1 = createNode("x" -> 1)
    val n2 = createNode("x" -> 2)
    val m1 = createNode()
    val m2 = createNode()

    relate(root, n1, m1)
    relate(root, n2, m2)

    val q = "start a=node(1) match a-->n-->m return n.x, count(*) order by n.x"

    val resultWithoutLimit = parseAndExecute(q)
    val resultWithLimit = parseAndExecute(q + " limit 1000")

    assert(resultWithLimit.toList === resultWithoutLimit.toList)
  }

  @Test def should_be_able_to_figure_out_that_aggregations_in_order_by() {
    val q = "start user=node({0}) return user, avg(user.age) order by avg(user.age) desc, count(*) desc"

    parseAndExecute(q)
  }

  @Test def return_all_identifiers() {
    val a = createNode()
    val b = createNode()
    val r = relate(a, b)

    val q = "start a=node(1) match p=a-->b return *"

    val result = parseAndExecute(q).toList
    val first = result.head
    assert(first.keys === Set("a", "b", "p"))
    assert(first("p").asInstanceOf[Path] == PathImpl(a, r, b))
  }

  @Test def issue_446() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    relate(a, b, "age" -> 24)
    relate(a, c, "age" -> 38)
    relate(a, d, "age" -> 12)

    val q = "start n=node(1) match n-[f]->() with n, max(f.age) as age match n-[f]->m where f.age = age return m"

    assert(parseAndExecute(q).toList === List(Map("m" -> c)))
  }

  @Test def issue_432() {
    val a = createNode()
    val b = createNode()
    relate(a, b)

    val q = "start n=node(1) match p = n-[*1..]->m return p, last(p) order by length(p) asc"

    assert(parseAndExecute(q).size === 1)
  }

  @Test def issue_479() {
    createNode()

    val q = "start n=node(1) match n-[?]->x where x-->() return x"

    assert(parseAndExecute(q).toList === List())
  }

  @Test def issue_479_has_relationship_to_specific_node() {
    createNode()

    val q = "start n=node(1) match n-[?:FRIEND]->x where not n-[:BLOCK]->x return x"

    assert(parseAndExecute(q).toList === List(Map("x" -> null)))
  }

  @Test def issue_508() {
    createNode()

    val q = "start n=node(0) set n.x=[1,2,3] return length(n.x)"

    assert(parseAndExecute(q).toList === List(Map("length(n.x)" -> 3)))
  }

  @Test def length_on_filter() {
    // https://github.com/neo4j/community/issues/526
    val q = "start n=node(*) match n-[r?]->m return length(filter(x in collect(r) : x <> null)) as cn"

    assert(executeScalar[Long](q) === 0)
  }

  @Test def long_or_double() {
    val result = parseAndExecute("start n=node(0) return 1, 1.5").toList.head

    assert(result("1").getClass === classOf[java.lang.Long])
    assert(result("1.5").getClass === classOf[java.lang.Double])
  }

  @Test def square_function_returns_decimals() {
    val result = parseAndExecute("start n=node(0) return sqrt(12.96)").toList

    assert(result === List(Map("sqrt(12.96)"->3.6)))
  }

  @Test def maths_inside_aggregation() {
    val andres = createNode("name"->"Andres")
    val michael = createNode("name"->"Michael")
    val peter = createNode("name"->"Peter")
    val bread = createNode("type"->"Bread")
    val veg = createNode("type"->"Veggies")
    val meat = createNode("type"->"Meat")

    relate(andres, bread, "ATE", Map("times"->10))
    relate(andres, veg, "ATE", Map("times"->8))

    relate(michael, veg, "ATE", Map("times"->4))
    relate(michael, bread, "ATE", Map("times"->6))
    relate(michael, meat, "ATE", Map("times"->9))

    relate(peter, veg, "ATE", Map("times"->7))
    relate(peter, bread, "ATE", Map("times"->7))
    relate(peter, meat, "ATE", Map("times"->4))

    val result = parseAndExecute(
      """    start me=node(1)
    match me-[r1:ATE]->food<-[r2:ATE]-you

    with me,count(distinct r1) as H1,count(distinct r2) as H2,you
    match me-[r1:ATE]->food<-[r2:ATE]-you

    return me,you,sum((1-ABS(r1.times/H1-r2.times/H2))*(r1.times+r2.times)/(H1+H2))""").dumpToString()

    println(result)
  }

  @Test def zero_matching_subgraphs_yield_correct_count_star() {
    val result = parseAndExecute("start n=node(*) where 1 = 0 return count(*)").toList
    assert(List(Map("count(*)" -> 0)) === result)
  }

  @Test def should_return_paths() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    val d = createNode()
    relate(a, b, "X")
    relate(a, c, "X")
    relate(a, d, "X")

    val result = parseAndExecute("start n=node(1) return n-->()").columnAs[List[Path]]("n-->()").toList.flatMap(p => p.map(_.endNode()))

    assert(result === List(b, c, d))
  }

  @Test def should_return_shortest_paths() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    relate(a, b)
    relate(b, c)

    val result = parseAndExecute("start a=node(1),c=node(3) return shortestPath(a-[*]->c)").columnAs[List[Path]]("shortestPath(a-[*]->c)").toList.head.head
    assertEquals(result.endNode(), c)
    assertEquals(result.startNode(), a)
    assertEquals(result.length(), 2)
  }

  @Test
  def in_against_non_existing_collection() {
    val result = parseAndExecute("start a=node(0) where 'z' in a.array_prop? return a")
    assert(result.toList === List(Map("a" -> refNode)))
  }

  @Test
  def array_prop_output() {
    createNode("foo"->Array(1,2,3))
    val result = parseAndExecute("start n=node(1) return n").dumpToString()
    assertThat(result, containsString("[1,2,3]"))
  }

  @Test
  def var_length_expression() {
    val a = createNode()
    val b = createNode()
    val r = relate(a, b)

    val result = parseAndExecute("START a=node(1), b=node(2) match a-->b RETURN a-[*]->b").toList
    assert(result === List(Map("a-[*]->b" -> List(PathImpl(a, r, b)))))
  }

  @Test
  def optional_expression() {
    val a = createNode()
    val b = createNode()
    val r = relate(a,b)

    val result = parseAndExecute("START a=node(1) match a-->b RETURN a-[?]->b").toList
    assert(result === List(Map("a-[?]->b" -> List(PathImpl(a, r, b)))))
  }

  @Test
  def pattern_expression_deep_in_function_call() {
    val a = createNode()
    val b = createNode()
    val c = createNode()
    relate(a,b)
    relate(a,c)

    val result = parseAndExecute("START a=node(1) foreach(n in extract(p in a-->() : last(p)) : set n.touched = true) return a-->()").dumpToString()
    println(result)
  }

  @Test
  def double_optional_with_no_matches() {
    createNode()
    createNode()

    val result = parseAndExecute("START a=node(1),b=node(2) MATCH a-[r1?]->X<-[r2?]-b return X").toList
    assert(result === List(Map("X"->null)))
  }

  @Test
  def two_double_optional_with_one_full_and_two_halfs() {
    val a = createNode()
    val b = createNode()
    val X = createNode()
    val Z1 = createNode()
    val Z2 = createNode()
    val r1 = relate(a, X)
    val r2 = relate(b, X)
    val r3 = relate(Z1, a)
    val r4 = relate(Z2, b)

    val result = parseAndExecute("START a=node(1), b=node(2) MATCH a-[r1?]->X<-[r2?]-b, a<-[r3?]-Z-[r4?]->b return r1,r2,r3,r4").toSet
    assert(result === Set(
      Map("r1" -> r1, "r2" -> r2, "r3" -> r3, "r4" -> null),
      Map("r1" -> r1, "r2" -> r2, "r3" -> null, "r4" -> r4)))
  }

  @Test
  def two_double_optional_with_no_matches() {
    createNode()
    createNode()

    val result = parseAndExecute("START a=node(1), b=node(2) MATCH a-[r1?]->X<-[r2?]-b, a<-[r3?]-Z-[r4?]->b return r1,r2,r3,r4").toSet
    assert(result === Set(Map("r1" -> null, "r2" -> null, "r3" -> null, "r4" -> null)))
  }

  @Test
  def two_double_optional_with_four_halfs() {
    val a = createNode()
    val b = createNode()
    val X1 = createNode()
    val X2 = createNode()
    val Z1 = createNode()
    val Z2 = createNode()
    val r1 = relate(a, X1)
    val r2 = relate(b, X2)
    val r3 = relate(Z1, a)
    val r4 = relate(Z2, b)

    val result = () => parseAndExecute("START a=node(1), b=node(2) MATCH a-[r1?]->X<-[r2?]-b, a<-[r3?]-Z-[r4?]->b return r1,r2,r3,r4 order by id(r1),id(r2),id(r3),id(r4)")

    assertEquals(Set(
      Map("r1" -> r1, "r2" -> null, "r3" -> r3, "r4" -> null),
      Map("r1" -> r1, "r2" -> null, "r3" -> null, "r4" -> r4),
      Map("r1" -> null, "r2" -> r2, "r3" -> r3, "r4" -> null),
      Map("r1" -> null, "r2" -> r2, "r3" -> null, "r4" -> r4)), result().toSet)
    assert(result().toList.size === 4)
  }
}