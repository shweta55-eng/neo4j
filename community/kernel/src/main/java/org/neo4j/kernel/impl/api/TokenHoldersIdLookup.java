/*
 * Copyright (c) 2002-2020 "Neo4j,"
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
package org.neo4j.kernel.impl.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.neo4j.internal.kernel.api.security.LoginContext;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.token.TokenHolders;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

class TokenHoldersIdLookup implements LoginContext.IdLookup
{
    private final TokenHolders tokens;
    private final GlobalProcedures globalProcedures;

    TokenHoldersIdLookup( TokenHolders tokens, GlobalProcedures globalProcedures )
    {
        this.tokens = tokens;
        this.globalProcedures = globalProcedures;
    }

    @Override
    public int getPropertyKeyId( String name )
    {
        return tokens.propertyKeyTokens().getIdByName( name );
    }

    @Override
    public int getLabelId( String name )
    {
        return tokens.labelTokens().getIdByName( name );
    }

    @Override
    public int getRelTypeId( String name )
    {
        return tokens.relationshipTypeTokens().getIdByName( name );
    }

    @Override
    public int[] getProcedureIds( String procedureGlobbing )
    {
        String escaped = escapeSpecialCharacters( procedureGlobbing );
        String escapedString = escaped.replaceAll( "\\*", ".*" )
                                      .replaceAll( "\\?", ".{1}" );
        return globalProcedures.getIdsOfProceduresMatching( Pattern.compile( escapedString, CASE_INSENSITIVE ) );
    }

    // These are characters that have special meaning in java regex, * and ? are omitted since we have special handling for those
    private final String specialCharacters = "<([{\\^-=$!|]})+.>";
    // To construct a pattern with the special characters they must first be escaped
    // the '.' is a regex matching every character in the string once and replacing it with the escaped form
    @SuppressWarnings( "ReplaceAllDot" )
    private final String escapedSpecialCharacters = specialCharacters.replaceAll( ".", "\\\\$0" );
    private final Pattern specialCharacterPattern = Pattern.compile( "[" + escapedSpecialCharacters + "]" );

    private String escapeSpecialCharacters( String s )
    {
        Matcher m = specialCharacterPattern.matcher( s );
        // escape all special character that were found
        return m.replaceAll( "\\\\$0" );
    }
}
