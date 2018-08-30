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
 * ---------------------------------------------------------------------
 *
 * Created on 2013.11.09. by Gabor Bakos
 */
package org.knime.base.node.stats.viz.extended;

import java.awt.Component;
import java.awt.Container;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLEditorKit.HTMLFactory;
import javax.swing.text.html.ObjectView;

import org.knime.base.data.statistics.HistogramColumn;
import org.knime.base.data.statistics.HistogramColumn.BinNumberSelectionStrategy;
import org.knime.base.data.statistics.HistogramModel;
import org.knime.base.data.statistics.Statistics3Table;
import org.knime.base.node.util.DoubleFormat;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataColumnSpec;
import org.knime.core.data.DataValue;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.RowKey;
import org.knime.core.node.NodeView;
import org.knime.core.node.property.hilite.HiLiteHandler;

/**
 * A view with HTML tables, so it can be copied to other programs.
 *
 * @author Gabor Bakos
 */
class ExtendedStatisticsHTMLNodeView extends NodeView<ExtendedStatisticsNodeModel> {

    private JEditorPane m_numeric;

    private JEditorPane m_nominal = new JEditorPane("text/html", "<html></html>");

    private JEditorPane m_topBottom = new JEditorPane("text/html", "<html></html>");

    private JTabbedPane m_tabs;

    /** The vertical alignment in the rows. */
    private static final String ROW_VERTICAL_ALIGN = "top";

