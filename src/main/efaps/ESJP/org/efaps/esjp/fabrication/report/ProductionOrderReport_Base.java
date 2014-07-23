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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.base.expression.AbstractSimpleExpression;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.ComponentColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.GenericElementBuilder;
import net.sf.dynamicreports.report.builder.component.SubreportBuilder;
import net.sf.dynamicreports.report.builder.component.TextFieldBuilder;
import net.sf.dynamicreports.report.builder.expression.AbstractComplexExpression;
import net.sf.dynamicreports.report.constant.StretchType;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import org.apache.commons.lang3.builder.ToStringBuilder;
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
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.AbstractCommon;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport_Base.ExportType;
import org.efaps.esjp.sales.report.ProfServReceiptReport;
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
@EFapsUUID("ee2c3c50-0ec1-4355-9d28-1c4a80c89050")
@EFapsRevision("$Rev$")
public abstract class ProductionOrderReport_Base
    extends AbstractCommon
{

    /**
     * Logging instance used in this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(ProductionOrderReport.class);

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
        dyRp.setFileName(DBProperties.getProperty(ProfServReceiptReport.class.getName() + ".FileName"));
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
        return new DynProductionOrderReport();
    }

    /**
     * Report class.
     */
    public static class DynProductionOrderReport
        extends AbstractDynamicReport
    {

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final Map<Instance, ProductBean> dataMap = new HashMap<>();
            final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
            add2QueryBldr(_parameter, queryBldr);
            final MultiPrintQuery multi = queryBldr.getPrint();

            final SelectBuilder prodSel  = SelectBuilder.get().linkto(CISales.PositionAbstract.Product);
            final SelectBuilder prodInstSel = new SelectBuilder(prodSel).instance();
            final SelectBuilder prodNameSel = new SelectBuilder(prodSel).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder prodDescSel = new SelectBuilder(prodSel)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(prodInstSel, prodNameSel, prodDescSel);
            multi.addAttribute(CISales.PositionAbstract.Quantity);
            multi.execute();
            while (multi.next()) {
                final Instance prodInst = multi.<Instance>getSelect(prodInstSel);
                final ProductBean bean;
                if (dataMap.containsKey(prodInst)) {
                    bean = dataMap.get(prodInst);
                } else {
                    bean = getProductBean(_parameter);
                    dataMap.put(prodInst, bean);
                    bean.setInstance(prodInst);
                    bean.setName(multi.<String>getSelect(prodNameSel));
                    bean.setDescription(multi.<String>getSelect(prodDescSel));
                }
                bean.addQuantity(multi.<BigDecimal>getAttribute(CISales.PositionAbstract.Quantity));
            }
            final List<ProductBean> datasource = new ArrayList<>(dataMap.values());
            Collections.sort(datasource, new Comparator<ProductBean>(){

                @Override
                public int compare(final ProductBean _arg0,
                                   final ProductBean _arg1)
                {
                    return _arg0.getName().compareTo(_arg1.getName());
                }});

            final Map<Instance, BOMBean> inst2bom = new HashMap<>();
            for (final ProductBean prodBean : datasource) {
                final List<BOMBean> bom = prodBean.getBom();
                for (final BOMBean bean  :bom) {
                    BOMBean beanTmp;
                    if (inst2bom.containsKey(bean.getMatInstance())) {
                        beanTmp = inst2bom.get(bean.getMatInstance());
                        beanTmp.setQuantity(beanTmp.getQuantity().add(bean.getQuantity()));
                    } else {
                        beanTmp = new BOMBean();
                        inst2bom.put(bean.getMatInstance(), beanTmp);
                        beanTmp.setMatInstance(bean.getMatInstance());
                        beanTmp.setQuantity(bean.getQuantity());
                        beanTmp.setMatDescription(bean.getMatDescription());
                        beanTmp.setMatName(bean.getMatName());
                        beanTmp.setUom(bean.getUom());
                    }
                }
            }

            final ProductBean subBean = getProductBean(_parameter);
            datasource.add(subBean);
            subBean.setQuantity(null);
            subBean.setName("-");
            subBean.setDescription("Suma");
            subBean.bom.addAll(inst2bom.values());
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
                final QueryBuilder oAttrQueryBldr = new QueryBuilder(CISales.ProductionOrder);
                oAttrQueryBldr.addWhereAttrNotEqValue(CISales.ProductionOrder.Status,
                                Status.find(CISales.ProductionOrderStatus.Canceled));

                final QueryBuilder pAttrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionOrder);
                pAttrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink, instance);
                pAttrQueryBldr.addWhereAttrInQuery(CIFabrication.Process2ProductionOrder.ToLink,
                                oAttrQueryBldr.getAttributeQuery(CISales.ProductionOrder.ID));

                _queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                                pAttrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));
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
                            .getProperty(ProductionOrderReport.class.getName() + ".Column.ProdName"),
                            "name", DynamicReports.type.stringType());
            final TextColumnBuilder<String> descColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProductionOrderReport.class.getName() + ".Column.ProdDescription"),
                            "description", DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column(DBProperties
                            .getProperty(ProductionOrderReport.class.getName() + ".Column.ProdQuantity"),
                            "quantity", DynamicReports.type.bigDecimalType());

            final GenericElementBuilder linkElement = DynamicReports.cmp.genericElement(
                            "http://www.efaps.org", "efapslink")
                            .addParameter(EmbeddedLink.JASPER_PARAMETERKEY, new LinkExpression())
                            .setHeight(12).setWidth(25);
            final ComponentColumnBuilder linkColumn = DynamicReports.col.componentColumn(linkElement).setTitle("");
            linkColumn.setPrintWhenExpression(new ShowLinkExpression());
            if (getExType().equals(ExportType.HTML)) {
                _builder.addColumn(linkColumn);
            }

            final SubreportBuilder subreport = DynamicReports.cmp
                            .subreport(getSubreportDesign(_parameter, getExType()))
                            .setDataSource(getSubreportDataSource(_parameter))
                            .setStretchType(StretchType.RELATIVE_TO_BAND_HEIGHT)
                            .setStyle(DynamicReports.stl.style().setBorder(DynamicReports.stl.pen1Point()));

            final ComponentColumnBuilder bomColumn = DynamicReports.col.componentColumn(DBProperties
                            .getProperty(ProductionOrderReport.class.getName() + ".Column.BOM"), subreport);

            if (ExportType.PDF.equals(getExType())) {

            } else if (ExportType.EXCEL.equals(getExType())) {

            } else {
                bomColumn.setWidth(500);
                descColumn.setWidth(200);
            }
            _builder.fields(DynamicReports.field("bom", List.class))
                .addColumn(quantityColumn, nameColumn, descColumn, bomColumn);

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
        protected ProductBean getProductBean(final Parameter _parameter)
            throws EFapsException
        {
            return new ProductBean();
        }

        /**
         * @param _parameter Parameter as passed by the eFaps API
         * @return new SubreportDataSource
         * @throws EFapsException on error
         */
        protected SubreportDataSource getSubreportDataSource(final Parameter _parameter)
        {
            return new SubreportDataSource();
        }

        /**
         * @param _parameter Parameter as passed by the eFaps API
         * @return new SubreportDesign
         * @throws EFapsException on error
         */
        protected SubreportDesign getSubreportDesign(final Parameter _parameter,
                                                     final ExportType _exType)
        {
            return new SubreportDesign(_exType);
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

    public static class ShowLinkExpression
        extends AbstractSimpleExpression<Boolean>
    {

        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        @Override
        public Boolean evaluate(final ReportParameters _reportParameters)
        {
            return Instance.get(_reportParameters.<String>getValue("oid")).isValid();
        }
    }

    public static class SubreportDesign
        extends AbstractSimpleExpression<JasperReportBuilder>
    {

        private static final long serialVersionUID = 1L;
        private final ExportType exType;

        public SubreportDesign(final ExportType _exType)
        {
            this.exType = _exType;
        }

        @Override
        public JasperReportBuilder evaluate(final ReportParameters _reportParameters)
        {
            final TextColumnBuilder<String> matNameColumn = DynamicReports.col.column("matName",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<String> matDescrColumn = DynamicReports.col.column("matDescription",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<String> uomColumn = DynamicReports.col.column("uom",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> quantityColumn = DynamicReports.col.column("quantity",
                            DynamicReports.type.bigDecimalType());
            final JasperReportBuilder report = DynamicReports.report();
            report.setShowColumnTitle(false);
            report.addColumn(quantityColumn, uomColumn);

            if (ExportType.PDF.equals(this.exType)) {
                report.setColumnStyle(DynamicReports.stl.style().setPadding(DynamicReports.stl.padding(2))
                                .setLeftBorder(DynamicReports.stl.pen1Point())
                                .setRightBorder(DynamicReports.stl.pen1Point())
                                .setBottomBorder(DynamicReports.stl.pen1Point())
                                .setTopBorder(DynamicReports.stl.pen1Point()));


            } else if (ExportType.EXCEL.equals(this.exType)) {

            } else if (ExportType.HTML.equals(this.exType)) {
                final GenericElementBuilder linkElement = DynamicReports.cmp.genericElement(
                            "http://www.efaps.org", "efapslink")
                            .addParameter(EmbeddedLink.JASPER_PARAMETERKEY, new LinkExpression())
                            .setHeight(12).setWidth(25);
                final ComponentColumnBuilder linkColumn = DynamicReports.col.componentColumn(linkElement).setTitle("");
                linkColumn.setPrintWhenExpression(new ShowLinkExpression());
                report.addColumn(linkColumn);
                matDescrColumn.setWidth(100);
                uomColumn.setWidth(10);
            }

            report.addColumn(matNameColumn, matDescrColumn);


            return report;
        }


    }




    public static class SubreportDataSource
        extends AbstractSimpleExpression<JRDataSource>
    {

        private static final long serialVersionUID = 1L;

        @Override
        public JRDataSource evaluate(final ReportParameters reportParameters)
        {

            final List<BOMBean> datasource  = reportParameters.getValue("bom");
            return new JRBeanCollectionDataSource(datasource);
        }
    }

    public static class ProductBean
    {
        /**
         * initialized.
         */
        private boolean initialized;

        private String name;

        private String description;

        private Instance instance;

        private BigDecimal quantity = BigDecimal.ZERO;

        private final List<BOMBean> bom = new ArrayList<BOMBean>();

        public String getOid()
        {
            return this.instance == null ? null : this.instance.getOid();
        }

        protected void initialize()
            throws EFapsException
        {
            if (!this.initialized) {
                final QueryBuilder queryBldr = new QueryBuilder(CIProducts.ProductionBOM);
                queryBldr.addWhereAttrEqValue(CIProducts.ProductionBOM.From, getInstance());
                final MultiPrintQuery multi = queryBldr.getPrint();
                final SelectBuilder matSel = SelectBuilder.get().linkto(CIProducts.ProductionBOM.To);
                final SelectBuilder matInstSel = new SelectBuilder(matSel).instance();
                final SelectBuilder matNameSel = new SelectBuilder(matSel).attribute(CIProducts.ProductAbstract.Name);
                final SelectBuilder matDescSel = new SelectBuilder(matSel)
                                .attribute(CIProducts.ProductAbstract.Description);
                multi.addSelect(matInstSel, matNameSel, matDescSel);
                multi.addAttribute(CIProducts.ProductionBOM.Quantity, CIProducts.ProductionBOM.UoM);
                multi.execute();
                while (multi.next()) {
                    final BOMBean bean = getBOMBean();
                    this.bom.add(bean);
                    bean.setMatInstance(multi.<Instance>getSelect(matInstSel));
                    bean.setMatName(multi.<String>getSelect(matNameSel));
                    bean.setMatDescription(multi.<String>getSelect(matDescSel));
                    final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CIProducts.ProductionBOM.UoM));

                    BigDecimal bomQuan = multi.<BigDecimal>getAttribute(CIProducts.ProductionBOM.Quantity);

                    bomQuan = bomQuan.setScale(8, BigDecimal.ROUND_HALF_UP)
                                    .multiply(new BigDecimal(uom.getNumerator()))
                                    .divide(new BigDecimal(uom.getDenominator())).multiply(getQuantity());
                    bean.setQuantity(bomQuan);
                    bean.setUom(uom.getDimension().getBaseUoM().getName());
                    bean.setUomID(uom.getDimension().getBaseUoM().getId());
                    bean.setDimension(uom.getDimension());
                }
                this.initialized = true;
                Collections.sort(this.bom, new Comparator<BOMBean>() {

                    @Override
                    public int compare(final BOMBean _arg0,
                                       final BOMBean _arg1)
                    {
                        return _arg0.getMatName().compareTo(_arg1.getMatName());
                    }});

            }
        }

        protected BOMBean getBOMBean() {
            return new BOMBean();
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


        /**
         * Getter method for the instance variable {@link #bom}.
         *
         * @return value of instance variable {@link #bom}
         */
        public List<BOMBean> getBom() throws EFapsException
        {
            if (this.bom.isEmpty()) {
                initialize();
            }
            return this.bom;
        }


        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this);
        }
    }


    /**
     * DataBean.
     */
    public static class BOMBean
    {
        private Instance matInstance;

        private String matName;

        private String matDescription;
        private BigDecimal quantity;
        private String uom;
        private Long uomID;
        private Dimension dimension;

        /**
         * Getter method for the instance variable {@link #matInstance}.
         *
         * @return value of instance variable {@link #matInstance}
         */
        public Instance getMatInstance()
        {
            return this.matInstance;
        }


        /**
         * Setter method for instance variable {@link #matInstance}.
         *
         * @param _matInstance value for instance variable {@link #matInstance}
         */
        public void setMatInstance(final Instance _matInstance)
        {
            this.matInstance = _matInstance;
        }


        /**
         * Getter method for the instance variable {@link #matName}.
         *
         * @return value of instance variable {@link #matName}
         */
        public String getMatName()
        {
            return this.matName;
        }

        /**
         * Setter method for instance variable {@link #matName}.
         *
         * @param _matName value for instance variable {@link #matName}
         */
        public void setMatName(final String _matName)
        {
            this.matName = _matName;
        }

        /**
         * Getter method for the instance variable {@link #matDescription}.
         *
         * @return value of instance variable {@link #matDescription}
         */
        public String getMatDescription()
        {
            return this.matDescription;
        }

        /**
         * Setter method for instance variable {@link #matDescription}.
         *
         * @param _matDescription value for instance variable {@link #matDescription}
         */
        public void setMatDescription(final String _matDescription)
        {
            this.matDescription = _matDescription;
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
        public void setQuantity(final BigDecimal _quantity)
        {
            this.quantity = _quantity;
        }

        /**
         * Getter method for the instance variable {@link #uom}.
         *
         * @return value of instance variable {@link #uom}
         */
        public String getUom()
        {
            return this.uom;
        }

        /**
         * Setter method for instance variable {@link #uom}.
         *
         * @param _uom value for instance variable {@link #uom}
         */
        public void setUom(final String _uom)
        {
            this.uom = _uom;
        }

        public String getOid()
        {
            return this.matInstance == null ? null : this.matInstance.getOid();
        }

        @Override
        public String toString()
        {
            return ToStringBuilder.reflectionToString(this);
        }


        /**
         * Getter method for the instance variable {@link #dimension}.
         *
         * @return value of instance variable {@link #dimension}
         */
        public Dimension getDimension()
        {
            return this.dimension;
        }

        /**
         * Setter method for instance variable {@link #dimension}.
         *
         * @param _dimension value for instance variable {@link #dimension}
         */
        public void setDimension(Dimension _dimension)
        {
            this.dimension = _dimension;
        }


        /**
         * Getter method for the instance variable {@link #uomID}.
         *
         * @return value of instance variable {@link #uomID}
         */
        public Long getUomID()
        {
            return this.uomID;
        }


        /**
         * Setter method for instance variable {@link #uomID}.
         *
         * @param _uomID value for instance variable {@link #uomID}
         */
        public void setUomID(Long _uomID)
        {
            this.uomID = _uomID;
        }
    }
}
