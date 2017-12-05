/*
 * Copyright 2003 - 2017 The eFaps Team
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

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.jasperreport.datatype.DateTimeDate;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.FieldBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.group.CustomGroupBuilder;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRRewindableDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * The Class ProcessAnalyzeReport_Base.
 */
@EFapsUUID("5fd0ab22-a2af-4715-b495-f7fbaf52c5dd")
@EFapsApplication("eFapsApp-Fabrication")
public abstract class ProcessCostingReport_Base
    extends FilteredReport
{

    /**
     * Generate report.
     *
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
     * Export report.
     *
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
        dyRp.setFileName(getProperty(_parameter, ".FileName"));
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
     * Gets the report.
     *
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynProcessCostingReport(this);
    }

    /**
     * The Class DynProcessCostingReport.
     */
    public static class DynProcessCostingReport
        extends AbstractDynamicReport
    {

        /** The filtered report. */
        private final ProcessCostingReport_Base filteredReport;

        /**
         * Instantiates a new dyn process costing report.
         *
         * @param _filteredReport the filtered report
         */
        public DynProcessCostingReport(final ProcessCostingReport_Base _filteredReport)
        {
            this.filteredReport = _filteredReport;
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final JRRewindableDataSource ret;
            if (getFilteredReport().isCached(_parameter)) {
                ret = getFilteredReport().getDataSourceFromCache(_parameter);
                try {
                    ret.moveFirst();
                } catch (final JRException e) {
                    throw new EFapsException("JRException", e);
                }
            } else {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.ProductionCosting);
                attrQueryBldr.addWhereAttrNotEqValue(CISales.ProductionCosting.Status,
                                Status.find(CISales.ProductionCostingStatus.Canceled));
                add2QueryBuilder(_parameter, attrQueryBldr);

                final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionCostingPosition);
                queryBldr.addWhereAttrInQuery(CISales.ProductionCostingPosition.ProductionCostingLink,
                                attrQueryBldr.getAttributeQuery(CISales.ProductionCosting.ID));
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selDoc = SelectBuilder.get().linkto(
                                CISales.ProductionCostingPosition.ProductionCostingLink);
                final SelectBuilder selDocDate = new SelectBuilder(selDoc).attribute(CISales.ProductionCosting.Date);
                final SelectBuilder selProduct = SelectBuilder.get().linkto(CISales.ProductionCostingPosition.Product);
                final SelectBuilder selProductInst = new SelectBuilder(selProduct).instance();
                final SelectBuilder selProductName = new SelectBuilder(selProduct)
                                .attribute(CIProducts.ProductAbstract.Name);
                final SelectBuilder selProductDescr = new SelectBuilder(selProduct)
                                .attribute(CIProducts.ProductAbstract.Description);
                multi.addSelect(selDocDate, selProductInst, selProductName, selProductDescr);
                multi.addAttribute(CISales.ProductionCostingPosition.RateNetUnitPrice,
                                CISales.ProductionCostingPosition.RateNetPrice,
                                CISales.ProductionCostingPosition.Quantity,
                                CISales.ProductionCostingPosition.UoM);
                multi.execute();
                final List<DataBean> beans = new ArrayList<>();
                while (multi.next()) {
                    final DataBean bean = new DataBean()
                                    .setDocDate(multi.getSelect(selDocDate))
                                    .setProductInst(multi.getSelect(selProductInst))
                                    .setProductName(multi.getSelect(selProductName))
                                    .setProductDescr(multi.getSelect(selProductDescr))
                                    .setQuantity(multi.getAttribute(CISales.ProductionCostingPosition.Quantity))
                                    .setCost(multi.getAttribute(CISales.ProductionCostingPosition.RateNetPrice))
                                    .setUnitCost(multi.getAttribute(CISales.ProductionCostingPosition.RateNetUnitPrice))
                                    .setUoMId(multi.getAttribute(CISales.ProductionCostingPosition.UoM));
                    beans.add(bean);
                }
                final ComparatorChain<DataBean> chain = new ComparatorChain<>();
                chain.addComparator((_arg0, _arg1) -> _arg0.getProductName().compareTo(_arg1.getProductName()));
                chain.addComparator((_arg0, _arg1) -> _arg0.getProductDescr().compareTo(_arg1.getProductDescr()));
                chain.addComparator((_arg0, _arg1) -> _arg0.getDocDate().compareTo(_arg1.getDocDate()));
                Collections.sort(beans, chain);
                ret = new JRBeanCollectionDataSource(beans);
                getFilteredReport().cache(_parameter, ret);
            }
            return ret;
        }

        /**
         * Adds the two query builder.
         *
         * @param _parameter the parameter
         * @param _queryBldr the query bldr
         * @throws EFapsException the e faps exception
         */
        protected void add2QueryBuilder(final Parameter _parameter, final QueryBuilder _queryBldr)
            throws EFapsException
        {
            final Map<String, Object> filter = getFilteredReport().getFilterMap(_parameter);
            final DateTime dateFrom;
            if (filter.containsKey("dateFrom")) {
                dateFrom = (DateTime) filter.get("dateFrom");
            } else {
                dateFrom = new DateTime().minusMonths(1);
            }
            final DateTime dateTo;
            if (filter.containsKey("dateTo")) {
                dateTo = (DateTime) filter.get("dateTo");
            } else {
                dateTo = new DateTime();
            }
            _queryBldr.addWhereAttrGreaterValue(CISales.DocumentSumAbstract.Date, dateFrom.minusDays(1));
            _queryBldr.addWhereAttrLessValue(CISales.DocumentSumAbstract.Date, dateTo.plusDays(1)
                            .withTimeAtStartOfDay());
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter, final JasperReportBuilder _builder)
            throws EFapsException
        {
            final FieldBuilder<String> productField = DynamicReports.field("product", String.class);

            final TextColumnBuilder<DateTime> docDateColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.docDate"), "docDate", DateTimeDate.get());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.quantity"), "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> unitCostColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.unitCost"), "unitCost", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> costColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.cost"), "cost", DynamicReports.type.bigDecimalType());

            _builder.addField(productField);
            final CustomGroupBuilder productGroup = DynamicReports.grp.group(productField).groupByDataType();
            _builder.addSubtotalAtGroupFooter(productGroup, DynamicReports.sbt.sum(quantityColumn),
                            DynamicReports.sbt.sum(costColumn));
            _builder.groupBy(productGroup);

            _builder.addColumn(docDateColumn, quantityColumn, unitCostColumn, costColumn);
        }

        /**
         * Gets the filtered report.
         *
         * @return the filtered report
         */
        public ProcessCostingReport_Base getFilteredReport()
        {
            return this.filteredReport;
        }
    }

    /**
     * The Class DataBean.
     */
    public static class DataBean {
        /** The doc date. */
        private DateTime docDate;
        /** The product inst. */
        private Instance productInst;
        /** The product name. */
        private String productName;
        /** The product name. */
        private String productDescr;
        /** The cost. */
        private BigDecimal cost;
        /** The unit cost. */
        private BigDecimal unitCost;
        /** The quantity. */
        private BigDecimal quantity;
        /** The uo M id. */
        private Long uoMId;

        public DateTime getDocDate()
        {
            return this.docDate;
        }

        public DataBean setDocDate(final DateTime _docDate)
        {
            this.docDate = _docDate;
            return this;
        }

        public Instance getProductInst()
        {
            return this.productInst;
        }

        public DataBean setProductInst(final Instance _productInst)
        {
            this.productInst = _productInst;
            return this;
        }

        public String getProductName()
        {
            return this.productName;
        }

        public DataBean setProductName(final String _productName)
        {
            this.productName = _productName;
            return this;
        }

        public String getProductDescr()
        {
            return this.productDescr;
        }

        public DataBean setProductDescr(final String _productDescr)
        {
            this.productDescr = _productDescr;
            return this;
        }

        public BigDecimal getCost()
        {
            return this.cost;
        }

        public DataBean setCost(final BigDecimal _cost)
        {
            this.cost = _cost;
            return this;
        }

        public BigDecimal getUnitCost()
        {
            return this.unitCost;
        }

        public DataBean setUnitCost(final BigDecimal _unitCost)
        {
            this.unitCost = _unitCost;
            return this;
        }

        public BigDecimal getQuantity()
        {
            return this.quantity;
        }

        public DataBean setQuantity(final BigDecimal _quantity)
        {
            this.quantity = _quantity;
            return this;
        }

        public Long getUoMId()
        {
            return this.uoMId;
        }

        public DataBean setUoMId(final Long _uoMId)
        {
            this.uoMId = _uoMId;
            return this;
        }

        public String getProduct() {
            return String.format("%s - %s", getProductName(), getProductDescr());
        }
    }
}
