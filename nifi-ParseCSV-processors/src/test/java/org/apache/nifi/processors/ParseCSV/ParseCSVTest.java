/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.processors.ParseCSV;

import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;


public class ParseCSVTest {

    private TestRunner testRunner;

    @Before
    public void init() {

        testRunner = TestRunners.newTestRunner(ParseCSV.class);
    }

    @Test
    public void testProcessor() {
        try {
            final TestRunner runner = TestRunners.newTestRunner(new ParseCSV());
            runner.setProperty(ParseCSV.DELIMITER, ",");
            runner.setProperty(ParseCSV.WITH_HEADER, "True");
            runner.setProperty(ParseCSV.FORMAT, "DEFAULT");
            //runner.setProperty(ParseCSV.CUSTOM_HEADER, "column4,column5,column6");
            runner.setProperty(ParseCSV.COLUMN_MASK, "RNC");
            //runner.setProperty(ParseCSV.COLUMN_TOKENIZE, "RNC");
            runner.setProperty(ParseCSV.COLUMN_ENCRYPT, "SITE_ID");
            runner.enqueue(Paths.get("/Users/acesir/Desktop/files/scotia/NiFi/DB_data.csv"));
            runner.run();

            //runner.assertAllFlowFilesTransferred(ParseCSV.RELATIONSHIP_SUCCESS);
            //runner.assertAllFlowFilesTransferred(ParseCSV.RELATIONSHIP_TOKENIZED);

            runner.assertTransferCount(ParseCSV.RELATIONSHIP_SUCCESS, 1);
            runner.assertTransferCount(ParseCSV.RELATIONSHIP_TOKENIZED, 1);

            final MockFlowFile out = runner.getFlowFilesForRelationship(ParseCSV.RELATIONSHIP_SUCCESS).get(0);
            //final MockFlowFile out2 = runner.getFlowFilesForRelationship(ParseCSV.RELATIONSHIP_TOKENIZED).get(0);
            out.assertContentEquals(new String(Files.readAllBytes(Paths.get("/Users/acesir/Desktop/files/scotia/NiFi/DB_data.csv"))));
            //out2.assertContentEquals(new String(Files.readAllBytes(Paths.get("/Users/acesir/Desktop/files/scotia/NiFi/DB_data.csv"))));
        }
        catch (Exception ex) {
            System.out.println(ex);
        }
    }

}
