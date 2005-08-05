/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core.state;

import org.apache.jackrabbit.Constants;
import org.apache.jackrabbit.core.ItemId;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.PropertyId;
import org.apache.jackrabbit.core.util.Dumpable;
import org.apache.jackrabbit.core.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.core.nodetype.NodeDefId;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.nodetype.PropDef;
import org.apache.jackrabbit.core.observation.EventStateCollection;
import org.apache.jackrabbit.core.observation.ObservationManagerImpl;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.core.virtual.VirtualItemStateProvider;
import org.apache.jackrabbit.name.QName;
import org.apache.log4j.Logger;

import javax.jcr.PropertyType;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.ArrayList;
import java.util.Iterator;
import java.io.PrintStream;

import EDU.oswego.cs.dl.util.concurrent.ReadWriteLock;
import EDU.oswego.cs.dl.util.concurrent.ReentrantWriterPreferenceReadWriteLock;

/**
 * Shared <code>ItemStateManager</code>. Caches objects returned from a
 * <code>PersistenceManager</code>. Objects returned by this item state
 * manager are shared among all sessions.
 */
public class SharedItemStateManager
        implements ItemStateManager, ItemStateListener, Dumpable {

    /**
     * Logger instance
     */
    private static Logger log = Logger.getLogger(SharedItemStateManager.class);

    /**
     * cache of weak references to ItemState objects issued by this
     * ItemStateManager
     */
    private final ItemStateReferenceCache cache;

    /**
     * Persistence Manager used for loading and storing items
     */
    private final PersistenceManager persistMgr;

    /**
     * Keep a hard reference to the root node state
     */
    private NodeState root;

    /**
     * Virtual item state providers
     */
    private VirtualItemStateProvider[] virtualProviders = new
            VirtualItemStateProvider[0];

    /**
     * Read-/Write-Lock to synchronize access on this item state manager.
     */
    private final ReadWriteLock rwLock =
            new ReentrantWriterPreferenceReadWriteLock();

    /**
     * Creates a new <code>SharedItemStateManager</code> instance.
     *
     * @param persistMgr
     * @param rootNodeUUID
     * @param ntReg
     */
    public SharedItemStateManager(PersistenceManager persistMgr,
                                  String rootNodeUUID,
                                  NodeTypeRegistry ntReg)
            throws ItemStateException {
        cache = new ItemStateReferenceCache();
        this.persistMgr = persistMgr;

        try {
            root = (NodeState) getNonVirtualItemState(new NodeId(rootNodeUUID));
        } catch (NoSuchItemStateException e) {
            // create root node
            root = createRootNodeState(rootNodeUUID, ntReg);
        }
    }

    //-----------------------------------------------------< ItemStateManager >
    /**
     * {@inheritDoc}
     */
    public ItemState getItemState(ItemId id)
            throws NoSuchItemStateException, ItemStateException {

        acquireReadLock();

        try {
            // check the virtual root ids (needed for overlay)
            for (int i = 0; i < virtualProviders.length; i++) {
                if (virtualProviders[i].isVirtualRoot(id)) {
                    return virtualProviders[i].getItemState(id);
                }
            }
            // check internal first
            if (hasNonVirtualItemState(id)) {
                return getNonVirtualItemState(id);
            }
            // check if there is a virtual state for the specified item
            for (int i = 0; i < virtualProviders.length; i++) {
                if (virtualProviders[i].hasItemState(id)) {
                    return virtualProviders[i].getItemState(id);
                }
            }
        } finally {
            rwLock.readLock().release();
        }
        throw new NoSuchItemStateException(id.toString());
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasItemState(ItemId id) {

        try {
            acquireReadLock();
        } catch (ItemStateException e) {
            return false;
        }

        try {
            if (cache.isCached(id)) {
                return true;
            }

            // check the virtual root ids (needed for overlay)
            for (int i = 0; i < virtualProviders.length; i++) {
                if (virtualProviders[i].isVirtualRoot(id)) {
                    return true;
                }
            }
            // check if this manager has the item state
            if (hasNonVirtualItemState(id)) {
                return true;
            }
            // otherwise check virtual ones
            for (int i = 0; i < virtualProviders.length; i++) {
                if (virtualProviders[i].hasItemState(id)) {
                    return true;
                }
            }
        } finally {
            rwLock.readLock().release();
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public NodeReferences getNodeReferences(NodeReferencesId id)
            throws NoSuchItemStateException, ItemStateException {

        acquireReadLock();

        try {
            // check persistence manager
            try {
                return persistMgr.load(id);
            } catch (NoSuchItemStateException e) {
                // ignore
            }
            // check virtual providers
            for (int i = 0; i < virtualProviders.length; i++) {
                try {
                    return virtualProviders[i].getNodeReferences(id);
                } catch (NoSuchItemStateException e) {
                    // ignore
                }
            }
        } finally {
            rwLock.readLock().release();
        }

        // throw
        throw new NoSuchItemStateException(id.toString());
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasNodeReferences(NodeReferencesId id) {

        try {
            acquireReadLock();
        } catch (ItemStateException e) {
            return false;
        }

        try {
            // check persistence manager
            try {
                if (persistMgr.exists(id)) {
                    return true;
                }
            } catch (ItemStateException e) {
                // ignore
            }
            // check virtual providers
            for (int i = 0; i < virtualProviders.length; i++) {
                if (virtualProviders[i].hasNodeReferences(id)) {
                    return true;
                }
            }
        } finally {
            rwLock.readLock().release();
        }
        return false;
    }

    //----------------------------------------------------< ItemStateListener >
    /**
     * {@inheritDoc}
     */
    public void stateCreated(ItemState created) {
        cache.cache(created);
    }

    /**
     * {@inheritDoc}
     */
    public void stateModified(ItemState modified) {
        // not interested
    }

    /**
     * {@inheritDoc}
     */
    public void stateDestroyed(ItemState destroyed) {
        destroyed.removeListener(this);
        cache.evict(destroyed.getId());
    }

    /**
     * {@inheritDoc}
     */
    public void stateDiscarded(ItemState discarded) {
        discarded.removeListener(this);
        cache.evict(discarded.getId());
    }

    //-------------------------------------------------------------< Dumpable >
    /**
     * {@inheritDoc}
     */
    public void dump(PrintStream ps) {
        ps.println("SharedItemStateManager (" + this + ")");
        ps.println();
        ps.print("[referenceCache] ");
        cache.dump(ps);
    }

    //-------------------------------------------------< misc. public methods >
    /**
     * Disposes this <code>SharedItemStateManager</code> and frees resources.
     */
    public void dispose() {
        // clear cache
        cache.evictAll();
    }

    /**
     * Adds a new virtual item state provider.<p/>
     * NOTE: This method is not synchronized, because it is called right after
     * creation only by the same thread and therefore concurrency issues
     * do not occur. Should this ever change, the synchronization status
     * has to be re-examined.
     * @param prov
     */
    public void addVirtualItemStateProvider(VirtualItemStateProvider prov) {
        VirtualItemStateProvider[] provs =
                new VirtualItemStateProvider[virtualProviders.length + 1];
        System.arraycopy(virtualProviders, 0, provs, 0, virtualProviders.length);
        provs[virtualProviders.length] = prov;
        virtualProviders = provs;
    }

    /**
     * Store modifications registered in a <code>ChangeLog</code>. The items
     * contained in the <tt>ChangeLog</tt> are not states returned by this
     * item state manager but rather must be reconnected to items provided
     * by this state manager.<p/>
     * After successfully storing the states the observation manager is informed
     * about the changes, if an observation manager is passed to this method.<p/>
     * NOTE: This method is not synchronized, because all methods it invokes
     * on instance members (such as {@link PersistenceManager#store} are
     * considered to be thread-safe. Should this ever change, the
     * synchronization status has to be re-examined.
     *
     * @param local  change log containing local items
     * @param obsMgr the observation manager to inform, or <code>null</code> if
     *               no observation manager should be informed.
     * @throws ItemStateException if an error occurs
     */
    public void store(ChangeLog local, ObservationManagerImpl obsMgr)
            throws ItemStateException {

        ChangeLog shared = new ChangeLog();

        // set of virtual node references
        // todo: remember by provider
        ArrayList virtualRefs = new ArrayList();

        acquireWriteLock();

        try {
            /**
             * Validate modified references. Target node of references may
             * have been deleted in the meantime.
             */
            Iterator iter = local.modifiedRefs();
            while (iter.hasNext()) {
                NodeReferences refs = (NodeReferences) iter.next();
                NodeId id = new NodeId(refs.getUUID());
                // if targetid is in virtual provider, transfer to its modified set
                for (int i = 0; i < virtualProviders.length; i++) {
                    VirtualItemStateProvider provider = virtualProviders[i];
                    if (provider.hasItemState(id)) {
                        virtualRefs.add(refs);
                        refs = null;
                        break;
                    }
                }
                if (refs != null) {
                    if (refs.hasReferences()) {
                        if (!local.has(id) && !hasItemState(id)) {
                            String msg = "Target node " + id
                                    + " of REFERENCE property does not exist";
                            throw new ItemStateException(msg);
                        }
                    }
                    shared.modified(refs);
                }
            }

            EventStateCollection events = null;
            boolean succeeded = false;

            try {
                /**
                 * Reconnect all items contained in the change log to their
                 * respective shared item and add the shared items to a
                 * new change log.
                 */
                iter = local.modifiedStates();
                while (iter.hasNext()) {
                    ItemState state = (ItemState) iter.next();
                    state.connect(getItemState(state.getId()));
                    shared.modified(state.getOverlayedState());
                }
                iter = local.deletedStates();
                while (iter.hasNext()) {
                    ItemState state = (ItemState) iter.next();
                    state.connect(getItemState(state.getId()));
                    shared.deleted(state.getOverlayedState());
                }
                iter = local.addedStates();
                while (iter.hasNext()) {
                    ItemState state = (ItemState) iter.next();
                    state.connect(createInstance(state));
                    shared.added(state.getOverlayedState());
                }

                /* prepare the events */
                if (obsMgr != null) {
                    events = obsMgr.createEventStateCollection();
                    events.createEventStates(root.getUUID(), local, this);
                    events.prepare();
                }

                /* Push all changes from the local items to the shared items */
                local.push();

                /* Store items in the underlying persistence manager */
                long t0 = System.currentTimeMillis();
                persistMgr.store(shared);
                succeeded = true;
                long t1 = System.currentTimeMillis();
                if (log.isInfoEnabled()) {
                    log.info("persisting change log " + shared + " took " + (t1 - t0) + "ms");
                }

            } finally {

                /**
                 * If some store operation was unsuccessful, we have to reload
                 * the state of modified and deleted items from persistent
                 * storage.
                 */
                if (!succeeded) {
                    local.disconnect();

                    iter = shared.modifiedStates();
                    while (iter.hasNext()) {
                        ItemState state = (ItemState) iter.next();
                        try {
                            state.copy(loadItemState(state.getId()));
                        } catch (ItemStateException e) {
                            state.discard();
                        }
                    }
                    iter = shared.deletedStates();
                    while (iter.hasNext()) {
                        ItemState state = (ItemState) iter.next();
                        try {
                            state.copy(loadItemState(state.getId()));
                        } catch (ItemStateException e) {
                            state.discard();
                        }
                    }
                    iter = shared.addedStates();
                    while (iter.hasNext()) {
                        ItemState state = (ItemState) iter.next();
                        state.discard();
                    }
                }
            }

            /* Let the shared item listeners know about the change */
            shared.persisted();

            /* notify virtual providers about node references */
            iter = virtualRefs.iterator();
            while (iter.hasNext()) {
                NodeReferences refs = (NodeReferences) iter.next();
                // if targetid is in virtual provider, transfer to its modified set
                for (int i = 0; i < virtualProviders.length; i++) {
                    if (virtualProviders[i].setNodeReferences(refs)) {
                        break;
                    }
                }
            }

            /* dispatch the events */
            if (events != null) {
                events.dispatch();
            }
        } finally {
            rwLock.writeLock().release();
        }
    }

    //-------------------------------------------------------< implementation >
    /**
     * Create a new node state instance
     *
     * @param uuid         uuid
     * @param nodeTypeName node type name
     * @param parentUUID   parent UUID
     * @return new node state instance
     */
    private NodeState createInstance(String uuid, QName nodeTypeName,
                                     String parentUUID) {

        NodeState state = persistMgr.createNew(new NodeId(uuid));
        state.setNodeTypeName(nodeTypeName);
        state.setParentUUID(parentUUID);
        state.setStatus(ItemState.STATUS_NEW);
        state.addListener(this);

        return state;
    }

    /**
     * Create root node state
     * @param rootNodeUUID root node UUID
     * @param ntReg node type registry
     * @return root node state
     * @throws ItemStateException if an error occurs
     */
    private NodeState createRootNodeState(String rootNodeUUID,
                                          NodeTypeRegistry ntReg)
            throws ItemStateException {

        NodeState rootState = createInstance(rootNodeUUID, Constants.REP_ROOT, null);

        // FIXME need to manually setup root node by creating mandatory jcr:primaryType property
        // @todo delegate setup of root node to NodeTypeInstanceHandler

        // id of the root node's definition
        NodeDefId nodeDefId;
        // definition of jcr:primaryType property
        PropDef propDef;
        try {
            nodeDefId = ntReg.getRootNodeDef().getId();
            EffectiveNodeType ent = ntReg.getEffectiveNodeType(Constants.REP_ROOT);
            propDef = ent.getApplicablePropertyDef(Constants.JCR_PRIMARYTYPE,
                    PropertyType.NAME, false);
        } catch (NoSuchNodeTypeException nsnte) {
            String msg = "internal error: failed to create root node";
            log.error(msg, nsnte);
            throw new ItemStateException(msg, nsnte);
        } catch (ConstraintViolationException cve) {
            String msg = "internal error: failed to create root node";
            log.error(msg, cve);
            throw new ItemStateException(msg, cve);
        }
        rootState.setDefinitionId(nodeDefId);

        // create jcr:primaryType property
        rootState.addPropertyName(propDef.getName());

        PropertyState prop = createInstance(propDef.getName(), rootNodeUUID);
        prop.setValues(new InternalValue[]{InternalValue.create(Constants.REP_ROOT)});
        prop.setType(propDef.getRequiredType());
        prop.setMultiValued(propDef.isMultiple());
        prop.setDefinitionId(propDef.getId());

        ChangeLog changeLog = new ChangeLog();
        changeLog.added(rootState);
        changeLog.added(prop);

        persistMgr.store(changeLog);
        changeLog.persisted();

        return rootState;
    }

    /**
     * Returns the item state for the given id without considering virtual
     * item state providers.
     */
    private ItemState getNonVirtualItemState(ItemId id)
            throws NoSuchItemStateException, ItemStateException {

        // check cache; synchronized to ensure an entry is not created twice.
        synchronized (cache) {
            ItemState state = cache.retrieve(id);
            if (state == null) {
                // not found in cache, load from persistent storage
                state = loadItemState(id);
                state.setStatus(ItemState.STATUS_EXISTING);
                // put it in cache
                cache.cache(state);
                // register as listener
                state.addListener(this);
            }
            return state;
        }
    }

    /**
     * Checks if this item state manager has the given item state without
     * considering the virtual item state managers.
     */
    private boolean hasNonVirtualItemState(ItemId id) {
        if (cache.isCached(id)) {
            return true;
        }

        try {
            if (id.denotesNode()) {
                return persistMgr.exists((NodeId) id);
            } else {
                return persistMgr.exists((PropertyId) id);
            }
        } catch (ItemStateException ise) {
            return false;
        }
    }

    /**
     * Create a new node state instance
     *
     * @param other other state associated with new instance
     * @return new node state instance
     */
    private ItemState createInstance(ItemState other) {
        if (other.isNode()) {
            NodeState ns = (NodeState) other;
            return createInstance(ns.getUUID(), ns.getNodeTypeName(), ns.getParentUUID());
        } else {
            PropertyState ps = (PropertyState) other;
            return createInstance(ps.getName(), ps.getParentUUID());
        }
    }

    /**
     * Create a new property state instance
     *
     * @param propName   property name
     * @param parentUUID parent UUID
     * @return new property state instance
     */
    private PropertyState createInstance(QName propName, String parentUUID) {
        PropertyState state = persistMgr.createNew(new PropertyId(parentUUID, propName));
        state.setStatus(ItemState.STATUS_NEW);
        state.addListener(this);

        return state;
    }

    /**
     * Load item state from persistent storage.
     * @param id item id
     * @return item state
     */
    private ItemState loadItemState(ItemId id)
            throws NoSuchItemStateException, ItemStateException {

        if (id.denotesNode()) {
            return persistMgr.load((NodeId) id);
        } else {
            return persistMgr.load((PropertyId) id);
        }
    }

    /**
     * Check targets of modified node references exist.
     * @param log change log
     * @throws ItemStateException if some target was not found
     */
    void checkTargetsExist(ChangeLog log) throws ItemStateException {
        Iterator iter = log.modifiedRefs();
        while (iter.hasNext()) {
            NodeReferences refs = (NodeReferences) iter.next();
            NodeId id = new NodeId(refs.getUUID());

            for (int i = 0; i < virtualProviders.length; i++) {
                VirtualItemStateProvider provider = virtualProviders[i];
                if (provider.hasItemState(id)) {
                    refs = null;
                    break;
                }
            }
            if (refs != null && refs.hasReferences()) {
                if (!log.has(id) && !hasItemState(id)) {
                    String msg = "Target node " + id
                            + " of REFERENCE property does not exist";
                    throw new ItemStateException(msg);
                }
            }
        }
    }

    /**
     * Acquires the read lock on this item state manager.
     * @throws ItemStateException if the read lock cannot be acquired.
     */
    private void acquireReadLock() throws ItemStateException {
        try {
            rwLock.readLock().acquire();
        } catch (InterruptedException e) {
            throw new ItemStateException("Interrupted while acquiring read lock");
        }
    }

    /**
     * Acquires the write lock on this item state manager.
     * @throws ItemStateException if the write lock cannot be acquired.
     */
    private void acquireWriteLock() throws ItemStateException {
        try {
            rwLock.writeLock().acquire();
        } catch (InterruptedException e) {
            throw new ItemStateException("Interrupted while acquiring write lock");
        }
    }
}
