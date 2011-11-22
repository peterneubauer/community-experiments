/**
 * Copyright (c) 2002-2011 "Neo Technology,"
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
package org.neo4j.graphalgo.cycles;

import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.test.GraphDescription;
import org.neo4j.test.GraphDescription.Graph;
import org.neo4j.test.GraphHolder;
import org.neo4j.test.ImpermanentGraphDatabase;
import org.neo4j.test.JavaTestDocsGenerator;
import org.neo4j.test.TestData;

public class ExampleOfUseTest implements GraphHolder {
    public @Rule
    TestData<JavaTestDocsGenerator> gen = TestData.producedThrough( JavaTestDocsGenerator.PRODUCER );
    public @Rule
    TestData<Map<String, Node>> data = TestData.producedThrough( GraphDescription.createGraphFor( this, true ) );
    protected static ImpermanentGraphDatabase db;

    @BeforeClass
    public static void init()
    {
        db = new ImpermanentGraphDatabase();
    }
    
    @AfterClass
    public static void shutdownDb()
    {
        try
        {
            if ( db != null ) db.shutdown();
        }
        finally
        {
            db = null;
        }
    }

    @Before
    public void setUp()
    {
        db.cleanContent();
        gen.get().setGraph( db );
    }

    @After
    public void doc()
    {
        gen.get().document( "target/docs/dev", "examples" );
    }

    @Override
    public GraphDatabaseService graphdb()
    {
        return db;
    }
	/**
	 * An example of use of SCCLister.
	 * It looks for impossible sub sets of events in an event graph.
	 * A set is wrong if the relationship BEFORE creates time paradoxes
	 */
    @Test
    @Graph({"A BEFORE B",
        "B BEFORE C",
        "B BEFORE D",
        "D BEFORE E",
        "D SIMILAR A",
        "C CONNECTED B",
        "A ANOTHER_TYPE B",
        "D BEFORE M",
        "M BEFORE B"})
	public void test() {
        data.get();
		SCCLister l=new SCCLister();
		//the situation of BEFORE relationships
		/*
		 * A->B
		 * B->C
		 * B->D
		 * D->E
		 * D->M
		 * M->C
		 * */
		
		
		Set<Set<Node>> cycles = l.getCycles(graphdb(), new MyRelationshipFilter());
		for(Set<Node> scc:cycles){
			System.out.println("Found a Strongly Connected Component:");
			for(Node n:scc)
				System.out.println(n.getProperty("name"));
		}
		

	}

}
class MyRelationshipFilter implements RelationshipFilter{

	@Override
	public boolean followThis(Relationship r) {
		//I want to find cycles only on the BEFORE relationship
		if (r.isType(DynamicRelationshipType.withName("BEFORE")))
			return true;
		else
			return false;
	}

}
