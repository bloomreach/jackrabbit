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
package org.apache.jackrabbit.core.journal;

import org.apache.jackrabbit.core.util.db.ConnectionHelper;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.NoSuchElementException;

/**
 * RecordIterator interface.
 */
class DatabaseRecordIterator implements RecordIterator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseRecordIterator.class);
    private static final int BATCH_SIZE = 1024;

    private final ConnectionHelper conHelper;
    private ResultSet rs;
    private final String selectRecordsStmt;
    private final long startRevision;
    private final NamespaceResolver resolver;
    private final NamePathResolver npResolver;
    private ReadRecord record;
    private ReadRecord lastRecord;
    private boolean isEOF;

    public DatabaseRecordIterator(final ConnectionHelper conHelper, final String selectRevisionsStmtSQL, final long startRevision, final NamespaceResolver resolver, final NamePathResolver npResolver) {
        this.selectRecordsStmt = selectRevisionsStmtSQL;
        this.startRevision = startRevision;
        this.conHelper = conHelper;
        this.resolver = resolver;
        this.npResolver = npResolver;
    }

    public boolean hasNext() {
        try {
            if (record == null) {
                fetchRecord();
            }
            return !isEOF;
        } catch (SQLException e) {
            String msg = "Error while moving to next record.";
            log.error(msg, e);
            return false;
        }
    }

    /**
     * Return the next record. If there are no more records, throws
     * a <code>NoSuchElementException</code>. If an error occurs,
     * throws a <code>JournalException</code>.
     *
     * @return next record
     * @throws java.util.NoSuchElementException if there are no more records
     * @throws JournalException if another error occurs
     */
    public Record nextRecord() throws NoSuchElementException, JournalException {
        if (!hasNext()) {
            String msg = "No current record.";
            throw new NoSuchElementException(msg);
        }
        close(lastRecord);
        lastRecord = record;
        record = null;

        return lastRecord;
    }

    public void close() {
        if (lastRecord != null) {
            close(lastRecord);
            lastRecord = null;
        }
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            String msg = "Error while closing result set: " + e.getMessage();
            log.warn(msg);
        }
    }

    /**
     * Fetch the next record.
     */
    private void fetchRecord() throws SQLException {
        if (rs != null && rs.next()) {
            readRecord();
        } else {
            try {
                if (rs != null) {
                    rs.close();
                }
                final long fromRevision = lastRecord != null ? lastRecord.getRevision() : startRevision;
                rs = conHelper.exec(selectRecordsStmt, new Object[]{ fromRevision }, false, BATCH_SIZE);
                if (rs.next()) {
                    readRecord();
                } else {
                    isEOF = true;
                }
            } catch (SQLException e) {
                log.error("Failed to retrieve journal records", e);
            }
        }
    }

    private void readRecord() throws SQLException {
        long revision = rs.getLong(1);
        String journalId = rs.getString(2);
        String producerId = rs.getString(3);
        DataInputStream dataIn = new DataInputStream(rs.getBinaryStream(4));
        record = new ReadRecord(journalId, producerId, revision, dataIn, 0, resolver, npResolver);
    }

    /**
     * Close a record.
     *
     * @param record record
     */
    private static void close(ReadRecord record) {
        if (record != null) {
            try {
                record.close();
            } catch (IOException e) {
                String msg = "Error while closing record.";
                log.warn(msg, e);
            }
        }
    }

}
