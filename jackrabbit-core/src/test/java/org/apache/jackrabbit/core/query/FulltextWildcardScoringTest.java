/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.query;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryResult;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;

import org.apache.jackrabbit.commons.iterator.RowIterable;

/**
 * Performs wildcard scoring tests with the <code>CONTAINS</code> function.
 */
public class FulltextWildcardScoringTest extends AbstractQueryTest {

    public void test_scoring_Wildcard() throws RepositoryException {
        String content1 = "The quick brown Fox jumps over the lazy dog.";
        String content2 = "The quick brown Fox jumpy jumps over the lazy dog.";
        // single * wildcard
        while (testRootNode.hasNode(nodeName1)) {
            testRootNode.getNode(nodeName1).remove();
        }
        while (testRootNode.hasNode(nodeName2)) {
            testRootNode.getNode(nodeName1).remove();
        }

        testRootNode.addNode(nodeName1).setProperty("text", content1);
        testRootNode.addNode(nodeName2).setProperty("text", content2);

        testRootNode.getSession().save();

        StringBuffer stmt = new StringBuffer();
        stmt.append("/jcr:root").append(testRoot).append("/*");
        stmt.append("[jcr:contains(., 'jum*')] order by @jcr:score descending");

        Query q = superuser.getWorkspace().getQueryManager().createQuery(stmt.toString(), Query.XPATH);
        RowIterator rows = q.execute().getRows();
        Map<String, Double> scores = new HashMap<String, Double>();

        while (rows.hasNext()) {
            Row row = rows.nextRow();
            scores.put(row.getNode().getName(), row.getScore());
        }

        assertTrue("content2 contains two matches for 'jum*' so should had scored higher", scores.get(nodeName2) > scores.get(nodeName1));
    }

}
