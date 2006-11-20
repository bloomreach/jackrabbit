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
package org.apache.jackrabbit.jcr2spi.state;

import org.apache.jackrabbit.jcr2spi.operation.Operation;

import javax.jcr.nodetype.ConstraintViolationException;
import java.util.Iterator;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Collection;

/**
 * Registers changes made to states and references and consolidates
 * empty changes.
 */
public class ChangeLog {

    private final ItemState target;
    /**
     * Added states
     */
    final Set addedStates = new LinkedHashSet();

    /**
     * Modified states
     */
    final Set modifiedStates = new LinkedHashSet();

    /**
     * Deleted states
     */
    final Set deletedStates = new LinkedHashSet();

    /**
     * Type of operation this changelog is collection state modifications for.
     */
    private Set operations = new LinkedHashSet();

    /**
     *
     * @param target
     */
    ChangeLog(ItemState target) {
        this.target = target;
    }

    //-----------------------------------------------< Inform the ChangeLog >---
    /**
     * Add the given operation to the list of operations to be recorded within
     * the current update cycle of this ChangeLog.
     *
     * @param operation
     */
    public void addOperation(Operation operation) {
        operations.add(operation);
    }

    /**
     * A state has been added
     *
     * @param state state that has been added
     */
    public void added(ItemState state) {
        addedStates.add(state);
    }

    /**
     * A state has been modified. If the state is not a new state
     * (not in the collection of added ones), then add
     * it to the modified states collection.
     *
     * @param state state that has been modified
     */
    public void modified(ItemState state) {
        if (!addedStates.contains(state)) {
            modifiedStates.add(state);
        }
    }

    /**
     * A state has been deleted. If the state is not a new state
     * (not in the collection of added ones), then remove
     * it from the modified states collection and add it to the
     * deleted states collection.
     *
     * @param state state that has been deleted
     */
    public void deleted(ItemState state) {
        if (!addedStates.remove(state)) {
            modifiedStates.remove(state);
            deletedStates.add(state);
        }
    }

    /**
     * Call this method when this change log has been sucessfully persisted.
     * This implementation will call {@link ItemState#persisted(ChangeLog)
     * ItemState.refresh(this)} on the target item of this change log.
     */
    public void persisted() {
        target.persisted(this);
    }

    //----------------------< Retrieve information present in the ChangeLog >---
    /**
     *
     * @return
     */
    public ItemState getTarget() {
        return target;
    }

    /**
     *
     * @return
     */
    public boolean isEmpty() {
        return operations.isEmpty();
    }

    /**
     *
     * @return
     */
    public Iterator getOperations() {
        return operations.iterator();
    }

    /**
     * Return an iterator over all added states.
     *
     * @return iterator over all added states.
     */
    public Iterator addedStates() {
        return addedStates.iterator();
    }

    /**
     * Return an iterator over all modified states.
     *
     * @return iterator over all modified states.
     */
    public Iterator modifiedStates() {
        return modifiedStates.iterator();
    }

    /**
     * Return an iterator over all deleted states.
     *
     * @return iterator over all deleted states.
     */
    public Iterator deletedStates() {
        return deletedStates.iterator();
    }

    /**
     * Removes the subset of this changelog represented by the given
     * <code>ChangeLog</code> from this changelog.
     *
     * @param subChangeLog remove all entries (states, operations) present in
     * the given changelog from this changelog.
     */
    public void removeAll(ChangeLog subChangeLog) {
        addedStates.removeAll(subChangeLog.addedStates);
        modifiedStates.removeAll(subChangeLog.modifiedStates);
        deletedStates.removeAll(subChangeLog.deletedStates);

        operations.removeAll(subChangeLog.operations);
    }

    /**
     * Make sure that this ChangeLog is totally 'self-contained'
     * and independant; items within the scope of this update operation
     * must not have 'external' dependencies;
     * (e.g. moving a node requires that the target node including both
     * old and new parents are saved)
     */
    public void checkIsSelfContained() throws ConstraintViolationException {
        Set affectedStates = new HashSet();
        affectedStates.addAll(modifiedStates);
        affectedStates.addAll(deletedStates);
        affectedStates.addAll(addedStates);

        // check if the affected states listed by the operations are all
        // listed in the modified,deleted or added states collected by this
        // changelog.
        Iterator it = getOperations();
        while (it.hasNext()) {
            Operation op = (Operation) it.next();
            Collection opStates = op.getAffectedItemStates();
            if (!affectedStates.containsAll(opStates)) {
                // need to save the parent as well
                String msg = "ChangeLog is not self contained.";
                throw new ConstraintViolationException(msg);
            }
        }
    }

    /**
     * Populates this <code>ChangeLog</code> with operations that are within the
     * scope of this change set.
     *
     * @param operations an Iterator of <code>Operation</code>s which are the
     *                   candidates to be included in this <code>ChangeLog</code>.
     */
    public void collectOperations(Iterator operations) {
        Set affectedStates = new HashSet();
        affectedStates.addAll(addedStates);
        affectedStates.addAll(deletedStates);
        affectedStates.addAll(modifiedStates);
        while (operations.hasNext()) {
            Operation op = (Operation) operations.next();
            Iterator states = op.getAffectedItemStates().iterator();
            while (states.hasNext()) {
                ItemState state = (ItemState) states.next();
                if (affectedStates.contains(state)) {
                    addOperation(op);
                    break;
                }
            }
        }
    }

    /**
     * Reset this change log, removing all members inside the
     * maps we built.
     */
    public void reset() {
        addedStates.clear();
        modifiedStates.clear();
        deletedStates.clear();
        // also clear all operations
        operations.clear();
    }

    //-------------------------------------------------------------< Object >---
    /**
     * Returns a string representation of this change log for diagnostic
     * purposes.
     *
     * @return a string representation of this change log
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("{");
        buf.append("#addedStates=").append(addedStates.size());
        buf.append(", #modifiedStates=").append(modifiedStates.size());
        buf.append(", #deletedStates=").append(deletedStates.size());
        buf.append("}");
        return buf.toString();
    }
}
