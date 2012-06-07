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
package org.neo4j.server;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.server.database.GraphDatabaseFactory;
import org.neo4j.server.modules.DiscoveryModule;
import org.neo4j.server.modules.ManagementApiModule;
import org.neo4j.server.modules.RESTApiModule;
import org.neo4j.server.modules.ServerModule;
import org.neo4j.server.modules.StatisticModule;
import org.neo4j.server.modules.ThirdPartyJAXRSModule;
import org.neo4j.server.modules.WebAdminModule;
import org.neo4j.server.startup.healthcheck.ConfigFileMustBePresentRule;
import org.neo4j.server.startup.healthcheck.HTTPLoggingPreparednessRule;
import org.neo4j.server.startup.healthcheck.Neo4jPropertiesMustExistRule;
import org.neo4j.server.startup.healthcheck.StartupHealthCheckRule;

public class NeoServerBootstrapper extends Bootstrapper
{
    @Override
    public Iterable<StartupHealthCheckRule> getHealthCheckRules()
    {
        return Arrays.asList( new ConfigFileMustBePresentRule(), new Neo4jPropertiesMustExistRule(),
            new HTTPLoggingPreparednessRule() );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public Iterable<Class<? extends ServerModule>> getServerModules()
    {
        return Arrays.asList( DiscoveryModule.class, RESTApiModule.class, ManagementApiModule.class,
                ThirdPartyJAXRSModule.class, WebAdminModule.class, StatisticModule.class );
    }

    @Override
    protected GraphDatabaseFactory getGraphDatabaseFactory( Configuration configuration )
    {
        return new GraphDatabaseFactory()
        {
            @Override
            public GraphDatabaseAPI createDatabase( String databaseStoreDirectory,
                    Map<String, String> databaseProperties )
            {
                return (GraphDatabaseAPI) new org.neo4j.graphdb.factory.GraphDatabaseFactory().
                    newEmbeddedDatabaseBuilder( databaseStoreDirectory ).
                    setConfig( databaseProperties ).
                    newGraphDatabase();
            }
        };
    }
}
