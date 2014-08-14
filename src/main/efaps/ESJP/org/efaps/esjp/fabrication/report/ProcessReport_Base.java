/*
 * Copyright 2003 - 2014 The eFaps Team
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
 * Revision:        $Rev$
 * Last Changed:    $Date$
 * Last Changed By: $Author$
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

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
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
import org.efaps.ui.wicket.models.EmbeddedLink;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("a6137d37-099f-4915-9945-4a277671c75e")
@EFapsRevision("$Rev$")
public abstract class ProcessReport_Base
    extends AbstractCommon
{

    /**
     * Logging instance used in this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(ProcessReport.class);

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
        dyRp.setFileName(DBProperties.getProperty(ProcessReport.class.getName() + ".FileName"));
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
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynProcessReport();
    }

    public static class DynProcessReport
        extends AbstractDynamicReport
    {

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final Instance processInst = _parameter.getInstance();
            final PrintQuery print = new PrintQuery(processInst);
            print.addAttribute(CIFabrication.ProcessAbstract.Date);
            print.execute();
            final DateTime date = print.getAttribute(CIFabrication.ProcessAbstract.Date);

            final Map<Instance, DataBean> dataMap = new HashMap<>();
            final Map<Instance, DataBean> paraMap = new HashMap<>();
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
                final BigDecimal quantity  = multi.getAttribute(CISales.PositionAbstract.Quantity);
                if (docInst.getType().isKindOf(CISales.ProductionOrder.getType())
                                || docInst.getType().isKindOf(CISales.ProductionReport.getType())) {
                    final DataBean paraBean;
                    if (paraMap.containsKey(prodInst)) {
                        paraBean = paraMap.get(prodInst);
                    } else {
                        paraBean = getDataBean(_parameter);
                        paraMap.put(prodInst, paraBean);
                        paraBean.setProdInstance(prodInst);
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
                    final SelectBuilder matNameSel = new SelectBuilder(matSel).attribute(CIProducts.ProductAbstract.Name);
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
                        if (dataMap.containsKey(matInst)) {
                            bean = dataMap.get(matInst);
                            bean.setParentProdInst(prodInst);
                        } else {
                            bean = getDataBean(_parameter);
                            dataMap.put(matInst, bean);
                            bean.setParentProdInst(prodInst);
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
                    if (dataMap.containsKey(prodInst)) {
                        bean = dataMap.get(prodInst);
                    } else {
                        bean = getDataBean(_parameter);
                        dataMap.put(prodInst, bean);
                        bean.setDate(date);
                        bean.setProdInstance(prodInst);
                        bean.setProdName(multi.<String>getSelect(prodNameSel));
                        bean.setProdDescription(multi.<String>getSelect(prodDescSel));
                        bean.setProdDimension(Dimension.get(multi.<Long>getSelect(prodDimSel)));
                    }
                    bean.addUsage(quantity);
                }
            }

            final List<DataBean> datasource = new ArrayList<>(dataMap.values());
            Collections.sort(datasource, new Comparator<DataBean>()
            {

                @Override
                public int compare(final DataBean _arg0,
                                   final DataBean _arg1)
                {
                    return _arg0.getProdName().compareTo(_arg1.getProdName());
                }
            });

            createTitle(_parameter, paraMap.values());

            createSummary(_parameter, paraMap.values(), datasource);

            return new JRBeanCollectionDataSource(datasource);
        }

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
            getReport().addTitle(DynamicReports.cmp.verticalList(DynamicReports.cmp.text(order.toString()),
                            DynamicReports.cmp.text(fabrication.toString())));
        }


        protected void createSummary(final Parameter _parameter,
                                     final Collection<DataBean> _parentBeans,
                                     final Collection<DataBean> _bomBeans)
            throws EFapsException
        {
            for (final DataBean parentBean : _parentBeans) {
                for (final DataBean bomBean : _bomBeans) {
                    if (bomBean.getParentProdInst().equals(parentBean.getProdInst())) {
                        parentBean.addCost(bomBean.getUsageCost());
                    }
                }
            }
            final VerticalListBuilder vl = DynamicReports.cmp.verticalList();
            for (final DataBean parentBean : _parentBeans) {
                final StringBuilder bldr = new StringBuilder()
                                .append(parentBean.getCost().divide(parentBean.getFabricatedQuantity()))
                                .append(" ").append(parentBean.getProdName()).append(" ")
                                .append(parentBean.getProdDescription());
                vl.add(DynamicReports.cmp.text(bldr.toString()));
            }
            getReport().addSummary(vl);
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

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> prodNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.ProdName"),
                            "prodName", DynamicReports.type.stringType());
            final TextColumnBuilder<String> prodDescriptionColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.ProdDescription"),
                            "prodDescription", DynamicReports.type.stringType());

            final TextColumnBuilder<String> prodUoMColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.ProdUoM"),
                            "prodUoM", DynamicReports.type.stringType());

            final TextColumnBuilder<BigDecimal> orderQuantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.OrderQuantity"),
                            "orderQuantity", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> orderCostColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.OrderCost"),
                            "orderCost", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> usageQuantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.UsageQuantity"),
                            "usageQuantity", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> usageCostColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.UsageCost"),
                            "usageCost", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> fabricatedQuantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.FabricatedQuantity"),
                            "fabricatedQuantity", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> fabricatedCostColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.FabricatedCost"),
                            "fabricatedCost", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> percentColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.Percent"),
                            "percent", DynamicReports.type.bigDecimalType());

            final TextColumnBuilder<BigDecimal> differenceColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".Column.Difference"),
                            "difference", DynamicReports.type.bigDecimalType());


            final ColumnTitleGroupBuilder prodGrid = DynamicReports.grid.titleGroup(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".ColumnGroup.Prod"));
            final ColumnTitleGroupBuilder orderGrid = DynamicReports.grid.titleGroup(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".ColumnGroup.Order")
                            , orderQuantityColumn, orderCostColumn);
            final ColumnTitleGroupBuilder usageGrid = DynamicReports.grid.titleGroup(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".ColumnGroup.Usage")
                            , usageQuantityColumn, usageCostColumn);
            final ColumnTitleGroupBuilder fabricatedGrid = DynamicReports.grid.titleGroup(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".ColumnGroup.Fabricated"),
                            fabricatedQuantityColumn, fabricatedCostColumn);

            final ColumnTitleGroupBuilder analysisGrid = DynamicReports.grid.titleGroup(DBProperties
                            .getProperty(ProcessReport.class.getName() + ".ColumnGroup.Analysis"),
                            percentColumn, differenceColumn);

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
                    .addSubtotalAtSummary(DynamicReports.sbt.sum(orderCostColumn))
                    .addSubtotalAtSummary(DynamicReports.sbt.sum(usageCostColumn))
                    .addSubtotalAtSummary(DynamicReports.sbt.sum(fabricatedCostColumn));;

            _builder.columnGrid(prodGrid, orderGrid, fabricatedGrid, usageGrid, analysisGrid)
                    .addColumn(prodNameColumn, prodDescriptionColumn, prodUoMColumn,
                                    orderQuantityColumn, orderCostColumn,
                                    fabricatedQuantityColumn, fabricatedCostColumn,
                                    usageQuantityColumn, usageCostColumn,
                                    percentColumn, differenceColumn);
        }

        /**
         * @param _parameter
         * @return
         */
        protected DataBean getDataBean(final Parameter _parameter)
        {
            return new DataBean();
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


    public static class DataBean
    {
        private DateTime date;
        private Dimension prodDimension;
        private String prodDescription;
        private String prodName;
        private Instance prodInst;
        private Instance parentProdInst;
        private BigDecimal orderQuantity = BigDecimal.ZERO;
        private BigDecimal usageQuantity = BigDecimal.ZERO;
        private BigDecimal fabricatedQuantity = BigDecimal.ZERO;

        private BigDecimal cost = BigDecimal.ZERO;

        private boolean init;

        protected void initialize() throws EFapsException
        {
            if (!this.init) {
                final QueryBuilder costBldr = new QueryBuilder(CIProducts.ProductCost);
                costBldr.addWhereAttrEqValue(CIProducts.ProductCost.ProductLink, getProdInst());
                costBldr.addWhereAttrGreaterValue(CIProducts.ProductCost.ValidUntil, this.date.minusMinutes(1));
                costBldr.addWhereAttrLessValue(CIProducts.ProductCost.ValidFrom, this.date.plusMinutes(1));
                final MultiPrintQuery costMulti = costBldr.getPrint();
                costMulti.addAttribute(CIProducts.ProductCost.Price);
                costMulti.executeWithoutAccessCheck();
                if (costMulti.next()) {
                    this.cost = costMulti.<BigDecimal>getAttribute(CIProducts.ProductCost.Price);
                }
                this.init = true;
            }
        }

        public String getProdUoM()
        {
            return getProdDimension().getBaseUoM().getName();
        }

        public String getOid()
        {
            return getProdInst() == null ? null : this.getProdInst().getOid();
        }

        /**
         * @param _prodInst
         */
        public void setProdInstance(final Instance _prodInst)
        {
            this.prodInst = _prodInst;
        }

        /**
         * @param _bomQuan
         */
        public void addOrder(final BigDecimal _orderQuantity)
        {
            this.orderQuantity = this.orderQuantity.add(_orderQuantity);
        }


        /**
         * @param _bomQuan
         */
        public void addUsage(final BigDecimal _usageQuantity)
        {
            this.usageQuantity = this.usageQuantity.add(_usageQuantity);
        }


        /**
         * @param _bomQuan
         */
        public void addFabrication(final BigDecimal _fabricatedQuantity)
        {
            this.fabricatedQuantity = this.fabricatedQuantity.add(_fabricatedQuantity);
        }

        /**
         * @param _select
         */
        public void setProdDescription(final String _prodDescription)
        {
            this.prodDescription = _prodDescription;
        }

        /**
         * @param _select
         */
        public void setProdName(final String _prodName)
        {
            this.prodName = _prodName;
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
         */
        public void setProdInst(final Instance _prodInst)
        {
            this.prodInst = _prodInst;
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
         */
        public BigDecimal getOrderCost() throws EFapsException
        {
            return getOrderQuantity().multiply(getCost());
        }

        /**
         * Setter method for instance variable {@link #orderQuantity}.
         *
         * @param _orderQuantity value for instance variable {@link #orderQuantity}
         */
        public void setOrderQuantity(final BigDecimal _orderQuantity)
        {
            this.orderQuantity = _orderQuantity;
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
         */
        public BigDecimal getUsageCost() throws EFapsException
        {
            return getUsageQuantity().multiply(getCost());
        }

        /**
         * Setter method for instance variable {@link #usageQuantity}.
         *
         * @param _usageQuantity value for instance variable {@link #usageQuantity}
         */
        public void setUsageQuantity(final BigDecimal _usageQuantity)
        {
            this.usageQuantity = _usageQuantity;
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
         */
        public void setDate(final DateTime _date)
        {
            this.date = _date;
        }

        /**
         * Getter method for the instance variable {@link #cost}.
         *
         * @return value of instance variable {@link #cost}
         */
        public BigDecimal getCost() throws EFapsException
        {
            initialize();
            return this.cost;
        }

        /**
         * Getter method for the instance variable {@link #cost}.
         *
         * @return value of instance variable {@link #cost}
         */
        public void addCost(final BigDecimal _cost) throws EFapsException
        {
            this.init = true;
            this.cost = this.cost.add(_cost);
        }


        public BigDecimal getPercent() throws EFapsException
        {
            BigDecimal order = getFabricatedQuantity();
            if (order.compareTo(BigDecimal.ZERO) == 0) {
                order = BigDecimal.ONE;
            }
            return new BigDecimal(100).setScale(8)
                            .divide(order, BigDecimal.ROUND_HALF_UP)
                            .multiply(getUsageQuantity()).setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        public BigDecimal getDifference()
            throws EFapsException
        {
            return getFabricatedQuantity().subtract(getUsageQuantity());
        }

        /**
         * Setter method for instance variable {@link #cost}.
         *
         * @param _cost value for instance variable {@link #cost}
         */
        public void setCost(final BigDecimal _cost)
        {
            this.cost = _cost;
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
         * @param _fabricatedQuantity value for instance variable {@link #fabricatedQuantity}
         */
        public void setFabricatedQuantity(final BigDecimal _fabricatedQuantity)
        {
            this.fabricatedQuantity = _fabricatedQuantity;
        }

        /**
         * Getter method for the instance variable {@link #usageQuantity}.
         *
         * @return value of instance variable {@link #usageQuantity}
         */
        public BigDecimal getFabricatedCost() throws EFapsException
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
         * @param _prodDimension value for instance variable {@link #prodDimension}
         */
        public void setProdDimension(final Dimension _prodDimension)
        {
            this.prodDimension = _prodDimension;
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
         * @param _parentProdInst value for instance variable {@link #parentProdInst}
         */
        public void setParentProdInst(final Instance _parentProdInst)
        {
            this.parentProdInst = _parentProdInst;
        }
    }
}