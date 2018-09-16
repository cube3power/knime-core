/*
 * ------------------------------------------------------------------------
 *
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
 * ---------------------------------------------------------------------
 *
 * History
 *   Sep 15, 2018 (loki): created
 */
package org.knime.workbench.editor2;

import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.RectangleFigure;
import org.eclipse.draw2d.Viewport;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.ui.parts.ScrollingGraphicalViewer;
import org.eclipse.swt.graphics.Color;
import org.knime.core.node.NodeLogger;
import org.knime.workbench.core.util.ImageRepository;
import org.knime.workbench.core.util.ImageRepository.SharedImages;

/**
 * Our subclass of <code>ScrollingGraphicalViewer</code> which facilitates the pinning of info, warning and error
 * messages to the top of the viewport, regardless of where the user scrolls to on the canvas, or how the user resizes
 * the canvas' editor pane.
 *
 * @author loki der quaeler
 */
public class ViewportPinningGraphicalViewer extends ScrollingGraphicalViewer {
    private static final Color WARN_ERROR_MESSAGE_BACKGROUND = new Color(null, 255, 249, 0);
    private static final Color INFO_MESSAGE_BACKGROUND = new Color(null, 200, 200, 255);
    private static final int MESSAGE_BACKGROUND_OPACITY = 171;

    private static final int MESSAGE_INSET = 10;

    private static NodeLogger LOGGER = NodeLogger.getLogger(ViewportPinningGraphicalViewer.class);

    private enum MessageAttributes {

        INFO(0, INFO_MESSAGE_BACKGROUND, SharedImages.Info),
        WARNING(1, WARN_ERROR_MESSAGE_BACKGROUND, SharedImages.Warning),
        ERROR(2, WARN_ERROR_MESSAGE_BACKGROUND, SharedImages.Error);

        private final int m_index;
        private final Color m_fillColor;
        private final SharedImages m_icon;

        MessageAttributes(final int index, final Color c, final SharedImages icon) {
            m_index = index;
            m_fillColor = c;
            m_icon = icon;
        }

        /**
         * @return the internal arrays' index for the message type
         */
        public int getIndex() {
            return m_index;
        }

        /**
         * @return the fill color associated with this message type
         */
        public Color getFillColor() {
            return m_fillColor;
        }

        /**
         * @return the icon associated with this message type
         */
        public SharedImages getIcon() {
            return m_icon;
        }
    }


    private final AtomicBoolean m_listenersAdded = new AtomicBoolean(false);

    /* Message figures indexed per MessageIndex */
    private Label[] m_messages = new Label[3];

    /* Background rectangles for the message figures; indexed per MessageIndex */
    private RectangleFigure[] m_messageRects = new RectangleFigure[3];

    /**
     * Sets an info message (with an info icon and light purple background) at the top of the editor (above an error
     * message and above a warning message, if either or both exist.)
     *
     * @param msg the message to display or <code>null</code> to remove it
     */
    public void setInfoMessage(final String msg) {
        setMessage(msg, MessageAttributes.INFO);
    }

    /**
     * Sets a warning message displayed at the top of the editor (above an error message if there is any, and below an
     * info message if there is any.)
     *
     * @param msg the message to display or <code>null</code> to remove it
     */
    public void setWarningMessage(final String msg) {
        setMessage(msg, MessageAttributes.WARNING);
    }

    /**
     * Sets an error message displayed at the top of the editor (underneath a warning message and underneath an info
     * message, if either or both exist.)
     *
     * @param msg the message to display or <code>null</code> to remove it
     */
    public void setErrorMessage(final String msg) {
        setMessage(msg, MessageAttributes.ERROR);
    }

    /**
     * A less computationally / redraw intensive method to clear messages than call each set-message-type with null.
     */
    public void clearAllMessages() {
        for (int i = 0; i < m_messages.length; i++) {
            if (m_messages[i] != null) {
                removeFigureFromViewport(m_messages[i]);
                removeFigureFromViewport(m_messageRects[i]);
                m_messages[i] = null;
                m_messageRects[i] = null;
            }
        }
    }

    private void setMessage(final String msg, final MessageAttributes attributes) {
        final int index = attributes.getIndex();

        if (((msg == null) && (m_messages[index] == null))
            || ((msg != null) && (m_messages[index] != null) && msg.equals(m_messages[index].getText()))) {
            //nothing has changed
            return;
        }

        if (m_messages[index] != null) {
            removeFigureFromViewport(m_messages[index]);
            removeFigureFromViewport(m_messageRects[index]);
            m_messages[index] = null;
            m_messageRects[index] = null;
        }

        if (msg != null) {
            final Color fill = attributes.getFillColor();

            m_messageRects[index] = new TranslucentRectangle();
            m_messageRects[index].setOpaque(false);
            m_messageRects[index].setBackgroundColor(fill);
            m_messageRects[index].setForegroundColor(fill);
            addFigureToViewport(m_messageRects[index]);

            m_messages[index] = new Label(msg);
            m_messages[index].setOpaque(false);
            m_messages[index].setIcon(ImageRepository.getUnscaledIconImage(attributes.getIcon()));
            addFigureToViewport(m_messages[index]);
        }

        layoutMessages();
    }

    private void layoutMessages() {
        final Viewport v = getViewport();

        if (v != null) {
            final Rectangle bounds = v.getBounds();
            final Point location = v.getViewLocation();
            int yOffset = location.y;

            for (int i = 0; i < m_messages.length; i++) {
                if (m_messages[i] != null) {
                    final Dimension preferredSize = m_messages[i].getPreferredSize();
                    final Rectangle messageBounds = new Rectangle((location.x + MESSAGE_INSET),
                        (yOffset + MESSAGE_INSET), (bounds.width - (2 * MESSAGE_INSET)), preferredSize.height);
                    m_messages[i].setBounds(messageBounds);

                    final Rectangle rectangleBounds =
                        new Rectangle(location.x, yOffset, bounds.width, (messageBounds.height + (2 * MESSAGE_INSET)));
                    m_messageRects[i].getBounds().setBounds(rectangleBounds); //set the bounds without repainting it

                    yOffset += rectangleBounds.height;
                }
            }

            v.repaint();
        } else {
            LOGGER.warn("Could not get viewport to lay out messages.");
        }
    }

    private Viewport getViewport() {
        final FigureCanvas fc = getFigureCanvas();

        if (fc != null) {
            final Viewport v = fc.getViewport();

            if (v != null) {
                if (!m_listenersAdded.getAndSet(true)) {
                    v.addFigureListener((figure) -> {
                        // this is invoked when the size of the viewport changes
                        layoutMessages();
                    });
                    v.addPropertyChangeListener("viewLocation", (listener) -> {
                        // this is invoked on scroll related changes
                        layoutMessages();
                    });
                }
            } else {
                LOGGER.error("Could not get canvas' viewport.");
            }

            return v;
        } else {
            LOGGER.error("Could not get viewer's figure canvas.");
        }

        return null;
    }

    private void addFigureToViewport(final Figure f) {
        final Viewport v = getViewport();

        if (v != null) {
            v.add(f);
        }
    }

    private void removeFigureFromViewport(final Figure f) {
        final Viewport v = getViewport();

        if (v != null) {
            v.remove(f);
        }
    }


    static private class TranslucentRectangle extends RectangleFigure {
        @Override
        public void paint(final Graphics graphics) {
            graphics.setAlpha(MESSAGE_BACKGROUND_OPACITY);
            super.paint(graphics);
        }
    }
}
