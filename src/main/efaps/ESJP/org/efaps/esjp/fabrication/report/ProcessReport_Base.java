/*
 * Copyright 2003 - 2015 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.efaps.esjp.fabrication.report;

import java.awt.Color;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.user.Role;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.fabrication.util.Fabrication;
import org.efaps.esjp.products.Cost;
import org.efaps.ui.wicket.models.EmbeddedLink;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.ComponentColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.GenericElementBuilder;
import net.sf.dynamicreports.report.builder.component.VerticalListBuilder;
import net.sf.dynamicreports.report.builder.expression.AbstractComplexExpression;
import net.sf.dynamicreports.report.builder.grid.ColumnTitleGroupBuilder;
import net.sf.dynamicreports.report.builder.style.ConditionalStyleBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("a6137d37-099f-4915-9945-4a277671c75e")
@EFapsApplication("eFapsApp-Fabrication")
public abstract class ProcessReport_Base
    extends AbstractCommon
{
    /**
     * Logging instance used in this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ProcessReport.class);

    /** The currency instance. */
    private Instance currencyInstance;

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing html snipplet
     * @throws EFapsException on error
     */
    public Return generateReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getReport(_parameter);
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing the file
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(getDBProperty("FileName"));
        File file = null;
        if ("xls".equalsIgnoreCase(mime)) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase(mime)) {
            file = dyRp.getPDF(_parameter);
        }
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }

    /**
     * Gets the values.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the values
     * @throws EFapsException on error
     */
    public ValuesBean getValues(final Parameter _parameter)
        throws EFapsException
    {
        final Instance currInst = Fabrication.CURRENCY4PROCESSREPORT.get();
        this.currencyInstance = currInst.isValid() ? currInst : Currency.getBaseCurrency();

        final ValuesBean ret = new ValuesBean();

        final Instance processInst = _parameter.getInstance();
        final PrintQuery print = new PrintQuery(processInst);
        print.addAttribute(CIFabrication.ProcessAbstract.Date);
        print.execute();
        final DateTime date = print.getAttribute(CIFabrication.ProcessAbstract.Date);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);

        final QueryBuilder oAttrQueryBldr = new QueryBuilder(CISales.ProductionOrder);
        oAttrQueryBldr.addType(CISales.UsageReport, CISales.ProductionReport);
        oAttrQueryBldr.addWhereAttrNotEqValue(CISales.DocumentStockAbstract.StatusAbstract,
                        Status.find(CISales.ProductionOrderStatus.Canceled));
        oAttrQueryBldr.addWhereAttrNotEqValue(CISales.DocumentStockAbstract.StatusAbstract,
                        Status.find(CISales.UsageReportStatus.Canceled));
        oAttrQueryBldr.addWhereAttrNotEqValue(CISales.DocumentStockAbstract.StatusAbstract,
                        Status.find(CISales.ProductionReportStatus.Canceled));

        final QueryBuilder pAttrQueryBldr = new QueryBuilder(CIFabrication.Process2DocumentAbstract);
        pAttrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2DocumentAbstract.FromLinkAbstract, processInst);
        pAttrQueryBldr.addWhereAttrInQuery(CIFabrication.Process2DocumentAbstract.ToLinkAbstract,
                        oAttrQueryBldr.getAttributeQuery(CISales.DocumentStockAbstract.ID));

        queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                        pAttrQueryBldr.getAttributeQuery(CIFabrication.Process2DocumentAbstract.ToLinkAbstract));

        add2QueryBldr(_parameter, queryBldr);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder docSel = SelectBuilder.get().linkto(CISales.PositionAbstract.DocumentAbstractLink);
        final SelectBuilder docInstSel = new SelectBuilder(docSel).instance();
        final SelectBuilder prodSel = SelectBuilder.get().linkto(CISales.PositionAbstract.Product);
        final SelectBuilder prodInstSel = new SelectBuilder(prodSel).instance();
        final SelectBuilder prodNameSel = new SelectBuilder(prodSel).attribute(CIProducts.ProductAbstract.Name);
        final SelectBuilder prodDescSel = new SelectBuilder(prodSel)
                        .attribute(CIProducts.ProductAbstract.Description);
        final SelectBuilder prodDimSel = new SelectBuilder(prodSel).attribute(CIProducts.ProductAbstract.Dimension);
        multi.addSelect(docInstSel, prodInstSel, prodNameSel, prodDescSel, prodDimSel);
        multi.addAttribute(CISales.PositionAbstract.Quantity);
        multi.execute();
        while (multi.next()) {
            final Instance docInst = multi.getSelect(docInstSel);
            final Instance prodInst = multi.getSelect(prodInstSel);
            final BigDecimal quantity = multi.getAttribute(CISales.PositionAbstract.Quantity);
            if (docInst.getType().isKindOf(CISales.ProductionOrder.getType())
                            || docInst.getType().isKindOf(CISales.ProductionReport.getType())) {
                final DataBean paraBean;
                if (ret.getParaMap().containsKey(prodInst)) {
                    paraBean = ret.getParaMap().get(prodInst);
                } else {
                    paraBean = getDataBean(_parameter);
                    ret.getParaMap().put(prodInst, paraBean);
                    paraBean.setCurrencyInst(getCurrencyInstance()).setProdInstance(prodInst);
                    paraBean.setProdName(multi.<String>getSelect(prodNameSel));
                    paraBean.setProdDescription(multi.<String>getSelect(prodDescSel));
                    paraBean.setProdDimension(Dimension.get(multi.<Long>getSelect(prodDimSel)));
                }
                if (docInst.getType().isKindOf(CISales.ProductionReport.getType())) {
                    paraBean.addFabrication(quantity);
                } else {
                    paraBean.addOrder(quantity);
                }
                final QueryBuilder bomQueryBldr = new QueryBuilder(CIProducts.ProductionBOM);
                bomQueryBldr.addWhereAttrEqValue(CIProducts.ProductionBOM.From, prodInst);
                final MultiPrintQuery bomMulti = bomQueryBldr.getPrint();
                final SelectBuilder matSel = SelectBuilder.get().linkto(CIProducts.ProductionBOM.To);
                final SelectBuilder matInstSel = new SelectBuilder(matSel).instance();
                final SelectBuilder matNameSel = new SelectBuilder(matSel)
                                .attribute(CIProducts.ProductAbstract.Name);
                final SelectBuilder matDescSel = new SelectBuilder(matSel)
                                .attribute(CIProducts.ProductAbstract.Description);
                final SelectBuilder matDimSel = new SelectBuilder(matSel)
                                .attribute(CIProducts.ProductAbstract.Dimension);
                bomMulti.addSelect(matInstSel, matNameSel, matDescSel, matDimSel);
                bomMulti.addAttribute(CIProducts.ProductionBOM.Quantity, CIProducts.ProductionBOM.UoM);
                bomMulti.execute();
                while (bomMulti.next()) {
                    final DataBean bean;
                    final Instance matInst = bomMulti.getSelect(matInstSel);
                    if (ret.getDataMap().containsKey(matInst)) {
                        bean = ret.getDataMap().get(matInst);
                        bean.setParentProdInst(prodInst);
                    } else {
                        bean = getDataBean(_parameter);
                        ret.getDataMap().put(matInst, bean);
                        bean.setCurrencyInst(getCurrencyInstance()).setParentProdInst(prodInst);
                        bean.setDate(date);
                        bean.setProdInstance(matInst);
                        bean.setProdName(bomMulti.<String>getSelect(matNameSel));
                        bean.setProdDescription(bomMulti.<String>getSelect(matDescSel));
                        bean.setProdDimension(Dimension.get(bomMulti.<Long>getSelect(matDimSel)));
                    }

                    final UoM uom = Dimension.getUoM(bomMulti.<Long>getAttribute(CIProducts.ProductionBOM.UoM));
                    BigDecimal bomQuan = bomMulti.<BigDecimal>getAttribute(CIProducts.ProductionBOM.Quantity);
                    bomQuan = bomQuan.setScale(8, BigDecimal.ROUND_HALF_UP)
                                    .multiply(new BigDecimal(uom.getNumerator()))
                                    .divide(new BigDecimal(uom.getDenominator())).multiply(quantity);
                    if (docInst.getType().isKindOf(CISales.ProductionReport.getType())) {
                        bean.addFabrication(bomQuan);
                    } else {
                        bean.addOrder(bomQuan);
                    }
                }
            } else {
                final DataBean bean;
                if (ret.getDataMap().containsKey(prodInst)) {
                    bean = ret.getDataMap().get(prodInst);
                } else {
                    bean = getDataBean(_parameter);
                    ret.getDataMap().put(prodInst, bean);
                    bean.setCurrencyInst(getCurrencyInstance());
                    bean.setDate(date);
                    bean.setProdInstance(prodInst);
                    bean.setProdName(multi.<String>getSelect(prodNameSel));
                    bean.setProdDescription(multi.<String>getSelect(prodDescSel));
                    bean.setProdDimension(Dimension.get(multi.<Long>getSelect(prodDimSel)));
                }
                bean.addUsage(quantity);
            }
        }
        return ret;
    }

    /**
     * Gets the currency instance.
     *
     * @return the currency instance
     */
    public Instance getCurrencyInstance()
    {
        return this.currencyInstance;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynProcessReport(this);
    }

    /**
     * Gets the data bean.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the data bean
     */
    protected DataBean getDataBean(final Parameter _parameter)
    {
        return new DataBean(_parameter);
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _queryBldr QueryBuilder to add to
     * @throws EFapsException on error
     */
    protected void add2QueryBldr(final Parameter _parameter,
                                 final QueryBuilder _queryBldr)
        throws EFapsException
    {

    }

    /**
     * The Class DynProcessReport.
     */
    public static class DynProcessReport
        extends AbstractDynamicReport
    {

        /** The reportContainer. */
        private final ProcessReport_Base reportContainer;

        /**
         * Instantiates a new dyn process report.
         *
         * @param _reportContainer the report
         * @throws EFapsException on error
         */
        public DynProcessReport(final ProcessReport_Base _reportContainer)
            throws EFapsException
        {
            this.reportContainer = _reportContainer;
        }

        /**
         * Gets the report conatiner.
         *
         * @return the report conatiner
         */
        protected ProcessReport_Base getReportContainer()
        {
            return this.reportContainer;
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final ValuesBean values = getReportContainer().getValues(_parameter);

            final List<DataBean> datasource = new ArrayList<>(values.getDataMap().values());
            Collections.sort(datasource, new Comparator<DataBean>()
            {

                @Override
                public int compare(final DataBean _arg0,
                                   final DataBean _arg1)
                {
                    return _arg0.getProdName().compareTo(_arg1.getProdName());
                }
            });

            createTitle(_parameter, values.getParaMap().values());

            createSummary(_parameter, values);

            return new JRBeanCollectionDataSource(datasource);
        }

        /**
         * Creates the title.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _beans the beans
         * @throws EFapsException on error
         */
        protected void createTitle(final Parameter _parameter,
                                   final Collection<DataBean> _beans)
            throws EFapsException
        {
            final StringBuilder order = new StringBuilder().append(CISales.ProductionOrder.getType().getLabel())
                            .append(": ");
            final StringBuilder fabrication = new StringBuilder()
                            .append(CISales.ProductionReport.getType().getLabel()).append(": ");
            for (final DataBean bean : _beans) {

                if (bean.getOrderQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    order.append(bean.getOrderQuantity()).append(bean.getProdUoM()).append(" ")
                                    .append(bean.getProdName()).append(" ").append(bean.getProdDescription());
                }
                if (bean.getFabricatedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    fabrication.append(bean.getFabricatedQuantity()).append(bean.getProdUoM()).append(" ")
                                    .append(bean.getProdName()).append(" ").append(bean.getProdDescription());
                }
            }
            final StyleBuilder style = DynamicReports.stl.style().setBold(true).setFontSize(14);
            getReport().addTitle(
                            DynamicReports.cmp.verticalList(DynamicReports.cmp.text(order.toString()).setStyle(style),
                                            DynamicReports.cmp.text(fabrication.toString()).setStyle(style)));
        }

        /**
         * Creates the summary.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @param _values the values
         * @throws EFapsException on error
         */
        protected void createSummary(final Parameter _parameter,
                                     final ValuesBean _values)
            throws EFapsException
        {
            if (showCost(_parameter)) {
                _values.calculateCost(_parameter);
                final StyleBuilder style = DynamicReports.stl.style().setBold(true).setFontSize(14);
                final VerticalListBuilder vl = DynamicReports.cmp.verticalList();
                for (final DataBean parentBean : _values.getParaMap().values()) {
                    final StringBuilder bldr = new StringBuilder()
                                    .append(parentBean.getUnitCost())
                                    .append(" ").append(parentBean.getProdName()).append(" ")
                                    .append(parentBean.getProdDescription());
                    vl.add(DynamicReports.cmp.text(bldr.toString()).setStyle(style));
                }
                getReport().addSummary(vl);
            }
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final boolean cost = showCost(_parameter);

            final TextColumnBuilder<String> prodNameColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.ProdName"),
                            "prodName", DynamicReports.type.stringType());
            final TextColumnBuilder<String> prodDescriptionColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.ProdDescription"), "prodDescription",
                            DynamicReports.type.stringType());

            final TextColumnBuilder<String> prodUoMColumn = DynamicReports.col.column(getReportContainer()
                           .getDBProperty("Column.ProdUoM"), "prodUoM", DynamicReports.type.stringType());

            final TextColumnBuilder<BigDecimal> orderQuantityColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.OrderQuantity"), "orderQuantity",
                            DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> orderCostColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.OrderCost"), "orderCost", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> usageQuantityColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.UsageQuantity"), "usageQuantity",
                            DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> usageCostColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.UsageCost"), "usageCost", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> fabricatedQuantityColumn = DynamicReports.col.column(
                            getReportContainer().getDBProperty("Column.FabricatedQuantity"),
                            "fabricatedQuantity", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> fabricatedCostColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.FabricatedCost"),
                            "fabricatedCost", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> percentColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.Percent"),
                            "percent", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> differenceColumn = DynamicReports.col.column(getReportContainer()
                            .getDBProperty("Column.Difference"),
                            "difference", DynamicReports.type.bigDecimalType());

            final ColumnTitleGroupBuilder prodGrid = DynamicReports.grid.titleGroup(getReportContainer()
                            .getDBProperty("ColumnGroup.Prod"));
            final ColumnTitleGroupBuilder orderGrid = DynamicReports.grid.titleGroup(getReportContainer()
                            .getDBProperty("ColumnGroup.Order"), orderQuantityColumn);
            final ColumnTitleGroupBuilder usageGrid = DynamicReports.grid.titleGroup(getReportContainer()
                            .getDBProperty("ColumnGroup.Usage"), usageQuantityColumn);
            final ColumnTitleGroupBuilder fabricatedGrid = DynamicReports.grid.titleGroup(getReportContainer()
                            .getDBProperty("ColumnGroup.Fabricated"), fabricatedQuantityColumn);

            final ColumnTitleGroupBuilder analysisGrid = DynamicReports.grid.titleGroup(getReportContainer()
                            .getDBProperty("ColumnGroup.Analysis"), percentColumn, differenceColumn);

            final ConditionalStyleBuilder conditionRed = DynamicReports.stl.conditionalStyle(
                            DynamicReports.cnd.smaller(differenceColumn, 0))
                            .setForegroundColor(Color.RED);
            final ConditionalStyleBuilder conditionGreen = DynamicReports.stl.conditionalStyle(
                            DynamicReports.cnd.greater(differenceColumn, 0))
                            .setForegroundColor(new Color(8, 81, 24));

            final StyleBuilder conditionStyle = DynamicReports.stl.style()
                            .conditionalStyles(conditionRed, conditionGreen)
                            .setBold(true);
            differenceColumn.setStyle(conditionStyle);

            final GenericElementBuilder linkElement = DynamicReports.cmp.genericElement(
                            "http://www.efaps.org", "efapslink")
                            .addParameter(EmbeddedLink.JASPER_PARAMETERKEY, new LinkExpression())
                            .setHeight(12).setWidth(25);
            final ComponentColumnBuilder linkColumn = DynamicReports.col.componentColumn(linkElement).setTitle("");
            if (getExType().equals(ExportType.HTML)) {
                prodGrid.add(linkColumn);
                _builder.addColumn(linkColumn);
            }
            prodGrid.add(prodNameColumn, prodDescriptionColumn, prodUoMColumn);

            _builder.setFloatColumnFooter(true)
                            .columnGrid(prodGrid, orderGrid, fabricatedGrid, usageGrid, analysisGrid);
            if (cost) {
                orderGrid.add(orderCostColumn);
                fabricatedGrid.add(fabricatedCostColumn);
                usageGrid.add(usageCostColumn);
                _builder.addSubtotalAtSummary(DynamicReports.sbt.sum(orderCostColumn))
                                .addSubtotalAtSummary(DynamicReports.sbt.sum(usageCostColumn))
                                .addSubtotalAtSummary(DynamicReports.sbt.sum(fabricatedCostColumn))
                                .addColumn(prodNameColumn, prodDescriptionColumn, prodUoMColumn,
                                                orderQuantityColumn, orderCostColumn,
                                                fabricatedQuantityColumn, fabricatedCostColumn,
                                                usageQuantityColumn, usageCostColumn,
                                                percentColumn, differenceColumn);
            } else {
                _builder.addColumn(prodNameColumn, prodDescriptionColumn, prodUoMColumn,
                                orderQuantityColumn,
                                fabricatedQuantityColumn,
                                usageQuantityColumn,
                                percentColumn, differenceColumn);
            }
        }

        /**
         * Show cost.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @return true, if successful
         * @throws EFapsException on error
         */
        protected boolean showCost(final Parameter _parameter)
            throws EFapsException
        {
            // Fabrication_Admin, Fabrication_Manager
            return Context.getThreadContext().getPerson()
                            .isAssigned(Role.get(UUID.fromString("1e3b0378-cb3b-411e-b4c0-9b88e97a0209")))
                            || Context.getThreadContext().getPerson()
                                            .isAssigned(Role.get(UUID
                                                            .fromString("fe489cb2-94ec-442d-975f-36d0f4fbc589")));
        }
    }

    /**
     * Expression used to render a link for the UserInterface.
     */
    public static class LinkExpression
        extends AbstractComplexExpression<EmbeddedLink>
    {

        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Costructor.
         */
        public LinkExpression()
        {
            addExpression(DynamicReports.field("oid", String.class));
        }

        @Override
        public EmbeddedLink evaluate(final List<?> _values,
                                     final ReportParameters _reportParameters)
        {
            final String oid = (String) _values.get(0);
            return EmbeddedLink.getJasperLink(oid);
        }
    }

    /**
     * The Class ValuesBean.
     */
    public static class ValuesBean
    {

        /** The data map. */
        private final Map<Instance, DataBean> dataMap = new HashMap<>();

        /** The para map. */
        private final Map<Instance, DataBean> paraMap = new HashMap<>();

        /**
         * Getter method for the instance variable {@link #dataMap}.
         *
         * @return value of instance variable {@link #dataMap}
         */
        public Map<Instance, DataBean> getDataMap()
        {
            return this.dataMap;
        }

        /**
         * Getter method for the instance variable {@link #paraMap}.
         *
         * @return value of instance variable {@link #paraMap}
         */
        public Map<Instance, DataBean> getParaMap()
        {
            return this.paraMap;
        }

        /**
         * Calculate cost.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @return the values bean
         * @throws EFapsException on error
         */
        public ValuesBean calculateCost(final Parameter _parameter)
            throws EFapsException
        {
            for (final DataBean parentBean : this.paraMap.values()) {
                for (final DataBean bomBean : this.dataMap.values()) {
                    parentBean.addCost(bomBean.getUsageCost());
                }
            }
            return this;
        }
    }

    /**
     * The Class DataBean.
     */
    public static class DataBean
    {

        /** The date. */
        private DateTime date;

        /** The prod dimension. */
        private Dimension prodDimension;

        /** The prod description. */
        private String prodDescription;

        /** The prod name. */
        private String prodName;

        /** The prod inst. */
        private Instance prodInst;

        /** The parent prod inst. */
        private Instance parentProdInst;

        /** The order quantity. */
        private BigDecimal orderQuantity = BigDecimal.ZERO;

        /** The usage quantity. */
        private BigDecimal usageQuantity = BigDecimal.ZERO;

        /** The fabricated quantity. */
        private BigDecimal fabricatedQuantity = BigDecimal.ZERO;

        /** The currency inst. */
        private Instance currencyInst;

        /** The cost. */
        private BigDecimal cost = BigDecimal.ZERO;

        /** The init. */
        private boolean init;

        /** The parameter. */
        private Parameter parameter;

        /**
         * Instantiates a new data bean.
         *
         * @param _parameter Parameter as passed by the eFaps API
         */
        public DataBean(final Parameter _parameter)
        {
            this.parameter = _parameter;
        }

        /**
         * Initialize.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @throws EFapsException on error
         */
        protected void initialize(final Parameter _parameter)
            throws EFapsException
        {
            if (!this.init) {
                this.cost =  Cost.getCost4Currency(_parameter, getDate(), getProdInst(), getCurrencyInst());
                this.init = true;
            }
        }

        /**
         * Gets the prod uo m.
         *
         * @return the prod uo m
         */
        public String getProdUoM()
        {
            return getProdDimension().getBaseUoM().getName();
        }

        /**
         * Gets the oid.
         *
         * @return the oid
         */
        public String getOid()
        {
            return getProdInst() == null ? null : this.getProdInst().getOid();
        }

        /**
         * Sets the prod instance.
         *
         * @param _prodInst the prod inst
         * @return the data bean
         */
        public DataBean setProdInstance(final Instance _prodInst)
        {
            this.prodInst = _prodInst;
            return this;
        }

        /**
         * Adds the order.
         *
         * @param _orderQuantity the order quantity
         * @return the data bean
         */
        public DataBean addOrder(final BigDecimal _orderQuantity)
        {
            this.orderQuantity = this.orderQuantity.add(_orderQuantity);
            return this;
        }

        /**
         * Adds the usage.
         *
         * @param _usageQuantity the usage quantity
         * @return the data bean
         */
        public DataBean addUsage(final BigDecimal _usageQuantity)
        {
            this.usageQuantity = this.usageQuantity.add(_usageQuantity);
            return this;
        }

        /**
         * Adds the fabrication.
         *
         * @param _fabricatedQuantity the fabricated quantity
         * @return the data bean
         */
        public DataBean addFabrication(final BigDecimal _fabricatedQuantity)
        {
            this.fabricatedQuantity = this.fabricatedQuantity.add(_fabricatedQuantity);
            return this;
        }

        /**
         * Sets the prod description.
         *
         * @param _prodDescription the prod description
         * @return the data bean
         */
        public DataBean setProdDescription(final String _prodDescription)
        {
            this.prodDescription = _prodDescription;
            return this;
        }

        /**
         * Sets the prod name.
         *
         * @param _prodName the prod name
         * @return the data bean
         */
        public DataBean setProdName(final String _prodName)
        {
            this.prodName = _prodName;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #prodInst}.
         *
         * @return value of instance variable {@link #prodInst}
         */
        public Instance getProdInst()
        {
            return this.prodInst;
        }

        /**
         * Setter method for instance variable {@link #prodInst}.
         *
         * @param _prodInst value for instance variable {@link #prodInst}
         * @return the data bean
         */
        public DataBean setProdInst(final Instance _prodInst)
        {
            this.prodInst = _prodInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #prodInst}.
         *
         * @return value of instance variable {@link #prodInst}
         */
        public Instance getCurrencyInst()
        {
            return this.currencyInst;
        }

        /**
         * Setter method for instance variable {@link #prodInst}.
         *
         * @param _prodInst value for instance variable {@link #prodInst}
         * @return the data bean
         */
        public DataBean setCurrencyInst(final Instance _prodInst)
        {
            this.currencyInst = _prodInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #prodDescription}.
         *
         * @return value of instance variable {@link #prodDescription}
         */
        public String getProdDescription()
        {
            return this.prodDescription;
        }

        /**
         * Getter method for the instance variable {@link #prodName}.
         *
         * @return value of instance variable {@link #prodName}
         */
        public String getProdName()
        {
            return this.prodName;
        }

        /**
         * Getter method for the instance variable {@link #orderQuantity}.
         *
         * @return value of instance variable {@link #orderQuantity}
         */
        public BigDecimal getOrderQuantity()
        {
            return this.orderQuantity;
        }

        /**
         * Getter method for the instance variable {@link #orderQuantity}.
         *
         * @return value of instance variable {@link #orderQuantity}
         * @throws EFapsException on error
         */
        public BigDecimal getOrderCost()
            throws EFapsException
        {
            return getOrderQuantity().multiply(getCost());
        }

        /**
         * Setter method for instance variable {@link #orderQuantity}.
         *
         * @param _orderQuantity value for instance variable
         *            {@link #orderQuantity}
         * @return the data bean
         */
        public DataBean setOrderQuantity(final BigDecimal _orderQuantity)
        {
            this.orderQuantity = _orderQuantity;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #usageQuantity}.
         *
         * @return value of instance variable {@link #usageQuantity}
         */
        public BigDecimal getUsageQuantity()
        {
            return this.usageQuantity;
        }

        /**
         * Getter method for the instance variable {@link #usageQuantity}.
         *
         * @return value of instance variable {@link #usageQuantity}
         * @throws EFapsException on error
         */
        public BigDecimal getUsageCost()
            throws EFapsException
        {
            return getUsageQuantity().multiply(getCost());
        }

        /**
         * Setter method for instance variable {@link #usageQuantity}.
         *
         * @param _usageQuantity value for instance variable
         *            {@link #usageQuantity}
         * @return the data bean
         */
        public DataBean setUsageQuantity(final BigDecimal _usageQuantity)
        {
            this.usageQuantity = _usageQuantity;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #date}.
         *
         * @return value of instance variable {@link #date}
         */
        public DateTime getDate()
        {
            return this.date;
        }

        /**
         * Setter method for instance variable {@link #date}.
         *
         * @param _date value for instance variable {@link #date}
         * @return the data bean
         */
        public DataBean setDate(final DateTime _date)
        {
            this.date = _date;
            return this;
        }

        /**
         * Gets the unit cost.
         *
         * @return the unit cost
         * @throws EFapsException on error
         */
        public BigDecimal getUnitCost()
            throws EFapsException
        {
            BigDecimal div = getFabricatedQuantity();
            if (div.compareTo(BigDecimal.ZERO) == 0) {
                div = BigDecimal.ONE;
            }
            return getCost().divide(div, BigDecimal.ROUND_HALF_UP);
        }

        /**
         * Getter method for the instance variable {@link #cost}.
         *
         * @return value of instance variable {@link #cost}
         * @throws EFapsException on error
         */
        public BigDecimal getCost()
            throws EFapsException
        {
            initialize(getParameter());
            return this.cost;
        }

        /**
         * Getter method for the instance variable {@link #cost}.
         *
         * @param _cost the cost
         * @return value of instance variable {@link #cost}
         * @throws EFapsException on error
         */
        public DataBean addCost(final BigDecimal _cost)
            throws EFapsException
        {
            this.init = true;
            this.cost = this.cost.add(_cost);
            return this;
        }

        /**
         * Gets the percent.
         *
         * @return the percent
         * @throws EFapsException on error
         */
        public BigDecimal getPercent()
            throws EFapsException
        {
            BigDecimal order = getFabricatedQuantity();
            if (order.compareTo(BigDecimal.ZERO) == 0) {
                order = BigDecimal.ONE;
            }
            return new BigDecimal(100).setScale(8)
                            .divide(order, BigDecimal.ROUND_HALF_UP)
                            .multiply(getUsageQuantity()).setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        /**
         * Gets the difference.
         *
         * @return the difference
         * @throws EFapsException on error
         */
        public BigDecimal getDifference()
            throws EFapsException
        {
            return getFabricatedQuantity().subtract(getUsageQuantity());
        }

        /**
         * Setter method for instance variable {@link #cost}.
         *
         * @param _cost value for instance variable {@link #cost}
         * @return the data bean
         */
        public DataBean setCost(final BigDecimal _cost)
        {
            this.cost = _cost;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #fabricatedQuantity}.
         *
         * @return value of instance variable {@link #fabricatedQuantity}
         */
        public BigDecimal getFabricatedQuantity()
        {
            return this.fabricatedQuantity;
        }

        /**
         * Setter method for instance variable {@link #fabricatedQuantity}.
         *
         * @param _fabricatedQuantity value for instance variable
         *            {@link #fabricatedQuantity}
         * @return the data bean
         */
        public DataBean setFabricatedQuantity(final BigDecimal _fabricatedQuantity)
        {
            this.fabricatedQuantity = _fabricatedQuantity;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #usageQuantity}.
         *
         * @return value of instance variable {@link #usageQuantity}
         * @throws EFapsException on error
         */
        public BigDecimal getFabricatedCost()
            throws EFapsException
        {
            return getFabricatedQuantity().multiply(getCost());
        }

        /**
         * Getter method for the instance variable {@link #prodDimension}.
         *
         * @return value of instance variable {@link #prodDimension}
         */
        public Dimension getProdDimension()
        {
            return this.prodDimension;
        }

        /**
         * Setter method for instance variable {@link #prodDimension}.
         *
         * @param _prodDimension value for instance variable
         *            {@link #prodDimension}
         * @return the data bean
         */
        public DataBean setProdDimension(final Dimension _prodDimension)
        {
            this.prodDimension = _prodDimension;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #parentProdInst}.
         *
         * @return value of instance variable {@link #parentProdInst}
         */
        public Instance getParentProdInst()
        {
            return this.parentProdInst;
        }

        /**
         * Setter method for instance variable {@link #parentProdInst}.
         *
         * @param _parentProdInst value for instance variable
         *            {@link #parentProdInst}
         * @return the data bean
         */
        public DataBean setParentProdInst(final Instance _parentProdInst)
        {
            this.parentProdInst = _parentProdInst;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #parameter}.
         *
         * @return value of instance variable {@link #parameter}
         */
        public Parameter getParameter()
        {
            return this.parameter;
        }

        /**
         * Setter method for instance variable {@link #parameter}.
         *
         * @param _parameter value for instance variable {@link #parameter}
         * @return the data bean
         */
        public DataBean setParameter(final Parameter _parameter)
        {
            this.parameter = _parameter;
            return this;
        }
    }
}
