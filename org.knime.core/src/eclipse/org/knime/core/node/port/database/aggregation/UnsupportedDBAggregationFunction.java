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
 *   03.09.2014 (koetter): created
 */
package org.knime.core.node.port.database.aggregation;

import org.knime.core.data.DataValue;
import org.knime.core.data.def.StringCell;

/**
 * Class that indicates an unsupported db aggregation function.
 * @author Tobias Koetter, KNIME.com, Zurich, Switzerland
 * @since 2.11
 */
public class UnsupportedDBAggregationFunction extends SimpleDBAggregationFunction
implements InvalidAggregationFunction {

    private final String m_dbIdentifier;

    /**
     * @param name the name of the function
     * @param dbIdentifier the database identifier. Can be <code>null</code> if unknown.
     */
    public UnsupportedDBAggregationFunction(final String name, final String dbIdentifier) {
        super(name, createMessage(name, dbIdentifier), StringCell.TYPE, DataValue.class);
        m_dbIdentifier = dbIdentifier;
    }

    private static String createMessage(final String name, final String dbIdentifier) {
        final String msg = "The function '" + name + "' is not supported by ";
        if (dbIdentifier != null) {
            return msg + dbIdentifier + ".";
        }
        return msg + "the current database.";
    }

    /**
     * @return the dbIdentifier might be <code>null</code>
     */
    public String getDbIdentifier() {
        return m_dbIdentifier;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getErrorMessage() {
        return getDescription();
    }
}
