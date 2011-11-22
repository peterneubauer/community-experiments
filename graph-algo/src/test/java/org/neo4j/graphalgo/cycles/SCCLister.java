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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
//neo4j Strongly connected Components search tools for neo4j graphs
//code written on March, 31st 2011 by Jacopo Farina
//email: jacopo.farina@email.it
//feel free to use it as you want
import org.neo4j.graphdb.Transaction;

/**
 * A Strongly Connected Components lister. It list all the sets of nodes which
 * are strongly connected components. A strongly connected component is a set of
 * nodes such that any node of the set can be reached without going out the set
 * itself.
 * */
public class SCCLister
{
    // HashMaps may be not sufficient in case of large graphs (>10^6 nodes)
    // what use instead? SQLite? Redis?
    private Map<Long, Integer> indexes = new HashMap<Long, Integer>(),
            lowlinks = new HashMap<Long, Integer>();

    private int index;
    private ArrayList<Node> s;
    private RelationshipFilter rf;
    private Set<Set<Node>> results = new HashSet<Set<Node>>();

    private Transaction tx;

    /**
     * Returns a Set of Set of nodes. Any of these sets contains a Strongly
     * Connected Component. That is, nodes which can be reached one by another
     * in the same set by following only directed edges (relationships) and
     * never going out of the set itself. It implies the presence of at least
     * one cycle.
     * 
     * @param g the graph to be analyzed
     * @param rf the relationship filter, which indicates whether a relationship
     *            has to be considered in SCC research
     * */
    public Set<Set<Node>> getCycles( GraphDatabaseService g,
            RelationshipFilter rf )
    {
        this.index = 0;
        this.rf = rf;
        this.s = new ArrayList<Node>();
        for ( Node n : g.getAllNodes() )
        {
            if ( !indexes.containsKey( n.getId() ) ) tarjan( n );
        }
        return results;
    }

    private void tarjan( Node v )
    {

        indexes.put( v.getId(), index );

        lowlinks.put( v.getId(), index );
        index++;

        s.add( v );

        for ( Relationship r : v.getRelationships( Direction.INCOMING ) )
        {
            if ( !rf.followThis( r ) ) continue;

            Node vp = r.getOtherNode( v );

            if ( !indexes.containsKey( vp.getId() ) )
            {
                tarjan( vp );

                if ( lowlinks.get( vp.getId() ) < lowlinks.get( v.getId() ) )
                    lowlinks.put( v.getId(), lowlinks.get( vp.getId() ) );

            }
            else
            {
                if ( s.contains( vp ) )
                    if ( indexes.containsKey( vp.getId() ) )
                    {
                        if ( indexes.get( vp.getId() ) < lowlinks.get( v.getId() ) )
                            lowlinks.put( v.getId(), indexes.get( vp.getId() ) );
                    }
            }
        }
        if ( lowlinks.get( v.getId() ) == indexes.get( v.getId() ) )
        {

            Node l;
            // the variable onlyone is used to avoid considering Strongly
            // Connected Components made up by just one node
            boolean onlyone = true;
            Set<Node> temp = new HashSet<Node>();
            temp.add( v );
            while ( !( l = s.remove( s.size() - 1 ) ).equals( v ) )
            {
                // add the node to the set
                temp.add( l );
                // remove the node from maps, to reduce memory usage
                indexes.remove( l );
                lowlinks.remove( l );
                onlyone = false;
            }
            if ( !onlyone ) results.add( temp );

        }
    }

}
