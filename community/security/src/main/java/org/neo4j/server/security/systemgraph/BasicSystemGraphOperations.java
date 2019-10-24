/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.server.security.systemgraph;

import java.util.Map;

import org.neo4j.cypher.internal.security.SecureHasher;
import org.neo4j.cypher.internal.security.SystemGraphCredential;
import org.neo4j.kernel.api.exceptions.InvalidArgumentsException;
import org.neo4j.kernel.impl.security.Credential;
import org.neo4j.kernel.impl.security.User;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.BooleanValue;
import org.neo4j.values.storable.TextValue;

import static org.neo4j.internal.helpers.collection.MapUtil.map;

public class BasicSystemGraphOperations
{
    protected final QueryExecutor queryExecutor;
    private final SecureHasher secureHasher;

    public BasicSystemGraphOperations( QueryExecutor queryExecutor, SecureHasher secureHasher )
    {
        this.queryExecutor = queryExecutor;
        this.secureHasher = secureHasher;
    }

    User getUser( String username ) throws InvalidArgumentsException
    {
        User[] user = new User[1];

        String query = "MATCH (u:User {name: $name}) RETURN u.credentials, u.passwordChangeRequired, u.suspended";
        Map<String,Object> params = map( "name", username );

        final ErrorPreservingQuerySubscriber subscriber = new ErrorPreservingQuerySubscriber()
        {
            private AnyValue[] fields;
            private int currentOffset = -1;

            @Override
            public void onResult( int numberOfFields )
            {
                this.fields = new AnyValue[numberOfFields];
            }

            @Override
            public void onRecord()
            {
                currentOffset = 0;
            }

            @Override
            public void onField( AnyValue value )
            {
                fields[currentOffset++] = value;
            }

            @Override
            public void onRecordCompleted() throws Exception
            {
                currentOffset = -1;
                Credential credential = SystemGraphCredential.deserialize( ((TextValue) fields[0]).stringValue(), secureHasher );
                boolean requirePasswordChange = ((BooleanValue) fields[1]).booleanValue();
                boolean suspended = ((BooleanValue) fields[2]).booleanValue();

                if ( suspended )
                {
                    user[0] = new User.Builder( username, credential )
                            .withRequiredPasswordChange( requirePasswordChange )
                            .withFlag( BasicSystemGraphRealm.IS_SUSPENDED )
                            .build();
                }
                else
                {
                    user[0] = new User.Builder( username, credential )
                            .withRequiredPasswordChange( requirePasswordChange )
                            .withoutFlag( BasicSystemGraphRealm.IS_SUSPENDED )
                            .build();
                }
            }
        };

        queryExecutor.executeQuery( query, params, subscriber );

        if ( user[0] == null )
        {
            throw new InvalidArgumentsException( "User '" + username + "' does not exist." );
        }

        return user[0];
    }
}
