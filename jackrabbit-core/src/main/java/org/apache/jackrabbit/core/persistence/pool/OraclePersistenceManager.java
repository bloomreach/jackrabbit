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
package org.apache.jackrabbit.core.persistence.pool;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.jackrabbit.core.persistence.PMContext;
import org.apache.jackrabbit.core.util.db.CheckSchemaOperation;
import org.apache.jackrabbit.core.util.db.ConnectionHelper;
import org.apache.jackrabbit.core.util.db.OracleConnectionHelper;

/**
 * Extends the {@link BundleDbPersistenceManager} by Oracle specific code. <p/> Configuration:<br>
 * <ul>
 * <li>&lt;param name="{@link #setExternalBLOBs(String)} externalBLOBs}" value="false"/>
 * <li>&lt;param name="{@link #setBundleCacheSize(String) bundleCacheSize}" value="8"/>
 * <li>&lt;param name="{@link #setConsistencyCheck(String) consistencyCheck}" value="false"/>
 * <li>&lt;param name="{@link #setMinBlobSize(String) minBlobSize}" value="16384"/>
 * <li>&lt;param name="{@link #setDriver(String) driver}" value="oracle.jdbc.OracleDriverr"/>
 * <li>&lt;param name="{@link #setUrl(String) url}" value="jdbc:oracle:thin:@127.0.0.1:1521:xe"/>
 * <li>&lt;param name="{@link #setUser(String) user}" value=""/>
 * <li>&lt;param name="{@link #setPassword(String) password}" value=""/>
 * <li>&lt;param name="{@link #setSchema(String) schema}" value="oracle"/>
 * <li>&lt;param name="{@link #setSchemaObjectPrefix(String) schemaObjectPrefix}" value="${wsp.name}_"/>
 * <li>&lt;param name="{@link #setTablespace(String) tableSpace}" value="user"/>
 * <li>&lt;param name="{@link #setIndexTablespace(String) tableSpace}" value="user"/>
 * <li>&lt;param name="{@link #setErrorHandling(String) errorHandling}" value=""/>
 * </ul>
 */
public class OraclePersistenceManager extends BundleDbPersistenceManager {
    /**
     * The default tablespace clause used when {@link #tablespace} or {@link #indexTablespace}
     * are not specified.
     */
    protected static final String DEFAULT_TABLESPACE_CLAUSE = "";

    /**
     * Name of the replacement variable in the DDL for {@link #tablespace}.
     */
    protected static final String TABLESPACE_VARIABLE = "${tablespace}";

    /**
     * Name of the replacement variable in the DDL for {@link #indexTablespace}.
     */
    protected static final String INDEX_TABLESPACE_VARIABLE = "${indexTablespace}";

    /** The Oracle tablespace to use for tables */
    protected String tablespace;

    /** The Oracle tablespace to use for indexes */
    protected String indexTablespace;

    /**
     * Creates a new oracle persistence manager
     */
    public OraclePersistenceManager() {
        tablespace = DEFAULT_TABLESPACE_CLAUSE;
        indexTablespace = DEFAULT_TABLESPACE_CLAUSE;
        // enable db blob support
        setExternalBLOBs(false);
    }

    /**
     * Returns the configured Oracle tablespace for tables.
     * @return the configured Oracle tablespace for tables.
     */
    public String getTablespace() {
        return tablespace;
    }

    /**
     * Sets the Oracle tablespace for tables.
     * @param tablespaceName the Oracle tablespace for tables.
     */
    public void setTablespace(String tablespaceName) {
        this.tablespace = this.buildTablespaceClause(tablespaceName);
    }
    
    /**
     * Returns the configured Oracle tablespace for indexes.
     * @return the configured Oracle tablespace for indexes.
     */
    public String getIndexTablespace() {
        return indexTablespace;
    }
    
    /**
     * Sets the Oracle tablespace for indexes.
     * @param tablespace the Oracle tablespace for indexes.
     */
    public void setIndexTablespace(String tablespaceName) {
        this.indexTablespace = this.buildTablespaceClause(tablespaceName);
    }
    
    /**
     * Constructs the <code>tablespace &lt;tbs name&gt;</code> clause from
     * the supplied tablespace name. If the name is empty, {@link #DEFAULT_TABLESPACE_CLAUSE}
     * is returned instead.
     * 
     * @param tablespaceName A tablespace name
     * @return A tablespace clause using the supplied name or
     * <code>{@value #DEFAULT_TABLESPACE_CLAUSE}</code> if the name is empty
     */
    private String buildTablespaceClause(String tablespaceName) {
        if (tablespaceName == null || tablespaceName.trim().length() == 0) {
            return DEFAULT_TABLESPACE_CLAUSE;
        } else {
            return "tablespace " + tablespaceName.trim();
        }
    }

    public void init(PMContext context) throws Exception {
        // init default values
        if (getDriver() == null) {
            setDriver("oracle.jdbc.OracleDriver");
        }
        if (getUrl() == null) {
            setUrl("jdbc:oracle:thin:@127.0.0.1:1521:xe");
        }
        if (getDatabaseType() == null) {
            setDatabaseType("oracle");
        }
        if (getSchemaObjectPrefix() == null) {
            setSchemaObjectPrefix(context.getHomeDir().getName() + "_");
        }
        super.init(context);
    }

    /**
     * Returns a new instance of a NGKDbNameIndex.
     * 
     * @return a new instance of a NGKDbNameIndex.
     * @throws SQLException if an SQL error occurs.
     */
    protected DbNameIndex createDbNameIndex() throws SQLException {
        return new NGKDbNameIndex(conHelper, schemaObjectPrefix);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ConnectionHelper createConnectionHelper(DataSource dataSrc) throws Exception {
        OracleConnectionHelper helper = new OracleConnectionHelper(dataSrc, blockOnConnectionLoss);
        helper.init();
        return helper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected CheckSchemaOperation createCheckSchemaOperation() {
        if (DEFAULT_TABLESPACE_CLAUSE.equals(indexTablespace) && !DEFAULT_TABLESPACE_CLAUSE.equals(tablespace)) {
            // tablespace was set but not indexTablespace : use the same for both
            indexTablespace = tablespace;
        }
        return super.createCheckSchemaOperation()
            .addVariableReplacement(TABLESPACE_VARIABLE, tablespace)
            .addVariableReplacement(INDEX_TABLESPACE_VARIABLE, indexTablespace);
    }
}
