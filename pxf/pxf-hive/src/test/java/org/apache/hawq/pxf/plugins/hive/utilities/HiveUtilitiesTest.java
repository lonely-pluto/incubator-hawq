package org.apache.hawq.pxf.plugins.hive.utilities;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import static org.junit.Assert.*;

import org.apache.hadoop.hive.metastore.api.FieldSchema;
import org.junit.Test;
import org.apache.hawq.pxf.api.Metadata;
import org.apache.hawq.pxf.api.UnsupportedTypeException;
import org.apache.hawq.pxf.plugins.hive.utilities.EnumHiveToHawqType;

public class HiveUtilitiesTest {

    FieldSchema hiveColumn;
    Metadata.Item tblDesc;

    static String[][] typesMappings = {
        /* hive type -> hawq type */
        {"tinyint", "int2"},
        {"smallint", "int2"},
        {"int", "int4"},
        {"bigint", "int8"},
        {"boolean", "bool"},
        {"float", "float4"},
        {"double", "float8"},
        {"string", "text"},
        {"binary", "bytea"},
        {"timestamp", "timestamp"},
        {"date", "date"},
    };

    static String[][] typesWithModifiers = {
        {"decimal(19,84)", "numeric", "19,84"},
        {"varchar(13)", "varchar", "13"},
        {"char(40)", "bpchar", "40"},
    };

    static String[][] complexTypes = {
        {"ArraY<string>", "text"},
        {"MaP<stRing, float>", "text"},
        {"Struct<street:string, city:string, state:string, zip:int>", "text"},
        {"UnionType<array<string>, string,int>", "text"}
    };

    @Test
    public void mapHiveTypeUnsupported() throws Exception {

        hiveColumn = new FieldSchema("complex", "someTypeWeDontSupport", null);

        try {
            HiveUtilities.mapHiveType(hiveColumn);
            fail("unsupported type");
        } catch (UnsupportedTypeException e) {
            assertEquals("Unable to map Hive's type: " + hiveColumn.getType() + " to HAWQ's type", e.getMessage());
        }
    }

    @Test
    public void mapHiveTypeSimple() throws Exception {
        /*
         * tinyint -> int2
         * smallint -> int2
         * int -> int4
         * bigint -> int8
         * boolean -> bool
         * float -> float4
         * double -> float8
         * string -> text
         * binary -> bytea
         * timestamp -> timestamp
         * date -> date
         */
        for (String[] line: typesMappings) {
            String hiveType = line[0];
            String hawqTypeName = line[1];
            hiveColumn = new FieldSchema("field" + hiveType, hiveType, null);
            Metadata.Field result = HiveUtilities.mapHiveType(hiveColumn);
            assertEquals("field" + hiveType, result.getName());
            assertEquals(hawqTypeName, result.getType().getTypeName());
            assertNull(result.getModifiers());
        }
    }

    @Test
    public void mapHiveTypeWithModifiers() throws Exception {
        /*
         * decimal -> numeric
         * varchar -> varchar
         * char -> bpchar
         */
        for (String[] line: typesWithModifiers) {
            String hiveType = line[0];
            String expectedType = line[1];
            String modifiersStr = line[2];
            String[] expectedModifiers = modifiersStr.split(",");
            hiveColumn = new FieldSchema("field" + hiveType, hiveType, null);
            Metadata.Field result = HiveUtilities.mapHiveType(hiveColumn);
            assertEquals("field" + hiveType, result.getName());
            assertEquals(expectedType, result.getType().getTypeName());
            assertArrayEquals(expectedModifiers, result.getModifiers());
        }
    }

