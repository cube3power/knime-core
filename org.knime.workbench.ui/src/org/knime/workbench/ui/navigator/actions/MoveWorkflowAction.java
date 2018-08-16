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
 * ------------------------------------------------------------------------
 */
package org.knime.workbench.ui.navigator.actions;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionValidator;
import org.knime.core.node.NodeLogger;
import org.knime.core.util.SWTUtilities;
import org.knime.core.util.VMFileLocker;
import org.knime.workbench.ui.metainfo.model.MetaInfoFile;
import org.knime.workbench.ui.nature.KNIMEProjectNature;
import org.knime.workbench.ui.nature.KNIMEWorkflowSetProjectNature;
import org.knime.workbench.ui.navigator.KnimeResourceUtil;
import org.knime.workbench.ui.navigator.actions.selection.ResourceSelectDialog;

/**
 *
 * @author Fabian Dill, KNIME.com AG
 *
 * @deprecated since AP 3.0
 */
@Deprecated
public class MoveWorkflowAction extends Action implements IRunnableWithProgress {

    private static final NodeLogger LOGGER = NodeLogger
            .getLogger(MoveWorkflowAction.class);

    /** ID of this action. */
    public static final String ID = "org.knime.workbench.ui.MoveAction";

    private IPath m_source;

    private IPath m_target;

    private final TreeViewer m_viewer;

    /**
     *
     * @param source path to the source (which sould be moved)
     * @param target target to which the source should be moved
     */
    public MoveWorkflowAction(final IPath source, final IPath target) {
        m_source = source;
        m_target = target;
        m_viewer = null;
    }

    /**
     * Used when instantiated for a menu (w/o target).
     *
     * @param parentShell
     * @param source
     */
    public MoveWorkflowAction(final TreeViewer viewer) {
        super("Move...");
        setId(ID);
        m_viewer = viewer;
    }

    /**
     *
     * @return path to the source (which sould be transfered)
     */
    public IPath getSource() {
        return m_source;
    }