    /**
     * @param nodeModel The initial {@link ExtendedStatisticsNodeModel}.
     */
    ExtendedStatisticsHTMLNodeView(final ExtendedStatisticsNodeModel nodeModel) {
        super(nodeModel);
        m_numeric = new JEditorPane("text/html", "<html></html>");
        m_numeric.setEditable(false);
        m_tabs = new JTabbedPane();
        m_tabs.add("Numeric", new JScrollPane(m_numeric));
        m_tabs.add("Nominal", new JScrollPane(m_nominal));
        m_tabs.add("Top/bottom", new JScrollPane(m_topBottom));
        setComponent(m_tabs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClose() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onOpen() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modelChanged() {
        ExtendedStatisticsNodeModel model = getNodeModel();
        final Statistics3Table statTable = model.getStatTable();
        if (statTable == null) {
            m_numeric.setText("");
            m_nominal.setText("");
            m_topBottom.setText("");
        } else {
            //            m_numeric.setEditorKitForContentType("text/html", new NumericEditorKit(model));
            m_numeric.setEditorKit(new NumericEditorKit(model, statTable));
            m_numeric.setEditable(false);
            m_numeric.setText(createTable(statTable));
            m_nominal.setEditorKit(new NominalEditorKit(model, statTable));
            m_nominal.setEditable(false);
            m_nominal.setText(createNominal(statTable));
            m_topBottom.setText(createTopBottom(statTable));
        }
        m_numeric.revalidate();
        m_nominal.revalidate();
        m_topBottom.revalidate();
    }

    /**
     * @param statTable
     * @return
     */
    private String createTopBottom(final Statistics3Table statTable) {
        final List<String> columnNames = new ArrayList<>();
        final List<Map<DataCell, Integer>> nominals = new ArrayList<>();
        if (statTable != null) {
            int colIdx = 0;
            for (DataColumnSpec spec : statTable.getSpec()) {
                if (statTable.getNominalValues(colIdx) != null) {
                    columnNames.add(spec.getName());
                    nominals.add(statTable.getNominalValues(colIdx));
                }
                ++colIdx;
            }
        }

        if (columnNames.isEmpty()) {
            return "";
        }

        StringBuilder buffer = new StringBuilder();
        buffer.append("<html>\n");
        buffer.append("<body>\n");
        buffer.append("<p>&nbsp</p>");
        buffer.append("<table border = \"1\">");

        for (int i = 0; i < columnNames.size(); i++) {
            if (nominals.get(i) != null) {
                buffer.append("<th style=\"white-space: nowrap\">"
                        + columnNames.get(i) + "</th>");
            }
        }

        buffer.append("<tr valign=\"top\">");
        int[] missings = statTable.getNumberMissingValues();
        for (int i = 0; i < columnNames.size(); i++) {
            if (nominals.get(i) != null) {
                buffer.append("<td style=\"white-space: nowrap\"><strong>"
                        + "No. missings: </strong>"
                        + missings[i] + "</td>");
            }
        }

        buffer.append("</tr><tr valign=\"top\">");

            final int numNomValues = getNodeModel().numOfNominalValues();
            for (Map<DataCell, Integer> map: nominals) {
                if (map != null) {
                    buffer.append("<td nowrap=\"nowrap\">");
                    final int size = map.size();
                    if (size == 0) {
                        buffer.append("<i>contains more than "
                                + getNodeModel().numOfNominalValuesOutput()
                                + " nominal values</i>");
                    } else {
                        int cnt = 0;
                        buffer.append("<strong>Top " + numNomValues
                                + ":</strong><br>");
                    for (Map.Entry<DataCell, Integer> e : map.entrySet()) {
                        buffer.append(e.getKey() + " : " + e.getValue() + "<br>");
                        if (++cnt == numNomValues) {
                            break;
                        }
                    }
                        buffer.append("</td>");
                    }
                }
            }
        buffer.append("</tr>");

        buffer.append("</tr><tr valign=\"top\">");

            for (Map<DataCell, Integer> map: nominals) {
                if (map != null) {
                    buffer.append("<td style=\"white-space: nowrap\">");
                    buffer.append("<strong>Bottom " + numNomValues
                            + ":</strong><br>");
                    final int size = map.size();
                    if (size >= numNomValues) {
                        int cnt = 0;
                        for (DataCell c : map.keySet()) {
                            if (cnt >= Math.max(numNomValues, size - numNomValues)) {
                                buffer.append(c.toString() + " : " + map.get(c) + "<br>");
                            }
                            cnt++;
                        }
                        buffer.append("</td>");
                    }
                }
            }
        buffer.append("</tr>");

        buffer.append("</table>");
        buffer.append("<p>&nbsp</p>");
        buffer.append("</body>\n");
        buffer.append("</html>\n");
        buffer.append("");
        return buffer.toString();
    }

    /**
     * @param statTable non null table from the model
     * @return The HTML version for nominal statistics.
     * @see NominalEditorKit
     */
    private String createNominal(final Statistics3Table statTable) {
        return renderNominal(statTable).toString();
    }

    /**
     * @param statTable non-null table to render
     * @return A {@link StringBuilder} with all the stats added for nominal HTML table.
     */
    private StringBuilder renderNominal(final Statistics3Table statTable) {
        StringBuilder ret = createHtmlHeader();
        ret.append("<body>\n");

        if (null != getNodeModel()) {
            ret.append("<table>\n");
            ret.append("<tr>");
            for (Iterator<String> iter = Arrays.asList("Column", "No. missings", "Histogram").iterator(); iter
                .hasNext();) {
                ret.append("<th class=\"left\">");
                String prop = iter.next();
                ret.append(prop);
                ret.append("</th>");
            }
            ret.append("</tr>");

            int i = 0, colIdx = 0;
            if (statTable != null) {
                for (DataColumnSpec spec : statTable.getSpec()) {
                    if (statTable.getNominalValues(colIdx) != null) {
                        String cssClass = i % 2 == 0 ? "even" : "odd";
                        int columnIndex = statTable.getSpec().findColumnIndex(spec.getName());
                        renderNominalRow(statTable, columnIndex, ret, cssClass);
                        i++;
                    }
                    ++colIdx;
                }
            }
            ret.append("</table>\n");

        } else {
            ret.append("No data available.\n");
        }

        ret.append("</body>\n");
        ret.append("</html>\n");
        return ret;
    }

    /**
     * @param Statistics3Table Table created in {@link ExtendedStatisticsNodeModel}.
     * @return The HTML version for numeric statistics.
     * @see NumericEditorKit
     */
    private String createTable(final Statistics3Table statTable) {
        return renderTable(statTable).toString();
    }

    /** Convenient method to create HTML Header. */
    private StringBuilder createHtmlHeader() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("<html>\n");
        buffer.append("<head>\n");
        buffer.append("<style type=\"text/css\">\n");
        buffer.append("body {color:#333333;}");
        buffer.append("table {width: 100%;margin: 7px 0 7px 0;}");
        buffer.append("th {font-weight: bold;background-color: #d3d3d3;" + "vertical-align: bottom;}");
        buffer.append("td {padding: 4px 10px 4px 10px;}");
        buffer.append("th {padding: 4px 10px 4px 10px;}");
        buffer.append(".left {text-align: left}");
        buffer.append(".right {text-align: right}");
        buffer.append(".numeric {text-align: right}");
        buffer.append(".odd {background-color:#ddeeff;}");
        buffer.append(".even {background-color:#ffffff;}");
        buffer.append("</style>\n");
        buffer.append("</head>\n");
        return buffer;
    }

    /** Escape special html characters. */
    private String escapeHtml(final String str) {
        // escape the quote character
        String s = str.replace("&", "&amp;");
        // escape lower than
        s = s.replace("<", "&lt;");
        // escape greater than
        s = s.replace(">", "&gt;");
        // escape quote character
        s = s.replace("\"", "&quot;");
        return s;
    }

    /**
     * Create HTML of the cross tabulation.
     *
     * @param statTable The non-null table created in {@link ExtendedStatisticsNodeModel}.
     */
    private String renderTable(final Statistics3Table statTable) {
        List<String> props =
            Arrays.asList("Column", "Min", "Mean", "Median", "Max", "Std. Dev.", "Skewness", "Kurtosis", "No. Missing",
                "No. +\u221E", "No. -\u221E", "Histogram");
        StringBuilder buffer = createHtmlHeader();
        buffer.append("<body>\n");

        if (null != getNodeModel() && getNodeModel().getStatTable() != null) {
            buffer.append("<table>\n");
            buffer.append("<tr>");
            for (Iterator<String> iter = props.iterator(); iter.hasNext();) {
                buffer.append("<th class=\"left\" nowrap>");
                String prop = iter.next();
                buffer.append(prop);
                buffer.append("</th>");
            }
            buffer.append("</tr>");

            int i = 0;
            for (DataColumnSpec spec : statTable.getSpec()) {
                if (spec.getType().isCompatible(DoubleValue.class)) {
                    String cssClass = i % 2 == 0 ? "even" : "odd";
                    int columnIndex = statTable.getSpec().findColumnIndex(spec.getName());
                    renderRow(statTable, columnIndex, buffer, cssClass);
                }
                i++;
            }
            buffer.append("</table>\n");

        } else {
            buffer.append("No data available.\n");
        }

        buffer.append("</body>\n");
        buffer.append("</html>\n");
        return buffer.toString();
    }

    /** Create HTML for the given row. */
    private void renderNominalRow(final Statistics3Table statTable, final int row, final StringBuilder buffer,
        final String cssClass) {
        buffer.append("<tr class=\"" + cssClass + "\">\n");
        buffer.append("<td valign=\"" + ROW_VERTICAL_ALIGN + "\">");
        buffer.append(escapeHtml(statTable.getSpec().getColumnSpec(row).getName()));
        buffer.append("</td>");
        buffer.append("<td class=\"numeric\" valign=\"" + ROW_VERTICAL_ALIGN + "\">");
        buffer.append(NumberFormat.getInstance().format((long)statTable.getNumberMissingValues(row)));
        buffer.append("</td>\n");
        buffer
            .append(
                "<td><object classid=\"org.knime.base.data.statistics.HistogramColumn.HistogramComponent\" colId=\"")
            .append(row).append("\" width=\"").append(getNodeModel().getHistogramWidth()).append("\" height=\"")
            .append(getNodeModel().getHistogramHeight()).append("\"></object></td>");
        buffer.append("</tr>\n");
    }

    /** Create HTML for the given row. */
    private void renderRow(final Statistics3Table statTable, final int row, final StringBuilder buffer,
        final String cssClass) {
        //        boolean first = true;
        buffer.append("<tr class=\"" + cssClass + "\">\n");
        buffer.append("<td valign=\"" + ROW_VERTICAL_ALIGN + "\">");
        buffer.append(escapeHtml(statTable.getSpec().getColumnSpec(row).getName()));
        buffer.append("</td>");
        for (double v : new double[]{statTable.getMin()[row], statTable.getMean(row), statTable.getMedian(row),
            statTable.getMax()[row], statTable.getStandardDeviation(row), statTable.getSkewness(row), statTable.getKurtosis(row),}) {
            buffer.append("<td class=\"numeric\" valign=\"" + ROW_VERTICAL_ALIGN + "\">");
            buffer.append(Double.isNaN(v) ? "?" : DoubleFormat.formatDouble(v));
            buffer.append("</td>\n");
        }
        NumberFormat nf = NumberFormat.getInstance();
        for (int v : new int[]{statTable.getNumberMissingValues()[row], statTable.getNumberPositiveInfiniteValues(row),
            statTable.getNumberNegativeInfiniteValues(row),}) {
            buffer.append("<td class=\"numeric\" valign=\"" + ROW_VERTICAL_ALIGN + "\">");
            buffer.append(nf.format(v));
            buffer.append("</td>\n");
        }
        buffer
            .append(
                "<td><object classid=\"org.knime.base.data.statistics.HistogramColumn.HistogramComponent\" colId=\"")
            .append(row).append("\" width=\"").append(getNodeModel().getHistogramWidth()).append("\" height=\"")
            .append(getNodeModel().getHistogramHeight()).append("\"></object></td>");
        buffer.append("</tr>\n");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Container getExportComponent() {
        if (getNodeModel() != null) {
            switch (m_tabs.getSelectedIndex()) {
                case -1:
                    return new JPanel();
                case 0: /*numeric*/
                    return m_numeric;
                case 1: /*nominal*/
                    return m_nominal;
            }
            return m_numeric;
        }
        return new JPanel();
    }

    /**
     * An {@link HTMLEditorKit} that can handle numeric histograms.
     *
     * Idea from: https://weblogs.java.net/blog/aim/archive/2007/07/embedding_swing.html
     */
    private static class NumericEditorKit extends HTMLEditorKit {
        private static final long serialVersionUID = 5445175528764219449L;

        private final ExtendedStatisticsNodeModel m_model;

        private final Statistics3Table m_statTable;

        /**
         * @param model The {@link ExtendedStatisticsNodeModel} to use.
         * @param statTable TODO
         */
        public NumericEditorKit(final ExtendedStatisticsNodeModel model, final Statistics3Table statTable) {
            m_model = model;
            m_statTable = statTable;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ViewFactory getViewFactory() {
            return new NumericViewFactory(m_model, m_statTable);
        }
    }

    /**
     * An {@link HTMLEditorKit} that can handle nominal histograms.
     *
     * Idea from: https://weblogs.java.net/blog/aim/archive/2007/07/embedding_swing.html
     */
    private static class NominalEditorKit extends HTMLEditorKit {
        private static final long serialVersionUID = -2348172161574057315L;

        private final ExtendedStatisticsNodeModel m_model;
        private final Statistics3Table m_statTable;

        /**
         * @param model The {@link ExtendedStatisticsNodeModel} to use.
         * @param statTable Non-null table from model
         */
        public NominalEditorKit(final ExtendedStatisticsNodeModel model, final Statistics3Table statTable) {
            m_model = model;
            m_statTable = statTable;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public ViewFactory getViewFactory() {
            return new NominalViewFactory(m_model, m_statTable);
        }
    }

    /**
     * Abstract base class for the nominal and numeric {@link HTMLFactory}s.
     */
    private static abstract class AbstractViewFactory extends HTMLFactory {
        private final ExtendedStatisticsNodeModel m_model;
        private final Statistics3Table m_statTable;

        /**
         * @param model
         * @param statTable TODO
         */
        public AbstractViewFactory(final ExtendedStatisticsNodeModel model, final Statistics3Table statTable) {
            this.m_model = model;
            m_statTable = statTable;
        }

        /**
         * @return the model
         */
        protected ExtendedStatisticsNodeModel getModel() {
            return m_model;
        }

        /**
         * @return the statTable
         */
        Statistics3Table getStatTable() {
            return m_statTable;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View create(final Element elem) {
            AttributeSet attrs = elem.getAttributes();
            if (attrs.getAttribute(StyleConstants.NameAttribute) == HTML.Tag.OBJECT) {
                final int colId = Integer.parseInt((String)attrs.getAttribute("colid"));
                final HistogramColumn hc =
                    HistogramColumn.getDefaultInstance().withHistogramWidth(m_model.getHistogramWidth().getIntValue())
                        .withHistogramHeight(m_model.getHistogramHeight().getIntValue())
                        .withBinSelectionStrategy(BinNumberSelectionStrategy.DecimalRange)
                        .withShowMinMax(m_model.getShowMinMax().getBooleanValue());
                return new ObjectView(elem) {
                    /**
                     * {@inheritDoc}
                     */
                    @Override
                    protected Component createComponent() {
                        return AbstractViewFactory.this.createComponent(colId, hc);
                    }
                };
            }
            Element parent = elem.getParentElement(), grandParent = parent == null ? null : parent.getParentElement();
            if (attrs.getAttribute(StyleConstants.NameAttribute) == HTML.Tag.TH
                || (parent != null && (parent.getAttributes().getAttribute(StyleConstants.NameAttribute) == HTML.Tag.TH || (grandParent != null && grandParent
                    .getAttributes().getAttribute(StyleConstants.NameAttribute) == HTML.Tag.TH)))) {
                View view = super.create(elem);
                return view;
            }
            return super.create(elem);
        }

        /**
         * Constructs the component for the column using the settings from {@code hc}.
         *
         * @param colId The column index.
         * @param hc The {@link HistogramColumn} object.
         * @return The view of the histogram defined by the parameters.
         */
        protected abstract Component createComponent(int colId, HistogramColumn hc);
    }

    /**
     * Numeric histogram view factory.
     */
    private static class NumericViewFactory extends AbstractViewFactory {
        /**
         * @param model {@link ExtendedStatisticsNodeModel} to use.
         * @param statTable Non-null stats table
         */
        public NumericViewFactory(final ExtendedStatisticsNodeModel model, final Statistics3Table statTable) {
            super(model, statTable);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Component createComponent(final int colId, final HistogramColumn hc) {
            final ExtendedStatisticsNodeModel model = getModel();
            if (model == null) {
                return new JLabel("No model");
            }
            final Map<Integer, ?> histograms = model.getHistograms();
            final Map<Integer, Map<Integer, Set<RowKey>>> buckets = model.getBuckets();
            final HiLiteHandler inHiLiteHandler = model.getInHiLiteHandler(0);
            if (histograms == null || buckets == null || inHiLiteHandler == null) {
                return new JLabel("No model");
            }
            return hc.createComponent(histograms.get(colId), model.getHistogramWidth().getIntValue(), model
                .getHistogramHeight().getIntValue(), model.getEnableHiLite().getBooleanValue() ? inHiLiteHandler
                : new HiLiteHandler(), buckets.get(colId), model.numOfNominalValues());
        }
    }

    /**
     * Nominal histogram view factory.
     */
    private static class NominalViewFactory extends AbstractViewFactory {
        /**
         * @param model {@link ExtendedStatisticsNodeModel} to use.
         * @param statTable Non-null stats table from model
         */
        public NominalViewFactory(final ExtendedStatisticsNodeModel model, final Statistics3Table statTable) {
            super(model, statTable);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Component createComponent(final int colId, final HistogramColumn hc) {
            ExtendedStatisticsNodeModel model = getModel();
            final HistogramModel<?> nominalModel =
                hc.fromNominalModel(getStatTable().getNominalValues(colId), colId, getStatTable().getSpec()
                    .getColumnSpec(colId).getName());
            nominalModel.setRowCount(getStatTable().getRowCount());
            Map<DataValue, Set<RowKey>> values = model.getNominalKeys().get(colId);
            if (values == null) {
                values = Collections.emptyMap();
            }
            Map<Integer, Set<RowKey>> rowKeys = new LinkedHashMap<Integer, Set<RowKey>>();
            for (Entry<DataValue, Set<RowKey>> entry : values.entrySet()) {
                int bin = nominalModel.findBin(entry.getKey());
                rowKeys.put(bin, entry.getValue());
            }
            return hc.createComponent(nominalModel, model.getHistogramWidth().getIntValue(), model.getHistogramHeight()
                .getIntValue(), model.getEnableHiLite().getBooleanValue() ? model.getInHiLiteHandler(0)
                : new HiLiteHandler(), rowKeys, model.numOfNominalValues());
        }
    }
}
