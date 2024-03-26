/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
 */
package org.efaps.esjp.fabrication.report;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.efaps.admin.dbproperty.DBProperties;
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
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.products.BOMCalculator;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabBuilder;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabColumnGroupBuilder;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabMeasureBuilder;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabRowGroupBuilder;
import net.sf.dynamicreports.report.builder.style.ConditionalStyleBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.Calculation;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("9bf8cb84-610b-485b-b43f-ce4333ac47a5")
@EFapsApplication("eFapsApp-Fabrication")
public abstract class ProductionOrderVsStockReport_Base
    extends AbstractCommon
{

    /**
     * Logging instance used in this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(ProductionOrderVsStockReport.class);

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
        dyRp.setFileName(DBProperties.getProperty(ProductionOrderVsStockReport.class.getName() + ".FileName"));
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
        return new DynProductionOrderVsStockReport();
    }

    public static class DynProductionOrderVsStockReport
        extends AbstractDynamicReport
    {

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final List<DataBean> datasource = new ArrayList<>();
            final Map<Instance, String> products = new HashMap<>();
            final Map<Instance, BigDecimal> quantities = new HashMap<>();

            final QueryBuilder attrQueryBldr = getQueryBldrFromProperties(_parameter);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                            attrQueryBldr.getAttributeQuery(CIERP.DocumentAbstract.ID));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProdInst = SelectBuilder.get().linkto(CISales.PositionAbstract.Product).instance();
            final SelectBuilder selProdName = SelectBuilder.get().linkto(CISales.PositionAbstract.Product)
                            .attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDescr = SelectBuilder.get().linkto(CISales.PositionAbstract.Product)
                            .attribute(CIProducts.ProductAbstract.Description);
            final SelectBuilder selDocName = SelectBuilder.get().linkto(CISales.PositionAbstract.DocumentAbstractLink)
                            .attribute(CISales.DocumentAbstract.Name);
            multi.addSelect(selProdInst, selProdName, selProdDescr, selDocName);
            multi.addAttribute(CISales.PositionAbstract.Quantity);
            multi.execute();
            while (multi.next()) {
                BigDecimal quantity = multi.getAttribute(CISales.PositionAbstract.Quantity);
                final Instance prodInst = multi.getSelect(selProdInst);
                final String prodName = multi.getSelect(selProdName);
                final String docName = multi.getSelect(selDocName);
                final String prodDescr = multi.getSelect(selProdDescr);
                final DataBean bean = new DataBean()
                                .setProductInstance(prodInst)
                                .setProduct(prodName + " " + prodDescr)
                                .setDoc(docName).setQuantity(quantity);
                datasource.add(bean);
                products.put(prodInst, prodName + " " + prodDescr);

                if (quantities.containsKey(prodInst)) {
                    quantity = quantity.add(quantities.get(prodInst));
                }
                quantities.put(prodInst, quantity);
            }

            for (final Entry<Instance, String> entry : products.entrySet()) {
                final BOMCalculator bomCalc = new BOMCalculator(_parameter, entry.getKey(), null);
                final DataBean bean = new DataBean()
                                .setProductInstance(entry.getKey())
                                .setProduct(entry.getValue())
                                .setDoc(DBProperties
                                                .getProperty(ProductionOrderVsStockReport.class.getName()
                                                                + ".QuantityStock"))
                                .setQuantity(bomCalc.getQuantityStock());
                datasource.add(bean);
                final DataBean bean2 = new DataBean()
                                .setProductInstance(entry.getKey())
                                .setProduct(entry.getValue())
                                .setDoc(DBProperties
                                                .getProperty(ProductionOrderVsStockReport.class.getName()
                                                                + ".QuantityOnPaper"))
                                .setQuantity(bomCalc.getQuantityOnPaper());
                datasource.add(bean2);
                final DataBean bean3 = new DataBean()
                                .setProductInstance(entry.getKey())
                                .setProduct(entry.getValue())
                                .setDoc(DBProperties
                                                .getProperty(ProductionOrderVsStockReport.class.getName()
                                                                + ".Total"))
                                .setQuantity(quantities.get(entry.getKey()));
                datasource.add(bean3);
            }

            return new JRBeanCollectionDataSource(datasource);
        }

        @Override
        protected void addColumnDefinition(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final CrosstabBuilder crosstab = DynamicReports.ctab.crosstab();
            final CrosstabRowGroupBuilder<String> productGroup = DynamicReports.ctab.rowGroup("product", String.class)
                            .setShowTotal(false);
            final CrosstabColumnGroupBuilder<String> docGroup = DynamicReports.ctab
                            .columnGroup("doc", String.class).setShowTotal(false);

            final CrosstabMeasureBuilder<BigDecimal> quantityMeasure = DynamicReports.ctab.measure("quantity",
                            BigDecimal.class, Calculation.SUM);

            final ConditionalStyleBuilder condition1 = DynamicReports.stl.conditionalStyle(
                            DynamicReports.cnd.greater(quantityMeasure, 0)).setBold(true);
            final StyleBuilder quantityStyle = DynamicReports.stl.style().conditionalStyles(condition1)
                            .setBorder(DynamicReports.stl.pen1Point());

            quantityMeasure.setStyle(quantityStyle);

            crosstab.headerCell(DynamicReports.cmp.text(DBProperties
                            .getProperty(ProductionOrderVsStockReport.class.getName() + ".HeaderCell"))
                            .setStyle(DynamicReports.stl.style().setBold(true)))
                            .rowGroups(productGroup)
                            .columnGroups(docGroup)
                            .measures(quantityMeasure);
            _builder.summary(crosstab);
        }
    }

    public static class DataBean
    {

        private Instance productInstance;
        private String product;
        private String doc;
        private BigDecimal quantity;

        /**
         * Getter method for the instance variable {@link #product}.
         *
         * @return value of instance variable {@link #product}
         */
        public String getProduct()
        {
            return this.product;
        }

        /**
         * Setter method for instance variable {@link #product}.
         *
         * @param _product value for instance variable {@link #product}
         */
        public DataBean setProduct(final String _product)
        {
            this.product = _product;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #doc}.
         *
         * @return value of instance variable {@link #doc}
         */
        public String getDoc()
        {
            return this.doc;
        }

        /**
         * Setter method for instance variable {@link #doc}.
         *
         * @param _doc value for instance variable {@link #doc}
         */
        public DataBean setDoc(final String _doc)
        {
            this.doc = _doc;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #quantity}.
         *
         * @return value of instance variable {@link #quantity}
         */
        public BigDecimal getQuantity()
        {
            return this.quantity;
        }

        /**
         * Setter method for instance variable {@link #quantity}.
         *
         * @param _quantity value for instance variable {@link #quantity}
         */
        public DataBean setQuantity(final BigDecimal _quantity)
        {
            this.quantity = _quantity;
            return this;
        }

        /**
         * Getter method for the instance variable {@link #productInstance}.
         *
         * @return value of instance variable {@link #productInstance}
         */
        public Instance getProductInstance()
        {
            return this.productInstance;
        }

        /**
         * Setter method for instance variable {@link #productInstance}.
         *
         * @param _productInstance value for instance variable
         *            {@link #productInstance}
         */
        public DataBean setProductInstance(final Instance _productInstance)
        {
            this.productInstance = _productInstance;
            return this;
        }
    }
}
