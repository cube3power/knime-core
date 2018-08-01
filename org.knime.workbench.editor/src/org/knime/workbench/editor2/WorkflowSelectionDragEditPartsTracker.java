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
 *   02.03.2006 (sieb): created
 */
package org.knime.workbench.editor2;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.Request;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.gef.commands.UnexecutableCommand;
import org.eclipse.gef.tools.AbstractTool;
import org.eclipse.gef.tools.DragEditPartsTracker;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.knime.core.node.NodeLogger;
import org.knime.core.ui.node.workflow.NodeContainerUI;
import org.knime.core.ui.node.workflow.SubNodeContainerUI;
import org.knime.workbench.editor2.commands.AsyncCommand;
import org.knime.workbench.editor2.editparts.AbstractPortEditPart;
import org.knime.workbench.editor2.editparts.AbstractWorkflowPortBarEditPart;
import org.knime.workbench.editor2.editparts.ConnectionContainerEditPart;
import org.knime.workbench.editor2.editparts.NodeContainerEditPart;

/**
 * Adjusts the default <code>DragEditPartsTracker</code> to create commands that also move bendpoints.
 *
 * @author Christoph Sieb, University of Konstanz
 */
public class WorkflowSelectionDragEditPartsTracker extends DragEditPartsTracker {

    // Commented out - see comments in handleButtonDown
//    private static final List<EditPart> EMPTY_EDIT_PARTS = Collections.unmodifiableList(new ArrayList<>());

    private static final NodeLogger LOGGER = NodeLogger.getLogger(WorkflowSelectionDragEditPartsTracker.class);


    private Set<EditPart> m_temporarilyAddedChildrenParts;

    /**
     * Constructs a new WorkflowSelectionDragEditPartsTracker with the given
     * source edit part.
     *
     * @param sourceEditPart the source edit part
     */
    public WorkflowSelectionDragEditPartsTracker(final EditPart sourceEditPart) {
        super(sourceEditPart);
    }

    /*
     * This is a utility method candidate - potentially also something we would cram into WorkflowEditor
     *
     * NOTE: currently commented out - see the comments in handleButtonDown
     */
 /*
    @SuppressWarnings("unchecked")
    private static List<EditPart> getEditPartsContainedOrIntersectingBounds(final WorkflowEditor we,
        final Rectangle bounds) {
        if ((bounds == null) || (bounds.width() <= 0) || (bounds.height() < 0)) {
            return EMPTY_EDIT_PARTS;
        }

        final ArrayList<EditPart> editParts = new ArrayList<>();
        final ScrollingGraphicalViewer provider = (ScrollingGraphicalViewer)we.getEditorSite().getSelectionProvider();

        if (provider == null) {
            return EMPTY_EDIT_PARTS;
        }

        final EditPart editorPart = (EditPart)provider.getRootEditPart().getChildren().get(0);
        final List<EditPart> children = editorPart.getChildren();

        children.stream().forEach((ep) -> {
            if (ep instanceof AbstractGraphicalEditPart) {
                final IFigure f = ((AbstractGraphicalEditPart)ep).getFigure();
                final Rectangle figureBounds = f.getBounds();

                if (bounds.contains(figureBounds)
                    || (bounds.intersects(figureBounds) && (!figureBounds.contains(bounds)))) {
                    editParts.add(ep);
                }
            }
        });

        return editParts;
    }
*/

    /*
     * once getOperationSet() is called, its value is cached in a private scoped variable; we need to null it out so we
     * can call gOS again to recache - without calling deactivate and activate which produces unwanted side-effects
     */
    private void resetCachedOperationSet() {
        try {
            final Field f = AbstractTool.class.getDeclaredField("operationSet");

            f.setAccessible(true);

            f.set(this, null);
          } catch (Exception e) {
            LOGGER.warn("Unable to null out the operationSet cached value.", e);
          }

    }

