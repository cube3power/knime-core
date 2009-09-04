/* This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2009
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 */
package org.knime.base.node.io.pmml.write;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.knime.core.node.CanceledExecutionException;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.ExecutionMonitor;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeModel;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.PortType;
import org.knime.core.node.port.pmml.PMMLPortObject;

/**
 * 
 * @author Fabian Dill, University of Konstanz
 */
public class PMMLWriterNodeModel extends NodeModel {
    
    
    private static final NodeLogger LOGGER = NodeLogger.getLogger(
            PMMLWriterNodeModel.class);
    
    private final SettingsModelString m_outfile 
        = PMMLWriterNodeDialog.createFileModel();
    
    private final SettingsModelBoolean m_overwriteOK
        = PMMLWriterNodeDialog.createOverwriteOKModel();
    
    /**
     * 
     */
    public PMMLWriterNodeModel() {
        super(new PortType[] {new PortType(PMMLPortObject.class)}, 
                new PortType[] {});
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs)
            throws InvalidSettingsException {
        checkFileLocation(m_outfile.getStringValue());
        return new PortObjectSpec[] {};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected PortObject[] execute(final PortObject[] inData, 
            final ExecutionContext exec)
            throws Exception {
        checkFileLocation(m_outfile.getStringValue());
        File f = new File(m_outfile.getStringValue());
        PMMLPortObject pmml = (PMMLPortObject)inData[0];
        pmml.save(new FileOutputStream(f));
        return new PortObject[] {};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadInternals(final File nodeInternDir, 
            final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadValidatedSettingsFrom(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_outfile.loadSettingsFrom(settings);
        try {
            // property added in v2.1 -- if missing (old flow), set it to true
            m_overwriteOK.loadSettingsFrom(settings);
        } catch (InvalidSettingsException ise) {
            m_overwriteOK.setBooleanValue(true);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveInternals(final File nodeInternDir, 
            final ExecutionMonitor exec)
            throws IOException, CanceledExecutionException {
        // ignore -> no view
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings) {
        m_outfile.saveSettingsTo(settings);
        m_overwriteOK.saveSettingsTo(settings);
    }
    
    private void checkFileLocation(final String fileName)
            throws InvalidSettingsException {
        LOGGER.debug("file name: " + fileName);
        if (fileName == null || fileName.isEmpty()) {
            throw new InvalidSettingsException("No file name provided! " 
                    + "Please enter a valid file name.");            
        }
        File f = new File(fileName);
        if ((f.exists() && !f.canWrite())
                || (!f.exists() && !f.getParentFile().canWrite())) {
            throw new InvalidSettingsException("File name \"" + fileName
                    + "\" is not valid. Please enter a valid file name.");
        }
        if (f.exists() && !m_overwriteOK.getBooleanValue()) {
            throw new InvalidSettingsException("File exists and can't be "
                    + "overwritten, check dialog settings");
        }
        if (f.exists() && m_overwriteOK.getBooleanValue()) {
            setWarningMessage("File exists and will be overwritten");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void validateSettings(final NodeSettingsRO settings)
            throws InvalidSettingsException {
        m_outfile.validateSettings(settings);
        String fileName = ((SettingsModelString)m_outfile.
                createCloneWithValidatedValue(settings)).getStringValue();
        if (fileName == null || fileName.length() == 0) {
            throw new InvalidSettingsException("No output file specified");
        }
        // overwriteOk added in v2.1 - can't validate
    }

}