    /**
     *
     * @return target to which the source should be transfered
     */
    public IPath getTarget() {
        return m_target;
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void run() {

        if (!isEnabled()) {
            LOGGER.error(
                    "This action is disabled. Even though it is "
                    + "available through the menu - it is doing nothing "
                    + "with the current selection. This is a know issue. "
                    + "Aka feature.");
            return;
        }

        if ((getTarget() == null) && !setSourceAndselectTarget()) {
            LOGGER.debug("Move canceled by user.");
            return;
        }
        try {
            PlatformUI.getWorkbench().getProgressService()
                    .busyCursorWhile(this);
        } catch (InvocationTargetException e) {
            LOGGER.error("Error while moving resource " + getSource(), e);
        } catch (InterruptedException e) {
            LOGGER.error("Error while moving resource " + getSource(), e);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled() {
        if (m_viewer != null) {
            IStructuredSelection sel =
                    (IStructuredSelection)m_viewer.getSelection();
            if (sel.size() != 1) {
                return false;
            }
            Object s = sel.getFirstElement();
            if (!(s instanceof IResource)) {
                return false;
            }
            IResource r = (IResource)s;
            if (KnimeResourceUtil.isWorkflow(r)
                    || KnimeResourceUtil.isWorkflowGroup(r)) {
                return true;
            }
            return false;
        } else {
            return true;
        }
    }

    private boolean setSourceAndselectTarget() {
        // set the source from the tree selection
        IStructuredSelection sel =
                (IStructuredSelection)m_viewer.getSelection();
        if (sel.size() != 1) {
            LOGGER.debug("MoveAction selection size != 1");
            return false;
        }
        m_source = ((IResource)sel.getFirstElement()).getFullPath();

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        String srcName = m_source.toString();
        if (srcName.length() > 60 && m_source.segmentCount() > 3) {
            srcName =
                    m_source.segment(0) + "/.../"
                            + m_source.segment(m_source.segmentCount() - 2)
                            + "/" + m_source.lastSegment();
        }
        ResourceSelectDialog rsd =
                new ResourceSelectDialog(m_viewer.getControl().getShell(),
                        root, sel, "Select target destination for\n"
                                + srcName);
        rsd.setBlockOnOpen(true);
        rsd.setTitle("Move Destination Selection");
        rsd.setValidator(new ISelectionValidator() {
            @Override
            public String isValid(final Object selection) {
                if (selection instanceof IWorkspaceRoot) {
                    // root is valid
                    return null;
                }
                if (selection instanceof IResource) {
                    IResource r = (IResource)selection;
                    if (KnimeResourceUtil.isWorkflowGroup(r)) {
                        return null;
                    }
                }
                return "Please select a destination workflow group";
            }
        });
        int ret = rsd.open();
        if (ret != org.eclipse.jface.window.Window.OK) {
            return false;
        }

        m_target = rsd.getSelection().getFullPath();
        return true;
    }

    protected void moveFiles(final File source, final File target) {
        for (File f : source.listFiles()) {
            f.renameTo(new File(target, f.getName()));
        }
    }

    protected void deleteSourceDir(final IResource source,
            final IProgressMonitor monitor) throws CoreException {
        source.delete(true, monitor);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void run(final IProgressMonitor monitor)
            throws InvocationTargetException, InterruptedException {
        final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        final IResource source = root.findMember(getSource());
        IPath target = getTarget();
        IResource targetRes = root.findMember(target);
        if (source == null || target == null) {
            return;
        }
        // check for linked projects (unfortunately still supported)
        if (source instanceof IProject) {
            IProject p = (IProject)source;
            IPath loc = p.getLocation();
            if (!ResourcesPlugin.getWorkspace().getRoot().getLocation()
                    .isPrefixOf(loc)) {
                showUnsupportedLinkedProject(source.getName());
                return;
            }
        }
        // check whether the target is contained in source
        if (getSource().isPrefixOf(getTarget())) {
            LOGGER.debug("Operation not allowed. " + source.getName()
                    + " is parent resource of target " + getTarget());
            showIsParent(source.getName(), targetRes.getName());
            return;
        }

        if (!source.isLinked()) {
            // check if the source is an opened workflow
            if (containsOpenWorkflows(source)) {
                showWorkflowIsOpenMessage();
                return;
            }
            // lock to-be-moved workflows
            List<IContainer> wfs =
                    KnimeResourceUtil.getContainedWorkflows(Collections
                            .singletonList(source));
            List<IContainer> lockedWFs = new LinkedList<IContainer>();
            if (!lockWorkflows(wfs, lockedWFs)) {
                unlockWorkflows(lockedWFs);
                showWorkflowInUseMessage(KnimeResourceUtil
                        .isWorkflowGroup(source));
                return;
            }

            final File sourceFile = new File(source.getLocationURI());
            if (!sourceFile.exists()) {
                showUnsupportedLinkedProject(sourceFile.getName());
                return;
            }
            File targetFile = new File(targetRes.getLocationURI());
            LOGGER.debug("target path: " + targetFile);
            // create path here
            File targetDir =
                    new File(targetFile, getSourceNameInTarget());
            if (!targetDir.mkdir()) {
                // don't complain if target dir is parent of source...
                if (!targetFile.equals(sourceFile.getParentFile())) {
                    LOGGER.debug("target dir could not be created!");
                    showAlreadyExists(targetDir.getName(), targetFile.getName());
                }
                return;
            }
            // unlock and move
            unlockWorkflows(lockedWFs);
            moveFiles(sourceFile, targetDir);

            try {
                if (targetRes instanceof IWorkspaceRoot) {
                    IProject newProject =
                            ((IWorkspaceRoot)targetRes).getProject(targetDir
                                    .getName());
                    if (newProject.exists()) {
                        // exception handling -> project already exists
                        LOGGER.warn("A workflow " + targetDir.getName()
                                + " already exists in /");
                        showAlreadyExists(newProject.getName(),
                                "workspace root");
                        return;
                    }
                    // check whether this is a workflow or a workflow group
                    String natureId = KNIMEWorkflowSetProjectNature.ID;
                    if (KnimeResourceUtil.isWorkflow(source)) {
                        natureId = KNIMEProjectNature.ID;
                    }
                    newProject =
                            MetaInfoFile.createKnimeProject(
                                    newProject.getName(), natureId);
                }
                // exception handling
                deleteSourceDir(source, monitor);
                targetRes.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            } catch (CoreException ex) {
                throw new InvocationTargetException(ex);
            } catch (Exception e) {
                LOGGER.error("Error while moving/copying resource " + source,
                        e);
                throw new InvocationTargetException(e);
            }
        }
    }

    /**
     * @return the name of the source when moved/copied into the target.
     */
    protected String getSourceNameInTarget() {
        return getSource().toFile().getName();
    }

    /**
     * Returns true if src is or contains open workflows.
     *
     * @param src the flow or group to test
     * @return true if src is or contains open workflows
     */
    protected boolean containsOpenWorkflows(final IResource src) {
        if (KnimeResourceUtil.isWorkflow(src)) {
            return KnimeResourceUtil.isOpenedWorkflow(src);
        }
        if (!KnimeResourceUtil.isWorkflowGroup(src)) {
            return false;
        }
        if (!(src instanceof IContainer)) {
            return false;
        }
        IResource[] kids;
        try {
            kids = ((IContainer)src).members();
        } catch (CoreException e) {
            return false;
        }
        for (IResource r : kids) {
            if (containsOpenWorkflows(r)) {
                return true;
            }
        }
        return false;
    }

    private boolean lockWorkflows(final List<IContainer> toBeLockedWFs,
            final List<IContainer> lockedWF) {
        boolean result = true;
        assert lockedWF.size() == 0;
        // open workflows can be locked multiple times.
        for (IContainer wf : toBeLockedWFs) {
            assert KnimeResourceUtil.isWorkflow(wf);
            if (VMFileLocker.lockForVM(new File(wf.getLocationURI()))) {
                lockedWF.add(wf);
            } else {
                result = false;
            }
        }
        return result;
    }

    private void unlockWorkflows(final List<IContainer> workflows) {
        for (IContainer wf : workflows) {
            assert KnimeResourceUtil.isWorkflow(wf);
            VMFileLocker.unlockForVM(new File(wf.getLocationURI()));
        }
    }

    private void showWorkflowIsOpenMessage() {
        final Display display = Display.getDefault();

        display.syncExec(() -> {
            MessageDialog.openInformation(SWTUtilities.getActiveShell(display), "Open Workflow",
                "Cannot move opened workflows. Please save and close the open workflow editor.");
        });
    }

    private void showUnsupportedLinkedProject(final String name) {
        final Display display = Display.getDefault();

        display.syncExec(() -> {
            MessageDialog.openInformation(SWTUtilities.getActiveShell(display), "Unsupported Linked Project",
                "\"" + name + "\" is a linked resource. Linked resources are only linked to the workspace "
                    + "but located elsewhere. They are not supported by this operation.");
        });
    }

    private void showWorkflowInUseMessage(final boolean isGroup) {
        final Display display = Display.getDefault();

        display.syncExec(() -> {
            if (isGroup) {
                MessageDialog.openInformation(SWTUtilities.getActiveShell(display), "Locked Workflow",
                    "The selected workflow group contains a workflow that is locked by another "
                        + "user/instance and can't be moved/copied.");
            } else {
                MessageDialog.openInformation(SWTUtilities.getActiveShell(display), "Locked Workflow",
                    "The selected workflow is locked by another user/instance and can't be moved/copied.");
            }
        });
    }

    private void showAlreadyExists(final String name, final String target) {
        final Display display = Display.getDefault();

        display.syncExec(() -> {
            MessageDialog.openWarning(SWTUtilities.getActiveShell(display), "Resource already exists", "A folder \""
                + name + "\" already exists in \"" + target + "\".\nPlease rename before moving/copying.");
        });
    }

    private void showIsParent(final String source, final String target) {
        final Display display = Display.getDefault();

        display.syncExec(() -> {
            MessageDialog.openWarning(SWTUtilities.getActiveShell(display), "Cannot Move/Copy Resource",
                "Operation not allowed.\n\"" + source + "\" is parent resource of target \"" + target + "\"");
        });
    }

}
