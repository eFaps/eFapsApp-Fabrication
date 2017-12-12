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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.jasperreport.datatype.DateTimeDate;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.esjp.erp.RateInfo;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

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
     * The Enum DateConfig.
     *
     * @author The eFaps Team
     */
    public enum GroupBy
    {
        /** Includes a group on date level. */
        PRODUCT,
        /** Includes a group on monthly level. */
        PROCESS;
    }

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
                final Instance reportCurrencyInst = getReportCurrency(_parameter);
                final String currency = CurrencyInst.get(reportCurrencyInst).getSymbol();

                final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.ProductionCosting);
                attrQueryBldr.addWhereAttrNotEqValue(CISales.ProductionCosting.Status,
                                Status.find(CISales.ProductionCostingStatus.Canceled));
                add2QueryBuilder(_parameter, attrQueryBldr);

                final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionCostingPosition);
                queryBldr.addWhereAttrInQuery(CISales.ProductionCostingPosition.ProductionCostingLink,
                                attrQueryBldr.getAttributeQuery(CISales.ProductionCosting.ID));
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder selProcess = SelectBuilder.get().linkto(
                                CISales.ProductionCostingPosition.ProductionCostingLink)
                                .linkfrom(CIFabrication.Process2ProductionCosting.ToLink)
                                .linkto(CIFabrication.Process2ProductionCosting.FromLink);
                final SelectBuilder selProcessDate = new SelectBuilder(selProcess)
                                .attribute(CIFabrication.Process.Date);
                final SelectBuilder selProcessName = new SelectBuilder(selProcess)
                                .attribute(CIFabrication.Process.Name);
                final SelectBuilder selDoc = SelectBuilder.get().linkto(
                                CISales.ProductionCostingPosition.ProductionCostingLink);
                final SelectBuilder selDocDate = new SelectBuilder(selDoc).attribute(CISales.ProductionCosting.Date);
                final SelectBuilder selDocName = new SelectBuilder(selDoc).attribute(CISales.ProductionCosting.Name);
                final SelectBuilder selProduct = SelectBuilder.get().linkto(CISales.ProductionCostingPosition.Product);
                final SelectBuilder selProductInst = new SelectBuilder(selProduct).instance();
                final SelectBuilder selProductName = new SelectBuilder(selProduct)
                                .attribute(CIProducts.ProductAbstract.Name);
                final SelectBuilder selProductDescr = new SelectBuilder(selProduct)
                                .attribute(CIProducts.ProductAbstract.Description);
                multi.addSelect(selProcessDate, selProcessName, selDocDate, selDocName, selProductInst, selProductName,
                                selProductDescr);
                multi.addAttribute(CISales.ProductionCostingPosition.RateNetUnitPrice,
                                CISales.ProductionCostingPosition.RateNetPrice,
                                CISales.ProductionCostingPosition.NetUnitPrice,
                                CISales.ProductionCostingPosition.RateCurrencyId,
                                CISales.ProductionCostingPosition.NetPrice,
                                CISales.ProductionCostingPosition.Quantity,
                                CISales.ProductionCostingPosition.UoM);
                multi.execute();
                final List<DataBean> beans = new ArrayList<>();
                while (multi.next()) {
                    BigDecimal cost;
                    BigDecimal unitCost;
                    final Long rateCurrencyId = multi.getAttribute(CISales.ProductionCostingPosition.RateCurrencyId);
                    if (Currency.getBaseCurrency().equals(reportCurrencyInst)) {
                        cost = multi.getAttribute(CISales.ProductionCostingPosition.NetPrice);
                        unitCost = multi.getAttribute(CISales.ProductionCostingPosition.NetUnitPrice);
                    } else if (rateCurrencyId.equals(reportCurrencyInst.getId())) {
                        cost = multi.getAttribute(CISales.ProductionCostingPosition.RateNetPrice);
                        unitCost = multi.getAttribute(CISales.ProductionCostingPosition.RateNetUnitPrice);
                    } else {
                        final RateInfo rateInfo = new Currency().evaluateRateInfo(_parameter, multi.<DateTime>getSelect(
                                        selDocDate), reportCurrencyInst);
                        cost = multi.<BigDecimal>getAttribute(CISales.ProductionCostingPosition.NetPrice)
                                        .multiply(rateInfo.getSaleRate());
                        unitCost = multi.<BigDecimal>getAttribute(CISales.ProductionCostingPosition.NetUnitPrice)
                                        .multiply(rateInfo.getSaleRate());
                    }

                    final DataBean bean = new DataBean()
                                    .setProcessDate(multi.getSelect(selProcessDate))
                                    .setProcessName(multi.getSelect(selProcessName))
                                    .setDocDate(multi.getSelect(selDocDate))
                                    .setDocName(multi.getSelect(selDocName))
                                    .setProductInst(multi.getSelect(selProductInst))
                                    .setProductName(multi.getSelect(selProductName))
                                    .setProductDescr(multi.getSelect(selProductDescr))
                                    .setQuantity(multi.getAttribute(CISales.ProductionCostingPosition.Quantity))
                                    .setCost(cost)
                                    .setUnitCost(unitCost)
                                    .setUoMId(multi.getAttribute(CISales.ProductionCostingPosition.UoM))
                                    .setCurrency(currency);
                    beans.add(bean);
                }
                final Collection<GroupBy> groupBy = evaluateGroupBy(_parameter);
                final ComparatorChain<DataBean> chain = new ComparatorChain<>();
                for (final GroupBy group : groupBy) {
                    switch (group) {
                        case PRODUCT:
                            chain.addComparator((_arg0, _arg1) -> _arg0.getProductName().compareTo(_arg1
                                            .getProductName()));
                            chain.addComparator((_arg0, _arg1) -> _arg0.getProductDescr().compareTo(_arg1
                                            .getProductDescr()));
                            break;
                        case PROCESS:
                            chain.addComparator((_arg0, _arg1) -> _arg0.getProcessDate().compareTo(_arg1
                                            .getProcessDate()));
                            chain.addComparator((_arg0, _arg1) -> _arg0.getProcessName().compareTo(_arg1
                                            .getProcessName()));
                            break;
                        default:
                            break;
                    }
                }

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
            final QueryBuilder processQueryBldr = new QueryBuilder(CIFabrication.Process);
            processQueryBldr.addWhereAttrGreaterValue(CIFabrication.Process.Date, dateFrom.minusDays(1));
            processQueryBldr.addWhereAttrLessValue(CIFabrication.Process.Date, dateTo.plusDays(1)
                            .withTimeAtStartOfDay());

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2DocumentAbstract);
            attrQueryBldr.addWhereAttrInQuery(CIFabrication.Process2DocumentAbstract.FromLinkAbstract,
                            processQueryBldr.getAttributeQuery(CIFabrication.Process.ID));
            _queryBldr.addWhereAttrInQuery(CISales.DocumentAbstract.ID,
                            attrQueryBldr.getAttributeQuery(CIFabrication.Process2DocumentAbstract.ToLinkAbstract));
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter, final JasperReportBuilder _builder)
            throws EFapsException
        {
            final FieldBuilder<String> productField = DynamicReports.field("product", String.class);
            final TextColumnBuilder<String> productNameColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.productName"), "productName", DynamicReports.type.stringType());
            final TextColumnBuilder<String> productDescrColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.productDescr"), "productDescr", DynamicReports.type.stringType());
            final FieldBuilder<String> processField = DynamicReports.field("process", String.class);
            final TextColumnBuilder<DateTime> processDateColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.processDate"), "processDate", DateTimeDate.get());
            final TextColumnBuilder<String> processNameColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.processName"), "processName", DynamicReports.type.stringType());
            final TextColumnBuilder<DateTime> docDateColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.docDate"), "docDate", DateTimeDate.get());
            final TextColumnBuilder<String> docNameColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.docName"), "docName", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.quantity"), "quantity", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> uoMColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.uoM"), "uoM", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> unitCostColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.unitCost"), "unitCost", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> costColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.cost"), "cost", DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> currencyColumn = DynamicReports.col.column(getFilteredReport()
                            .getDBProperty("Column.currency"), "currency", DynamicReports.type.stringType());

            final Collection<GroupBy> groupBy = evaluateGroupBy(_parameter);
            for (final GroupBy group : groupBy) {
                switch (group) {
                    case PRODUCT:
                        _builder.addField(productField);
                        final CustomGroupBuilder productGroup = DynamicReports.grp.group(productField)
                                        .groupByDataType();

                        _builder.addSubtotalAtGroupFooter(productGroup, DynamicReports.sbt.sum(costColumn),
                                getUoMSubtotalBuilder(_parameter, _builder, uoMColumn, quantityColumn, productGroup));
                        _builder.groupBy(productGroup);
                        break;
                    case PROCESS:
                        _builder.addField(processField);
                        final CustomGroupBuilder processGroup = DynamicReports.grp.group(processField)
                                        .groupByDataType();
                        _builder.addSubtotalAtGroupFooter(processGroup, DynamicReports.sbt.sum(costColumn),
                                getUoMSubtotalBuilder(_parameter, _builder, uoMColumn, quantityColumn, processGroup));
                        _builder.groupBy(processGroup);
                        break;
                    default:
                        break;
                }
            }
            if (!groupBy.contains(GroupBy.PRODUCT)) {
                _builder.addColumn(productNameColumn, productDescrColumn);
            }
            if (!groupBy.contains(GroupBy.PROCESS)) {
                _builder.addColumn(processNameColumn, processDateColumn);
            }
            final UoMSubtotalBuilder subTotal = getUoMSubtotalBuilder(_parameter, _builder, uoMColumn, quantityColumn,
                            null);

            _builder.addColumn(docNameColumn, docDateColumn, quantityColumn, uoMColumn, unitCostColumn, costColumn,
                            currencyColumn)
                            .addSubtotalAtSummary(subTotal, DynamicReports.sbt.sum(costColumn));
        }

        /**
         * Gets the filtered report.
         *
         * @return the filtered report
         */
        protected ProcessCostingReport_Base getFilteredReport()
        {
            return this.filteredReport;
        }

        /**
         * Evaluate price config.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @return the price config
         * @throws EFapsException on error
         */
        protected Collection<GroupBy> evaluateGroupBy(final Parameter _parameter)
            throws EFapsException
        {
            final List<GroupBy> ret = new ArrayList<>();
            final Map<String, Object> filters = this.filteredReport.getFilterMap(_parameter);
            final GroupByFilterValue groupBy = (GroupByFilterValue) filters.get("groupBy");
            final List<Enum<?>> selected = groupBy.getObject();
            if (CollectionUtils.isNotEmpty(selected)) {
                for (final Enum<?> sel : selected) {
                    ret.add((GroupBy) sel);
                }
            }
            return ret;
        }

        /**
         * Gets the Currency.
         *
         * @param _parameter Parameter as passed by the eFaps API
         * @return the pay doc
         * @throws EFapsException on error
         */
        protected Instance getReportCurrency(final Parameter _parameter)
            throws EFapsException
        {
            final Map<String, Object> filterMap = this.filteredReport.getFilterMap(_parameter);
            Instance ret = null;
            if (filterMap.containsKey("currency")) {
                final CurrencyFilterValue filterValue = (CurrencyFilterValue) filterMap.get("currency");
                ret = filterValue.getObject();
            }
            return ret;
        }
    }

    /**
     * The Class DataBean.
     */
    public static class DataBean
    {


        /** The process date. */
        private DateTime processDate;

        /** The process name. */
        private String processName;

        /** The doc date. */
        private DateTime docDate;

        /** The doc name. */
        private String docName;

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

        /** The currency id. */
        private String currency;

        /**
         * Gets the doc date.
         *
         * @return the doc date
         */
        public DateTime getProcessDate()
        {
            return this.processDate;
        }

        /**
         * Sets the process date.
         *
         * @param _processDate the process date
         * @return the data bean
         */
        public DataBean setProcessDate(final DateTime _processDate)
        {
            this.processDate = _processDate;
            return this;
        }

        /**
         * Gets the process name.
         *
         * @return the process name
         */
        public String getProcessName()
        {
            return this.processName;
        }

        /**
         * Sets the process name.
         *
         * @param _processName the process name
         * @return the data bean
         */
        public DataBean setProcessName(final String _processName)
        {
            this.processName = _processName;
            return this;
        }


        /**
         * Gets the doc date.
         *
         * @return the doc date
         */
        public DateTime getDocDate()
        {
            return this.docDate;
        }

        /**
         * Sets the doc date.
         *
         * @param _docDate the doc date
         * @return the data bean
         */
        public DataBean setDocDate(final DateTime _docDate)
        {
            this.docDate = _docDate;
            return this;
        }

        /**
         * Gets the doc name.
         *
         * @return the doc name
         */
        public String getDocName()
        {
            return this.docName;
        }

        /**
         * Sets the doc name.
         *
         * @param _docName the doc name
         * @return the data bean
         */
        public DataBean setDocName(final String _docName)
        {
            this.docName = _docName;
            return this;
        }

        /**
         * Gets the product inst.
         *
         * @return the product inst
         */
        public Instance getProductInst()
        {
            return this.productInst;
        }

        /**
         * Sets the product inst.
         *
         * @param _productInst the product inst
         * @return the data bean
         */
        public DataBean setProductInst(final Instance _productInst)
        {
            this.productInst = _productInst;
            return this;
        }

        /**
         * Gets the product name.
         *
         * @return the product name
         */
        public String getProductName()
        {
            return this.productName;
        }

        /**
         * Sets the product name.
         *
         * @param _productName the product name
         * @return the data bean
         */
        public DataBean setProductName(final String _productName)
        {
            this.productName = _productName;
            return this;
        }

        /**
         * Gets the product name.
         *
         * @return the product name
         */
        public String getProductDescr()
        {
            return this.productDescr;
        }

        /**
         * Sets the product descr.
         *
         * @param _productDescr the product descr
         * @return the data bean
         */
        public DataBean setProductDescr(final String _productDescr)
        {
            this.productDescr = _productDescr;
            return this;
        }

        /**
         * Gets the cost.
         *
         * @return the cost
         */
        public BigDecimal getCost()
        {
            return this.cost;
        }

        /**
         * Sets the cost.
         *
         * @param _cost the cost
         * @return the data bean
         */
        public DataBean setCost(final BigDecimal _cost)
        {
            this.cost = _cost;
            return this;
        }

        /**
         * Gets the unit cost.
         *
         * @return the unit cost
         */
        public BigDecimal getUnitCost()
        {
            return this.unitCost;
        }

        /**
         * Sets the unit cost.
         *
         * @param _unitCost the unit cost
         * @return the data bean
         */
        public DataBean setUnitCost(final BigDecimal _unitCost)
        {
            this.unitCost = _unitCost;
            return this;
        }

        /**
         * Gets the quantity.
         *
         * @return the quantity
         */
        public BigDecimal getQuantity()
        {
            return this.quantity;
        }

        /**
         * Sets the quantity.
         *
         * @param _quantity the quantity
         * @return the data bean
         */
        public DataBean setQuantity(final BigDecimal _quantity)
        {
            this.quantity = _quantity;
            return this;
        }

        /**
         * Gets the uo M id.
         *
         * @return the uo M id
         */
        public Long getUoMId()
        {
            return this.uoMId;
        }

        /**
         * Sets the uo M id.
         *
         * @param _uoMId the uo M id
         * @return the data bean
         */
        public DataBean setUoMId(final Long _uoMId)
        {
            this.uoMId = _uoMId;
            return this;
        }

        /**
         * Sets the uo M id.
         *
         * @param _currency the currency
         * @return the data bean
         */
        public DataBean setCurrency(final String _currency)
        {
            this.currency = _currency;
            return this;
        }

        /**
         * Gets the currency.
         *
         * @return the currency
         * @throws EFapsException the e faps exception
         */
        public String getCurrency()
            throws EFapsException
        {
            return this.currency;
        }

        /**
         * Gets the product.
         *
         * @return the product
         */
        public String getProduct()
        {
            return String.format("%s - %s", getProductName(), getProductDescr());
        }
        /**
         * Gets the product.
         *
         * @return the product
         * @throws EFapsException on error
         */
        public String getProcess()
            throws EFapsException
        {
            return String.format("%s - %s", getProcessName(), getProcessDate()
                            .toString(DateTimeFormat.shortDate().withLocale(Context.getThreadContext().getLocale())));
        }

        /**
         * Gets the uo M.
         *
         * @return the uo M
         */
        public String getUoM()
        {
            return Dimension.getUoM(this.uoMId).getName();
        }
    }
}