    @SuppressWarnings("unchecked") // generic casting
    @Override
    protected boolean handleButtonUp(final int button) {
        final boolean result = super.handleButtonUp(button);

        // At the moment, m_temporarilyAddedChildrenParts will always be null; see comments in handleButtonDown
        if (m_temporarilyAddedChildrenParts != null) {
            final HashSet<EditPart> selected = new HashSet<>(getOperationSet());

            selected.removeAll(m_temporarilyAddedChildrenParts);

            final StructuredSelection selectionSet = new StructuredSelection(new ArrayList<>(selected));
            getCurrentViewer().setSelection(selectionSet);

            resetCachedOperationSet();
            getOperationSet();

            m_temporarilyAddedChildrenParts = null;
        }

        return result;
    }

    /*
     * {@inheritDoc}
     */
//    @SuppressWarnings("unchecked")  // generic casting - commented out as long as the below block is commented out
    @Override
    protected boolean handleButtonDown(final int button) {
        // don't do any state changes if this is the pan button
        if (button != WorkflowSelectionTool.PAN_BUTTON) {
/*
    ** I am leaving this work in the code, because i'm pretty confident that after we do the initial public release of
    *   the feature represented in AP-8593, the next request will be to turn this on (this enables the dragging of
    *   all spatial children of an annotation (for example, an annotation that has in its bounds 2 nodes: dragging the
    *   annotation would drag the 2 nodes with it).

            final WorkflowEditor we =
                (WorkflowEditor)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();

            if (we.getEditorMode().equals(WorkflowEditorMode.ANNOTATION_EDIT)) {
                final HashSet<EditPart> selected = new HashSet<>(getOperationSet());
                // TODO there are avenues of optimization here - for example build M many maximal bounding rectangles
                //          from the N original bounding rectangles (where M <= N)
                m_temporarilyAddedChildrenParts = new HashSet<>();

                // This is the case of the direct selection of an annotation (as opposed to marquee selection)
                if (selected.size() == 0) {
                    selected.add(getSourceEditPart());
                }

                selected.stream().forEach((ep) -> {
                    if (ep instanceof AnnotationEditPart) {
                        final Rectangle bounds = ((AnnotationEditPart)ep).getFigure().getBounds();
                        final List<EditPart> adds = getEditPartsContainedOrIntersectingBounds(we, bounds);

                        adds.stream().forEach((add) -> {
                            if (!selected.contains(add)) {
                                m_temporarilyAddedChildrenParts.add(add);
                            }
                        });
                    }
                });

                final ArrayList<EditPart> newSelection = new ArrayList<>(selected);
                newSelection.addAll(m_temporarilyAddedChildrenParts);

                final StructuredSelection selectionSet = new StructuredSelection(newSelection);
                getCurrentViewer().setSelection(selectionSet);

                resetCachedOperationSet();
                getOperationSet();
            }
*/

            return super.handleButtonDown(button);
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean handleDoubleClick(final int button) {
        EditPart part = getSourceEditPart();
        if (part instanceof NodeContainerEditPart && getCurrentInput().isModKeyDown(SWT.MOD1)) {
            NodeContainerEditPart ncPart = ((NodeContainerEditPart)part);
            NodeContainerUI container = (NodeContainerUI)ncPart.getModel();
            if (container instanceof SubNodeContainerUI) {
                ncPart.openSubWorkflowEditor();
                return false;
            }
        }
        return super.handleDoubleClick(button);
    }

    /**
     * Asks each edit part in the
     * {@link org.eclipse.gef.tools.AbstractTool#getOperationSet() operation set}
     * to contribute to a {@link CompoundCommand} after first setting the
     * request type to either {@link org.eclipse.gef.RequestConstants#REQ_MOVE}
     * or {@link org.eclipse.gef.RequestConstants#REQ_ORPHAN}, depending on the
     * result of {@link #isMove()}.
     *
     * Additionally the method creates a command to adapt connections where both
     * node container are include in the drag operation.
     *
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")  // generic casting
    @Override
    protected Command getCommand() {

        CompoundCommand command = new CompoundCommand();
        command.setDebugLabel("Drag Object Tracker");

        Iterator<?> iter = getOperationSet().iterator();

        Request request = getTargetRequest();

        if (isCloneActive()) {
            request.setType(REQ_CLONE);
        } else if (isMove()) {
            request.setType(REQ_MOVE);
        } else {
            request.setType(REQ_ORPHAN);
        }

        List<AsyncCommand> asyncCommands = new ArrayList<AsyncCommand>();
        if (!isCloneActive()) {
            while (iter.hasNext()) {
                EditPart editPart = (EditPart)iter.next();
                Command c = editPart.getCommand(request);
                if(!collectIfAsync(c, asyncCommands)) {
                    command.add(c);
                }
            }
        }

        // now add the commands for the node-embraced connections
        ConnectionContainerEditPart[] connectionsToAdapt =
                getEmbracedConnections(getOperationSet());
        for (ConnectionContainerEditPart connectionPart : connectionsToAdapt) {
            Command c = connectionPart.getBendpointAdaptionCommand(request);
            if (!collectIfAsync(c, asyncCommands)) {
                command.add(c);
            }
        }

        //create one single command from the async commands such that they are executed as one
        if (!asyncCommands.isEmpty()) {
            command.add(AsyncCommand.combineWithRefresh(asyncCommands,
                "Waiting to complete operations on selected nodes and connections ..."));
        }

        if (!isMove() || isCloneActive()) {

            if (!isCloneActive()) {
                request.setType(REQ_ADD);
            }

            if (getTargetEditPart() == null) {
                command.add(UnexecutableCommand.INSTANCE);
            } else {
                command.add(getTargetEditPart().getCommand(getTargetRequest()));
            }
        }

        return command;
    }

    private static boolean collectIfAsync(final Command c, final List<AsyncCommand> asyncCommands) {
        if (c instanceof AsyncCommand && ((AsyncCommand)c).shallExecuteAsync()) {
            asyncCommands.add((AsyncCommand)c);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the connections whose source and target is contained in the argument list.
     * @param parts list of selected nodes
     * @return the connections whose source and target is contained in the argument list.
     */
    public static ConnectionContainerEditPart[] getEmbracedConnections(
            final List<EditPart> parts) {

        // result list
        List<ConnectionContainerEditPart> result =
                new ArrayList<ConnectionContainerEditPart>();

        for (EditPart part : parts) {
            if (part instanceof NodeContainerEditPart
                    || part instanceof AbstractWorkflowPortBarEditPart) {
                EditPart containerPart = part;

                ConnectionContainerEditPart[] outPortConnectionParts =
                        getOutportConnections(containerPart);

                // if one of the connections in-port-node is included in the
                // selected list, the connections bendpoints must be adapted
                for (ConnectionContainerEditPart connectionPart
                            : outPortConnectionParts) {

                    // get the in-port-node part of the connection and check
                    AbstractPortEditPart inPortPart = null;
                    if (connectionPart.getTarget() != null
                            && ((AbstractPortEditPart)connectionPart
                                    .getTarget()).isInPort()) {
                        inPortPart =
                                (AbstractPortEditPart)connectionPart
                                        .getTarget();
                    } else if (connectionPart.getSource() != null) {
                        inPortPart =
                                (AbstractPortEditPart)connectionPart
                                        .getSource();
                    }

                    if (inPortPart != null
                            && isPartInList(inPortPart.getParent(), parts)) {
                        result.add(connectionPart);
                    }
                }

            }
        }
        return result.toArray(new ConnectionContainerEditPart[result.size()]);
    }

    @SuppressWarnings("unchecked")
    private static ConnectionContainerEditPart[] getOutportConnections(
            final EditPart containerPart) {

        // result list
        List<ConnectionContainerEditPart> result =
                new ArrayList<ConnectionContainerEditPart>();
        List<EditPart> children = containerPart.getChildren();

        for (EditPart part : children) {
            if (part instanceof AbstractPortEditPart) {
                AbstractPortEditPart outPortPart = (AbstractPortEditPart)part;

                // append all connection edit parts
                result.addAll(outPortPart.getSourceConnections());
            }
        }

        return result.toArray(new ConnectionContainerEditPart[result.size()]);
    }

    private static boolean isPartInList(final EditPart partToCheck,
            final List<EditPart> parts) {

        for (EditPart part : parts) {

            if (part == partToCheck) {

                return true;
            }
        }

        return false;
    }
}