    @Test
    public void mapHiveTypeWithModifiersNegative() throws Exception {

        String badHiveType = "decimal(2)";
        hiveColumn = new FieldSchema("badNumeric", badHiveType, null);
        try {
            HiveUtilities.mapHiveType(hiveColumn);
            fail("should fail with bad numeric type error");
        } catch (UnsupportedTypeException e) {
            String errorMsg = "HAWQ does not support type " + badHiveType + " (Field badNumeric), " +
                "expected number of modifiers: 2, actual number of modifiers: 1";
            assertEquals(errorMsg, e.getMessage());
        }

        badHiveType = "char(1,2,3)";
        hiveColumn = new FieldSchema("badChar", badHiveType, null);
        try {
            HiveUtilities.mapHiveType(hiveColumn);
            fail("should fail with bad char type error");
        } catch (UnsupportedTypeException e) {
            String errorMsg = "HAWQ does not support type " + badHiveType + " (Field badChar), " +
                    "expected number of modifiers: 1, actual number of modifiers: 3";
            assertEquals(errorMsg, e.getMessage());
        }

        badHiveType = "char(acter)";
        hiveColumn = new FieldSchema("badModifier", badHiveType, null);
        try {
            HiveUtilities.mapHiveType(hiveColumn);
            fail("should fail with bad modifier error");
        } catch (UnsupportedTypeException e) {
            String errorMsg = "HAWQ does not support type " + badHiveType + " (Field badModifier), " +
                "modifiers should be integers";
            assertEquals(errorMsg, e.getMessage());
        }
    }

    @Test
    public void mapHiveTypeInvalidModifiers() throws Exception {
        String badHiveType = "decimal(abc, xyz)";
        hiveColumn = new FieldSchema("numericColumn", badHiveType, null);
        try {
            HiveUtilities.mapHiveType(hiveColumn);
            fail("should fail with bad modifiers error");
        } catch (UnsupportedTypeException e) {
            String errorMsg = "HAWQ does not support type " + badHiveType + " (Field numericColumn), modifiers should be integers";
            assertEquals(errorMsg, e.getMessage());
        }
    }

    @Test
    public void mapHiveTypeComplex() throws Exception {
        /*
         * array<dataType> -> text
         * map<keyDataType, valueDataType> -> text
         * struct<fieldName1:dataType, ..., fieldNameN:dataType> -> text
         * uniontype<...> -> text
         */
        for (String[] line: complexTypes) {
            String hiveType = line[0];
            String expectedType = line[1];
            hiveColumn = new FieldSchema("field" + hiveType, hiveType, null);
            Metadata.Field result = HiveUtilities.mapHiveType(hiveColumn);
            assertEquals("field" + hiveType, result.getName());
            assertEquals(expectedType, result.getType().getTypeName());
            assertNull(result.getModifiers());
        }
    }

    @Test
    public void parseTableQualifiedNameNoDbName() throws Exception {
        String name = "orphan";
        tblDesc = HiveUtilities.extractTableFromName(name);

        assertEquals("default", tblDesc.getPath());
        assertEquals(name, tblDesc.getName());
    }

    @Test
    public void parseTableQualifiedName() throws Exception {
        String name = "not.orphan";
        tblDesc = HiveUtilities.extractTableFromName(name);

        assertEquals("not", tblDesc.getPath());
        assertEquals("orphan", tblDesc.getName());
    }

    @Test
    public void parseTableQualifiedNameTooManyQualifiers() throws Exception {
        String name = "too.many.parents";
        String errorMsg = surroundByQuotes(name) + " is not a valid Hive table name. "
                + "Should be either <table_name> or <db_name.table_name>";

        parseTableQualifiedNameNegative(name, errorMsg, "too many qualifiers");
    }

    @Test
    public void parseTableQualifiedNameEmpty() throws Exception {
        String name = "";
        String errorMsg = "empty string is not a valid Hive table name. "
                + "Should be either <table_name> or <db_name.table_name>";

        parseTableQualifiedNameNegative(name, errorMsg, "empty string");

        name = null;
        parseTableQualifiedNameNegative(name, errorMsg, "null string");

        name = ".";
        errorMsg = surroundByQuotes(name) + " is not a valid Hive table name. "
                + "Should be either <table_name> or <db_name.table_name>";
        parseTableQualifiedNameNegative(name, errorMsg, "empty db and table names");

        name = " . ";
        errorMsg = surroundByQuotes(name) + " is not a valid Hive table name. "
                + "Should be either <table_name> or <db_name.table_name>";
        parseTableQualifiedNameNegative(name, errorMsg, "only white spaces in string");
    }

    private String surroundByQuotes(String str) {
        return "\"" + str + "\"";
    }

    private void parseTableQualifiedNameNegative(String name, String errorMsg, String reason) throws Exception {
        try {
            tblDesc = HiveUtilities.extractTableFromName(name);
            fail("test should fail because of " + reason);
        } catch (IllegalArgumentException e) {
            assertEquals(errorMsg, e.getMessage());
        }
    }
}
