/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
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
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
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
 * -------------------------------------------------------------------
 *
 * History
 *   02.07.2006 (sieb): created
 */
package org.knime.workbench.ui.wizards.export;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.dialogs.ExportWizard;
import org.eclipse.ui.internal.wizards.datatransfer.ArchiveFileExportOperation;
import org.knime.core.node.FileNodePersistor;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodePersistor;
import org.knime.core.node.workflow.SingleNodeContainer;
import org.knime.core.node.workflow.WorkflowPersistor;
import org.knime.core.util.SWTUtilities;
import org.knime.core.util.VMFileLocker;
import org.knime.workbench.ui.navigator.KnimeResourceUtil;

/**
 * This wizard exports KNIME workflows and workflow groups if workflows are
 * selected which are in different workflow groups.
 *
 *
 * @author Christoph Sieb, University of Konstanz
 * @author Fabian Dill, KNIME AG, Zurich, Switzerland
 *
 * @deprecated since AP 3.0
 */
@Deprecated
public class WorkflowExportWizard extends ExportWizard
    implements IExportWizard {
    private WorkflowExportPage m_page;

    private ISelection m_selection;

    private final Collection<IContainer>m_workflowsToExport
        = new ArrayList<IContainer>();

    private boolean m_excludeData;

    private IContainer m_container;

    /**
     * Constructor.
     */
    public WorkflowExportWizard() {
        super();
        setWindowTitle("Export");
        setNeedsProgressMonitor(true);
    }

    /**
     * Adding the page to the wizard.
     */
    @Override
    public void addPages() {
        m_page = new WorkflowExportPage(m_selection);
        addPage(m_page);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public boolean canFinish() {
        Collection<IContainer> workflows = m_page.getWorkflows();
        final List<IResource> resourceList = new ArrayList<IResource>();
        for (IContainer c : workflows) {
            addResourcesFor(resourceList, c, m_excludeData);
        }
        if (resourceList.isEmpty()) {
            return false;
        }
        return m_page.isPageComplete();
    }

    /**
     * This method is called when 'Finish' button is pressed in the wizard. We
     * will create an operation and run it using wizard as execution context.
     *
     * @return If finished successfully
     */
    @Override
    public boolean performFinish() {

        // first save dirty editors
        boolean canceled = !PlatformUI.getWorkbench().saveAllEditors(true);
        if (canceled) {
            return false;
        }

        m_page.saveDialogSettings();

        m_container = m_page.getSelectedContainer();
        String fileName = m_page.getFileName().trim();
        m_excludeData = m_page.excludeData();
        m_workflowsToExport.clear();
        m_workflowsToExport.addAll(m_page.getWorkflows());

        // if the specified export file already exist ask the user
        // for confirmation

        final File exportFile = new File(fileName);

        if (exportFile.exists()) {
            // if it exists we have to check if we can write to:
            if (!exportFile.canWrite() || exportFile.isDirectory()) {
                // display error
                m_page.setErrorMessage("Cannot write to specified file");
                return false;
            }
            boolean overwrite = MessageDialog.openQuestion(getShell(),
                    "File already exists...",
                    "File already exists.\nDo you want to overwrite the "
                    + "specified file ?");
            if (!overwrite) {
                return false;
            }
        } else {
            File parentFile = exportFile.getParentFile();
            if ((parentFile != null) && parentFile.exists()) {
                // check if we can write to
                if (!parentFile.canWrite() || !parentFile.isDirectory()) {
                    // display error
                    m_page.setErrorMessage("Cannot write to specified file");
                    return false;
                }
            } else if ((parentFile != null) && !parentFile.exists() && !exportFile.getParentFile().mkdirs()) {
                boolean wasRoot = false;
                for (File root : File.listRoots()) {
                    if (exportFile.getParentFile().equals(root)) {
                        wasRoot = true;
                        break;
                    }
                }
                if (!wasRoot) {
                    m_page.setErrorMessage("Failed to create: "
                            + exportFile.getAbsolutePath()
                            + ". \n Please check if it is a "
                            + "valid file name.");
                    return false;
                }
            }
        }

        IRunnableWithProgress op = new IRunnableWithProgress() {
            @Override
            public void run(final IProgressMonitor monitor)
                    throws InvocationTargetException {
                try {
                    doFinish(m_container, exportFile, monitor);
                } catch (CoreException e) {
                    throw new InvocationTargetException(e);
                } finally {
                    monitor.done();
                }
            }
        };
        try {
            getContainer().run(true, false, op);
        } catch (InterruptedException e) {
            return false;
        } catch (InvocationTargetException e) {
            Throwable realException = e.getTargetException();
            String message = realException.getMessage();

            message = "Problem during export: " + e.getMessage();
            e.printStackTrace();
            MessageDialog.openError(getShell(), "Error", message);
            return false;
        }
        return true;
    }

    /**
     * Implements the exclude policy. At the moment the "intern" folder and
     * "*.zip" files are excluded.
     *
     * @param resource the resource to check
     * @return true if the given resource should be excluded, false if it
     * should be included
     */
    protected static boolean excludeResource(final IResource resource) {
        String name = resource.getName();
        if (name.equals("internal")) {
            return true;
        }
        if (resource.getType() == IResource.FILE) {
            if (name.startsWith("model_")) {
                return true;
            }
            if (name.equals("data.xml")) {
                return true;
            }
        }
        // exclusion list for workflows in format of 2.x
        switch (resource.getType()) {
            case IResource.FOLDER:
                if (name.startsWith(
                        FileNodePersistor.PORT_FOLDER_PREFIX)) {
                    return true;
                }
                if (name.startsWith(
                        FileNodePersistor.INTERNAL_TABLE_FOLDER_PREFIX)) {
                    return true;
                }
                if (name.startsWith(
                        FileNodePersistor.FILESTORE_FOLDER_PREFIX)) {
                    return true;
                }
                if (name.startsWith(NodePersistor.INTERN_FILE_DIR)) {
                    return true;
                }
                if (name.startsWith(SingleNodeContainer.DROP_DIR_NAME)) {
                    return true;
                }
                break;
            case IResource.FILE:
                if (name.startsWith(WorkflowPersistor.SAVED_WITH_DATA_FILE)) {
                    return true;
                }
                if (name.startsWith(VMFileLocker.LOCK_FILE)) {
                    return true;
                }
                break;
            default:
        }
        // get extension to check if this resource is a zip file
        return name.toLowerCase().endsWith(".zip");
    }


    /**
     * The worker method. It will find the container, create the export file if
     * missing or just replace its contents.
     */
    private void doFinish(final IContainer container, final File fileName,
            final IProgressMonitor monitor)
            throws CoreException {
        // start zipping
        monitor.beginTask("Collect resources... ", 3);
        container.refreshLocal(IResource.DEPTH_INFINITE, monitor);

        // if the data should be excluded from the export
        // iterate over the resources and add only the wanted stuff
        // i.e. the "intern" folder and "*.zip" files are excluded
        final List<IResource> resourceList = new ArrayList<IResource>();
        for (IContainer child : m_workflowsToExport) {
            // add all files within the workflow
            addResourcesFor(resourceList, child, m_excludeData);
        }
        monitor.worked(1);
        try {
            ArchiveFileExportOperation exportOperation = null;
            // find lead offset
            final int leadOffset = findLeadOffset();
            if (leadOffset >= 0) {
                // if we have a workflow selected which is inside a workflow
                // group we want to export only the workflow, i.e. strip the
                // preceeding workflow groups from path:
                // this is done with the offset
                exportOperation = new OffsetArchiveFileExportOperation(
                                container, resourceList, fileName.getPath());
                ((OffsetArchiveFileExportOperation)exportOperation).setOffset(
                        leadOffset);
            } else {
                exportOperation = new ArchiveFileExportOperation(
                        container, resourceList, fileName.getPath());
            }
            monitor.beginTask("Write to file... " + fileName, 3);
            exportOperation.run(monitor);
        } catch (Throwable t) {
            final String error = "KNIME project could not be exported."
                + "\n Reason: " + t.getMessage();
            NodeLogger.getLogger(getClass()).error(error, t);
            final Display display = Display.getDefault();
            display.syncExec(() -> {
                MessageDialog.openError(SWTUtilities.getActiveShell(display), "Export could not be completed...",
                    error);
            });
        }
        monitor.worked(1);

    }

    private int findLeadOffset() {
        // go up until root is reached
        int offset = 0;
        IContainer p = m_container;
        if (ResourcesPlugin.getWorkspace().getRoot().equals(
                m_container.getParent())) {
            // nothing to strip -> use common export operation
            return -1;
        }
        while (p != null && !p.equals(ResourcesPlugin.getWorkspace()
                .getRoot())) {
            offset++;
            p = p.getParent();
        }
        return offset;
    }

    /**
     *
     * @param resourceList list of resources to export
     * @param resource the resource representing the workflow directory
     * @param excludeData true if KNIME data files should be excluded
     */
    public static void addResourcesFor(
            final List<IResource> resourceList,
            final IResource resource, final boolean excludeData) {
        if (resource instanceof IWorkspaceRoot) {
            // ignore the workspace root in order to avoid adding all workflows
            // method should be called for every workflow and every group that
            // should be exported
            return;
        }

        if (KnimeResourceUtil.isWorkflow(resource)) {
            // collect the content of the workflow
            addWorkflowResources(resourceList, resource, excludeData);
            return;
        } else if (KnimeResourceUtil.isWorkflowGroup(resource)) {
            // add group files - but not recursively the contained groups/flows
            addWorkflowGroupResources(resourceList, resource);
            return;
        }

    }

    /**
     * Used to collect all files contained in a workflow dir.
     * Collects recursively the file resources of the specified resource. If the
     * exclude data flag is true, files/dirs containing workflow node data are
     * not included.
     *
     * @param resourceList list things are added to (must not be null)
     * @param resource file or dir
     * @param excludeData if true, files/dirs containing node data are not added
     *            to the list.
     */
    private static void addWorkflowResources(final List<IResource> resourceList,
            final IResource resource, final boolean excludeData) {
        if (!KnimeResourceUtil.isWorkflow(resource)
                && !KnimeResourceUtil.isMetaNode(resource)
                && excludeData && excludeResource(resource)) {
            // workflows and metanodes named like excluded resourced
            // (e.g. "drop" or "internal") should still be included
            return;
        }
        // if this is a file add it to the list
        if (resource instanceof IFile) {
            resourceList.add(resource);
            return;
        }
        IResource[] resources;
        try {
            resources = ((IContainer)resource).members();
        } catch (Exception e) {
            throw new IllegalStateException("Members of folder "
                    + resource.getName() + " could not be retrieved.");
        }
        // else visit all child resources
        for (IResource currentResource : resources) {
            addWorkflowResources(resourceList, currentResource,
                    excludeData);
        }

    }

    /**
     * Adds resources that belong to a workflow group. Files that are contained
     * in a group are added (mostly this will only be the meta info file).
     * Doesn't work recursively, i.e. no sub-flows/groups contained in the
     * group are added.
     *
     * @param resourceList to add resources to
     * @param groupRes the workflow group
     */
    private static void addWorkflowGroupResources(
            final List<IResource> resourceList, final IResource groupRes) {
        if (KnimeResourceUtil.isWorkflowGroup(groupRes)) {
            // add all files contained (don't descend into sub dirs)
            IResource[] resources;
            try {
                resources = ((IContainer)groupRes).members();
            } catch (Exception e) {
                throw new IllegalStateException("Members of folder "
                        + groupRes.getName() + " could not be retrieved.");
            }
            for (IResource currentResource : resources) {
                if (currentResource instanceof IFile) {
                    resourceList.add(currentResource);
                }
            }
        }
    }

    /**
     * We will initialize file contents with a sample text.
     * @return an empty content stream
     */
    InputStream openContentStream() {
        return new ByteArrayInputStream(new byte[0]);
    }


    /**
     * We will accept the selection in the workbench to see if we can initialize
     * from it.
     *
     * {@inheritDoc}
     */
    @Override
    public void init(final IWorkbench workbench,
            final IStructuredSelection selection) {
        this.m_selection = selection;
    }
}
