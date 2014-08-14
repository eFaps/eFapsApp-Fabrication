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

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.ComponentColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.GenericElementBuilder;
import net.sf.dynamicreports.report.builder.component.TextFieldBuilder;
import net.sf.dynamicreports.report.builder.expression.AbstractComplexExpression;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.efaps.admin.datamodel.Dimension;
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
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.ui.wicket.models.EmbeddedLink;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("80ace6a8-4161-4592-b044-82a2aa56fe29")
@EFapsRevision("$Rev$")
public abstract class UsageReport_Base
    extends AbstractCommon
{

    /**
     * Logging instance used in this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(UsageReport.class);

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
        dyRp.setFileName(DBProperties.getProperty(UsageReport.class.getName() + ".FileName"));
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
        return new DynUsageReport();
    }

    /**
     * Report class.
     */
    public static class DynUsageReport
        extends AbstractDynamicReport
    {

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {


            final Map<Instance, DataBean> dataMap = new HashMap<>();
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            add2QueryBldr(_parameter, queryBldr);
            final MultiPrintQuery multi = queryBldr.getPrint();

            final SelectBuilder prodSel  = SelectBuilder.get().linkto(CISales.PositionAbstract.Product);
            final SelectBuilder prodInstSel = new SelectBuilder(prodSel).instance();
            final SelectBuilder prodNameSel = new SelectBuilder(prodSel).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder prodDescSel = new SelectBuilder(prodSel)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(prodInstSel, prodNameSel, prodDescSel);
            multi.addAttribute(CISales.PositionAbstract.Quantity, CISales.PositionAbstract.UoM);
            multi.execute();
            while (multi.next()) {
                final Instance prodInst = multi.<Instance>getSelect(prodInstSel);
                final DataBean bean;
                if (dataMap.containsKey(prodInst)) {
                    bean = dataMap.get(prodInst);
                } else {
                    bean = getBean(_parameter);
                    dataMap.put(prodInst, bean);
                    bean.setInstance(prodInst);
                    bean.setName(multi.<String>getSelect(prodNameSel));
                    bean.setDescription(multi.<String>getSelect(prodDescSel));
                    bean.setUoM(Dimension.getUoM(multi.<Long>getAttribute( CISales.PositionAbstract.UoM)).getName());
                }
                bean.addQuantity(multi.<BigDecimal>getAttribute(CISales.PositionAbstract.Quantity));
            }

            final List<DataBean> datasource = new ArrayList<>(dataMap.values());


            return new JRBeanCollectionDataSource(datasource);
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
            final Instance instance = _parameter.getInstance();
            if (instance.getType().isKindOf(CIFabrication.ProcessAbstract.getType())) {
                final QueryBuilder oAttrQueryBldr = new QueryBuilder(CISales.UsageReport);
                oAttrQueryBldr.addWhereAttrNotEqValue(CISales.UsageReport.Status,
                                Status.find(CISales.UsageReportStatus.Canceled));

                final QueryBuilder pAttrQueryBldr = new QueryBuilder(CIFabrication.Process2UsageReport);
                pAttrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2UsageReport.FromLink, instance);
                pAttrQueryBldr.addWhereAttrInQuery(CIFabrication.Process2UsageReport.ToLink,
                                oAttrQueryBldr.getAttributeQuery(CISales.UsageReport.ID));

                _queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                                pAttrQueryBldr.getAttributeQuery(CIFabrication.Process2UsageReport.ToLink));
            } else {
                _queryBldr.addWhereAttrEqValue(CISales.PositionAbstract.DocumentAbstractLink, instance);
            }
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(UsageReport.class.getName() + ".Column.ProdName"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(UsageReport.class.getName() + ".Column.ProdDescription"),
                            "description", DynamicReports.type.stringType());
            final TextColumnBuilder<String> uoMColumn = DynamicReports.col.column(DBProperties
                            .getProperty(UsageReport.class.getName() + ".Column.UoM"),
                            "uoM", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(UsageReport.class.getName() + ".Column.ProdQuantity"),
                            "quantity", DynamicReports.type.bigDecimalType());

            final GenericElementBuilder linkElement = DynamicReports.cmp.genericElement(
                            "http://www.efaps.org", "efapslink")
                            .addParameter(EmbeddedLink.JASPER_PARAMETERKEY, new LinkExpression())
                            .setHeight(12).setWidth(25);
            final ComponentColumnBuilder linkColumn = DynamicReports.col.componentColumn(linkElement).setTitle("");
            if (getExType().equals(ExportType.HTML)) {
                _builder.addColumn(linkColumn);
            }

            _builder.addColumn(quantityColumn, uoMColumn, nameColumn, descColumn);
        }

        protected TextFieldBuilder<String> getTitle(final Parameter _parameter,
                        final String _key)
        {
            return DynamicReports.cmp.text(DBProperties.getProperty(ProductionOrderReport.class.getName() + "." + _key))
                            .setStyle(DynamicReports.stl.style().setBold(true));
        }

        /**
         * @param _parameter Parameter as passed by the eFaps API
         * @return new ProductBean
         * @throws EFapsException on error
         */
        protected DataBean getBean(final Parameter _parameter)
            throws EFapsException
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
        private String name;

        private String description;

        private Instance instance;

        private BigDecimal quantity = BigDecimal.ZERO;

        private String uoM;

        /**
         * Getter method for the instance variable {@link #uoM}.
         *
         * @return value of instance variable {@link #uoM}
         */
        public String getUoM()
        {
            return this.uoM;
        }

        /**
         * Setter method for instance variable {@link #uoM}.
         *
         * @param _uoM value for instance variable {@link #uoM}
         */
        public void setUoM(final String _uoM)
        {
            this.uoM = _uoM;
        }

        public String getOid()
        {
            return this.instance == null ? null : this.instance.getOid();
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
         * @param _attribute
         */
        public void addQuantity(final BigDecimal _quantity)
        {
           setQuantity(getQuantity().add(_quantity));
        }

        /**
         * Setter method for instance variable {@link #quantity}.
         *
         * @param _quantity value for instance variable {@link #quantity}
         */
        public void setQuantity(final BigDecimal _quantity)
        {
            this.quantity = _quantity;
        }

        /**
         * Getter method for the instance variable {@link #description}.
         *
         * @return value of instance variable {@link #description}
         */
        public String getDescription()
        {
            return this.description;
        }




        /**
         * Setter method for instance variable {@link #description}.
         *
         * @param _description value for instance variable {@link #description}
         */
        public void setDescription(final String _description)
        {
            this.description = _description;
        }

        /**
         * Getter method for the instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public Instance getInstance()
        {
            return this.instance;
        }

        /**
         * Setter method for instance variable {@link #oid}.
         *
         * @param _oid value for instance variable {@link #oid}
         */
        public void setInstance(final Instance _oid)
        {
            this.instance = _oid;
        }

        /**
         * Getter method for the instance variable {@link #name}.
         *
         * @return value of instance variable {@link #name}
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * Setter method for instance variable {@link #name}.
         *
         * @param _name value for instance variable {@link #name}
         */
        public void setName(final String _name)
        {
            this.name = _name;
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this);
        }
    }
}
