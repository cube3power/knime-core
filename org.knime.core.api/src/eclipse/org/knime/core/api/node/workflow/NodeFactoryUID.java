/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME GmbH, Konstanz, Germany
 *  Website: http://www.knime.org; Email: contact@knime.org
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME GMBH herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Sep 20, 2016 (hornm): created
 */
package org.knime.core.api.node.workflow;

import org.knime.core.node.util.CheckUtils;

/**
 * A unique identifier for a particular NodeFactory. The identifier is composed of the fully-qualified class name and
 * the name of the node the node factory represents.
 *
 * @author Martin Horn, KNIME.com
 */
public final class NodeFactoryUID {

    private final String m_className;
    private final String m_nodeName;

    private NodeFactoryUID(final Builder builder) {
        CheckUtils.checkArgumentNotNull(builder.m_className, "Class name must not be null.");
        CheckUtils.checkArgumentNotNull(builder.m_nodeName, "Node name must not be null.");
        m_className = builder.m_className;
        m_nodeName = builder.m_nodeName;
    }

    /**
     * @return the node factory's fully-qualified class name
     */
    public String getClassName() {
        return m_className;
    }

    /**
     * @return the name of the node
     */
    public String getNodeName() {
        return m_nodeName;
    }

    /**
     * @param className the node factory's fully qualified class name - not <code>null</code>
     * @param nodeName  the name of the node - not <code>null</code>
     * @return a new {@link Builder} with default values.
     */
    public Builder builder(final String className, final String nodeName) {
        return new Builder(className, nodeName);
    }

    /**
     * Builder to create {@link NodeFactoryUID} objects.
     */
    public static final class Builder {

        private String m_className;
        private String m_nodeName;

        private Builder(final String className, final String nodeName) {
            m_className = className;
            m_nodeName = nodeName;
        }

        /**
         * Sets the fully-qualified class name of the node factory.
         *
         * @param name
         * @return this
         */
        public Builder setClassName(final String name) {
            m_className = name;
            return this;
        }

        /**
         * Sets the node's name.
         *
         * @param name
         * @return this
         */
        public Builder setNodeName(final String name) {
            m_nodeName = name;
            return this;
        }

        /**
         * @return the newly created {@link NodeFactoryUID} from this builder
         */
        public NodeFactoryUID build() {
            return new NodeFactoryUID(this);
        }

    }


}
