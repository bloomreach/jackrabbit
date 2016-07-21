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
package org.apache.jackrabbit.spi.commons.name;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.Path;

public class CargoNamePath extends RelativePath {

    /** Serial version UID */
    private static final long serialVersionUID = -2887665244213430950L;

    /**
     * Name of the last path element.
     */
    private final Name name;

    /**
     * Optional index of the last path element. Set to
     * {@link Path#INDEX_UNDEFINED} if not explicitly specified,
     * otherwise contains the 1-based index.
     */
    private final int index;

    private final String argument;

    public CargoNamePath(Path parent, Name name, String argument) {
        super(parent);
        assert name != null;
        this.name = name;
        this.index = 0;
        this.argument = argument;
    }

    protected int getDepthModifier() {
        return 1;
    }

    protected Path getParent() throws RepositoryException {
        if (parent != null) {
            return parent;
        } else {
            return new CurrentPath(null);
        }
    }

    protected String getElementString() {
        if (index > Path.INDEX_DEFAULT) {
            return name + "[" + index + "]";
        } else {
            return name.toString();
        }
    }

    public Name getName() {
        return name;
    }

    @Override
    public int getIndex() {
        return Path.INDEX_DEFAULT;
    }

    @Override
    public String getIdentifier() {
        return argument;
        }

    @Override
    public boolean denotesIdentifier() {
        return true;
    }

    @Override
    public String getString() {
        return name + "[[" + argument + "]]";
    }

    @Override
    public int getNormalizedIndex() {
        return Path.INDEX_DEFAULT;
    }

    @Override
    public boolean denotesName() {
        return true;
    }

    public boolean isCanonical() {
        return parent != null && parent.isCanonical();
    }

    public boolean isNormalized() {
        return parent == null
            || (parent.isNormalized()
                    && !parent.denotesCurrent());
    }

    public Path getNormalizedPath() throws RepositoryException {
        if (isNormalized()) {
            return this;
        } else {
            // parent is guaranteed to be !null
            Path normalized = parent.getNormalizedPath();
            if (normalized.denotesCurrent()) {
                normalized = null; // special case: ./a
            }
            return new CargoNamePath(normalized, name, argument);
        }
    }

    public Path getCanonicalPath() throws RepositoryException {
        if (isCanonical()) {
            return this;
        } else if (parent != null) {
            return new CargoNamePath(parent.getCanonicalPath(), name, argument);
        } else {
            throw new RepositoryException(
                    "There is no canonical representation of " + this);
        }
    }

    /**
     * Returns the last element of this path.
     *
     * @return last element of this path
     */
    @Override
    public AbstractPath getLastElement() {
        if (parent != null) {
            return new CargoNamePath(null, name, argument);
        } else {
            return this;
        }
    }

    //--------------------------------------------------------------< Object >

    @Override
    public final boolean equals(Object that) {
        if (this == that) {
            return true;
        } else if (that instanceof Path) {
            Path path = (Path) that;
            return path.denotesName()
                && name.equals(path.getName())
                && getNormalizedIndex() == path.getNormalizedIndex()
                && super.equals(that);
        } else {
            return false;
        }
    }

    @Override
    public final int hashCode() {
        return super.hashCode() * 37 + name.hashCode() + getNormalizedIndex();
    }

}
