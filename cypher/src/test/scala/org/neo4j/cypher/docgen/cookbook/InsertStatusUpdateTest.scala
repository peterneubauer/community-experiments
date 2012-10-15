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
package org.neo4j.cypher.docgen.cookbook

import org.junit.Test
import org.junit.Assert._
import org.neo4j.cypher.docgen.DocumentingTestBase

class InsertStatusUpdateTest extends DocumentingTestBase {
  def graphDescription = List()

  def section = "cookbook"
  override val noTitle = true;

  @Test def updateStatus() {
      executeQuery("""
create 
(bob{name:'Bob'})-[:STATUS]->(bob_s1{name:'bob_s1', text:'bobs status1',date:1})-[:NEXT]->(bob_s2{name:'bob_s2', text:'bobs status2',date:4})
          """);
    testQuery(
      title = "Insert a new status update for a user",
      text =
"""
Here, the example shows how to add a new status update into the existing data for a user.""",
      queryText = """START me=node:node_auto_index(name='Bob') MATCH me-[r?:STATUS]-secondlatestupdate DELETE r 
WITH me, secondlatestupdate 
CREATE me-[:STATUS]->(latest_update{text:'Status',date:123}) 
WITH latest_update,secondlatestupdate 
CREATE latest_update-[:NEXT]-secondlatestupdate 
WHERE secondlatestupdate <> null 
RETURN latest_update.text as new_status""",
      returns =
"""
Dividing the query into steps (with relavant query), this query resembles adding new item in middle of doubly linked list:

. Get the latest update(if exists) of the user through Status relationship (`MATCH user-[r?:STATUS]-secondlatestupdate`)
. Delete the Status relationship between user and statusupdate(if exists), as this would become the second latest update now and only the latest update would be added through Status relationship, all earlier updates would be connection to their subsequent updates through Next relationship. (`DELETE r`)
. Now, create the new Statusupdate node (with text and date as properties) and connection this with Person through STATUS relationship (`CREATE user-[:STATUS]-(lateststatusupdate{text:{0},date:{1}}`)
. Now, create Next relationship between latest status update and the second latest status update(if exists)  (`CREATE  lateststatusupdate-[:NEXT]-secondlatestupdate WHERE secondlatestupdate <> null`)""",
      assertions = (p) => assertEquals(List(Map("new_status" -> "Status")), p.toList))
  } 
}
