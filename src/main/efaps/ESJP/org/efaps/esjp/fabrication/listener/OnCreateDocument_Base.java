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
 * Revision:        $Rev$
 * Last Changed:    $Date$
 * Last Changed By: $Author$
 */

package org.efaps.esjp.fabrication.listener;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.ci.CITableSales;
import org.efaps.esjp.common.listener.ITypedClass;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.common.util.InterfaceUtils_Base.DojoLibs;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.listener.IOnCreateDocument;
import org.efaps.esjp.fabrication.Process;
import org.efaps.esjp.fabrication.report.ProcessReport;
import org.efaps.esjp.fabrication.report.ProcessReport_Base.DataBean;
import org.efaps.esjp.fabrication.report.ProcessReport_Base.ValuesBean;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.BOMBean;
import org.efaps.esjp.products.Storage;
import org.efaps.esjp.products.util.Products;
import org.efaps.esjp.products.util.Products.ProductIndividual;
import org.efaps.esjp.products.util.ProductsSettings;
import org.efaps.esjp.sales.document.ProductionOrder;
import org.efaps.esjp.sales.document.UsageReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("da56cbac-c191-4d9f-9c12-ffab92c8eefb")
@EFapsApplication("eFapsApp-Fabrication")
public abstract class OnCreateDocument_Base
    extends CommonDocument
    implements IOnCreateDocument
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCreate(final Parameter _parameter,
                            final CreatedDoc _createdDoc)
        throws EFapsException
    {
        // not used
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CharSequence getJavaScript4Doc(final ITypedClass _typeClass,
                                          final Parameter _parameter)
        throws EFapsException
    {
        CharSequence ret = new String();
        if (_typeClass != null) {
            if (CISales.UsageReport.equals(_typeClass.getCIType())) {
                ret = getJavaScript4UsageReport(_parameter);
            } else if (CISales.ProductionReport.equals(_typeClass.getCIType())) {
                ret = getJavaScript4ProductionReport(_parameter);
            } else if (CISales.ProductionCosting.equals(_typeClass.getCIType())) {
                ret = getJavaScript4ProductionCosting(_parameter);
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return JavaScript
     * @throws EFapsException on error
     */
    protected CharSequence getJavaScript4ProductionCosting(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        final Instance inst = _parameter.getInstance();
        if (inst != null && inst.isValid() && inst.getType().isKindOf(CIFabrication.ProcessAbstract)) {
            final PrintQuery print = new PrintQuery(inst);
            print.addAttribute(CIFabrication.ProcessAbstract.Name);
            print.executeWithoutAccessCheck();

            final String name = print.<String>getAttribute(CIFabrication.ProcessAbstract.Name);

            js.append(getSetFieldValue(0, CIFormSales.Sales_ProductionCostingForm.fabricationProcess.name,
                            inst.getOid(), name));

            final DecimalFormat qtyFrmt = NumberFormatter.get().getTwoDigitsFormatter();

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionReport);
            attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionReport.FromLink, inst);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionReportPosition);
            queryBldr.addWhereAttrInQuery(CISales.ProductionReportPosition.DocumentAbstractLink,
                            attrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionReport.ToLink));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = SelectBuilder.get()
                            .linkto(CISales.ProductionReportPosition.DocumentAbstractLink).instance();
            final SelectBuilder selProd = SelectBuilder.get().linkto(CISales.ProductionReportPosition.Product);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdIndividual = new SelectBuilder(selProd)
                                        .attribute(CIProducts.ProductAbstract.Individual);
            final SelectBuilder selProdDescr = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(selDocInst, selProdInst, selProdName, selProdDescr, selProdIndividual);
            multi.addAttribute(CISales.ProductionReportPosition.UoM, CISales.ProductionReportPosition.Quantity);
            multi.execute();
            final Map<Instance, Map<String, Object>> valueMap = new HashMap<>();

            final ProcessReport report = new ProcessReport();
            final ValuesBean values = report.getValues(_parameter);
            values.calculateCost(_parameter);
            final Set<Instance> instances = new HashSet<>();
            while (multi.next()) {
                instances.add(multi.<Instance>getSelect(selDocInst));
                final Instance prodInst = multi.getSelect(selProdInst);
                if (valueMap.containsKey(prodInst)) {
                    try {
                        final Map<String, Object> map = valueMap.get(prodInst);
                        final BigDecimal quant = (BigDecimal) qtyFrmt.parse((String) map
                                        .get(CITableSales.Sales_ProductionCostingPositionTable.quantity.name));
                        map.put(CITableSales.Sales_ProductionCostingPositionTable.quantity.name, qtyFrmt.format(multi
                                       .<BigDecimal>getAttribute(CISales.ProductionReportPosition.Quantity).add(quant)));
                    } catch (final ParseException e) {

                    }
                } else {
                    final Map<String, Object> map = new HashMap<String, Object>();
                    final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CISales.ProductionReportPosition.UoM));

                    final StringBuilder jsUoM = new StringBuilder("new Array('").append(uom.getId()).append("','").
                                    append(uom.getId()).append("','").append(uom.getName()).append("')");
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.quantity.name, qtyFrmt.format(multi
                                    .<BigDecimal>getAttribute(CISales.ProductionOrderPosition.Quantity)));
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.product.name, new String[] {
                                    multi.<Instance>getSelect(selProdInst).getOid(),
                                    multi.<String>getSelect(selProdName) });
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.productDesc.name,
                                    multi.<String>getSelect(selProdDescr));
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.uoM.name, jsUoM);

                    for (final DataBean parentBean : values.getParaMap().values()) {
                        if (parentBean.getProdInst().equals(prodInst)) {
                            map.put(CITableSales.Sales_ProductionCostingPositionTable.netUnitPrice.name,
                                            parentBean.getUnitCost());
                        }
                    }
                    valueMap.put(prodInst, map);
                }
            }
            final StringBuilder derived = new StringBuilder();
            derived.append("var pN = query('.eFapsContentDiv')[0];\n");
            for (final Instance instance : instances) {
                derived.append("domConstruct.create(\"input\", {")
                    .append(" value: \"").append(instance.getOid()).append("\", ")
                    .append(" name: \"derived\", ")
                    .append(" type: \"hidden\" ")
                    .append("}, pN);\n");
            }
            js.append(InterfaceUtils.wrapInDojoRequire(_parameter, derived, DojoLibs.QUERY, DojoLibs.DOMCONSTRUCT));

            final Set<String> noEscape = new HashSet<String>();
            noEscape.add("uoM");
            final StringBuilder onComplete = new StringBuilder();
            onComplete.append(getSetFieldReadOnlyScript(_parameter,
                            CIFormSales.Sales_ProductionCostingForm.fabricationProcess.name));
            js.append(getTableRemoveScript(_parameter, "positionTable", false, false))
                            .append(getTableAddNewRowsScript(_parameter, "positionTable", valueMap.values(),
                                            onComplete, false, false, noEscape));
        }
        return js;
    }


    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return JavaScript
     * @throws EFapsException on error
     */
    protected CharSequence getJavaScript4ProductionReport(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        final Instance inst = _parameter.getInstance();
        if (inst != null && inst.isValid() && inst.getType().isKindOf(CIFabrication.ProcessAbstract)) {
            final PrintQuery print = new PrintQuery(inst);
            print.addAttribute(CIFabrication.ProcessAbstract.Name);
            print.executeWithoutAccessCheck();

            final String name = print.<String>getAttribute(CIFabrication.ProcessAbstract.Name);

            js.append(getSetFieldValue(0, CIFormSales.Sales_ProductionReportForm.fabricationProcess.name,
                            inst.getOid(), name));

            final DecimalFormat qtyFrmt = NumberFormatter.get().getTwoDigitsFormatter();

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionOrder);
            attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink, inst);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionOrderPosition);
            queryBldr.addWhereAttrInQuery(CISales.ProductionOrderPosition.DocumentAbstractLink,
                            attrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProd = SelectBuilder.get().linkto(CISales.ProductionOrderPosition.Product);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdName = new SelectBuilder(selProd).attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdIndividual = new SelectBuilder(selProd)
                                        .attribute(CIProducts.ProductAbstract.Individual);
            final SelectBuilder selProdDescr = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(selProdInst, selProdName, selProdDescr, selProdIndividual);
            multi.addAttribute(CISales.ProductionOrderPosition.UoM, CISales.ProductionOrderPosition.Quantity);
            multi.execute();
            final Map<Instance, Map<String, Object>> valueMap = new HashMap<>();

            while (multi.next()) {
                final Instance prodInst = multi.getSelect(selProdInst);
                if (valueMap.containsKey(prodInst)) {
                    try {
                        final Map<String, Object> map = valueMap.get(prodInst);
                        final BigDecimal quant = (BigDecimal) qtyFrmt.parse((String) map
                                        .get(CITableSales.Sales_ProductionReportPositionTable.quantity.name));
                        map.put(CITableSales.Sales_ProductionReportPositionTable.quantity.name, qtyFrmt.format(multi
                                       .<BigDecimal>getAttribute(CISales.ProductionOrderPosition.Quantity).add(quant)));
                    } catch (final ParseException e) {

                    }
                } else {
                    final Map<String, Object> map = new HashMap<String, Object>();
                    final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CISales.ProductionOrderPosition.UoM));

                    final StringBuilder jsUoM = new StringBuilder("new Array('").append(uom.getId()).append("','").
                                    append(uom.getId()).append("','").append(uom.getName()).append("')");
                    map.put(CITableSales.Sales_ProductionReportPositionTable.quantity.name, qtyFrmt.format(multi
                                    .<BigDecimal>getAttribute(CISales.ProductionOrderPosition.Quantity)));
                    map.put(CITableSales.Sales_ProductionReportPositionTable.product.name, new String[] {
                                    multi.<Instance>getSelect(selProdInst).getOid(),
                                    multi.<String>getSelect(selProdName) });
                    map.put(CITableSales.Sales_ProductionReportPositionTable.productDesc.name,
                                    multi.<String>getSelect(selProdDescr));
                    map.put(CITableSales.Sales_ProductionReportPositionTable.uoM.name, jsUoM);

                    valueMap.put(prodInst, map);

                    if (Products.getSysConfig().getAttributeValueAsBoolean(ProductsSettings.ACTIVATEINDIVIDUAL)) {
                        js.append(new ProductionOrder().add4Individual(
                                        _parameter,
                                        prodInst,
                                        multi.<ProductIndividual>getSelect(selProdIndividual),
                                        null,
                                        prodInst.getOid(),
                                        multi.<String>getSelect(selProdName) + "-"
                                                        + multi.<String>getSelect(selProdDescr)));
                    }
                }
            }
            final Set<String> noEscape = new HashSet<String>();
            noEscape.add("uoM");

            final StringBuilder readOnlyFields = getSetFieldReadOnlyScript(_parameter,
                            CITableSales.Sales_ProductionReportPositionTable.quantity.name,
                            CITableSales.Sales_ProductionReportPositionTable.product.name,
                            CITableSales.Sales_ProductionReportPositionTable.productDesc.name,
                            CIFormSales.Sales_ProductionReportForm.fabricationProcess.name);
            js.append(getTableRemoveScript(_parameter, "positionTable", false, false))
                            .append(getTableAddNewRowsScript(_parameter, "positionTable", valueMap.values(),
                                            readOnlyFields, false, false, noEscape));
        }
        return js;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return JavaScript
     * @throws EFapsException on error
     */
    protected CharSequence getJavaScript4UsageReport(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        final Instance storageInst = Storage.getDefaultStorage(_parameter,
                        new UsageReport().getTypeName4SysConf(_parameter));

        final Map<Instance, BOMBean> ins2map = new Process().getInstance2BOMMap(_parameter);
        final DecimalFormat qtyFrmt = NumberFormatter.get().getTwoDigitsFormatter();

        final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();

        final StringBuilder noteBldr = new StringBuilder();
        final QueryBuilder atrtQueryBldr= new QueryBuilder(CIFabrication.Process2ProductionOrder);
        atrtQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink, _parameter.getInstance());

        final QueryBuilder queryBldr= new QueryBuilder(CISales.ProductionOrderPosition);
        queryBldr.addWhereAttrInQuery(CISales.ProductionOrderPosition.ProductionOrderLink,
                        atrtQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CISales.ProductionOrderPosition.ProductDesc, CISales.ProductionOrderPosition.Quantity);
        multi.execute();
        while (multi.next()) {
            noteBldr.append(multi.getAttribute(CISales.ProductionOrderPosition.Quantity))
                .append(" ")
                .append(multi.getAttribute(CISales.ProductionOrderPosition.ProductDesc));
        }
        js.append(getSetFieldValue(0, CIFormSales.Sales_UsageReportForm.note.name, noteBldr.toString()));

        if (_parameter.getInstance() != null && _parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CIFabrication.ProcessAbstract)) {
            final PrintQuery print = new PrintQuery(_parameter.getInstance());
            print.addAttribute(CIFabrication.ProcessAbstract.Name, CIFabrication.ProcessAbstract.Date);
            print.execute();
            final String processName = print.getAttribute(CIFabrication.Process.Name);
            final StringBuilder bldr = new StringBuilder().append(print.getAttribute(CIFabrication.ProcessAbstract.Name))
                            .append(" - ").append(print.<DateTime>getAttribute(CIFabrication.ProcessAbstract.Date)
                                        .toString("dd/MM/yyyy", Context.getThreadContext().getLocale()));

            js.append(getSetFieldValue(0, CIFormSales.Sales_UsageReportForm.fabricationProcess.name, _parameter
                        .getInstance().getOid(), processName))
                        .append(getSetFieldValue(0, CIFormSales.Sales_UsageReportForm.fabricationProcessData.name,
                                        bldr.toString()));
        }
        for (final BOMBean mat : ins2map.values()) {
            final Map<String, Object> map = new HashMap<String, Object>();
            final StringBuilder jsUoM = new StringBuilder("new Array('").append(mat.getUomID()).append("','").
                            append(mat.getUomID()).append("','").append(mat.getUom()).append("')");
            map.put(CITableSales.Sales_UsageReportPositionTable.quantity.name, qtyFrmt.format(mat.getQuantity()));
            map.put(CITableSales.Sales_UsageReportPositionTable.product.name, new String[] {
                            mat.getMatInstance().getOid(),
                            mat.getMatName() });
            map.put(CITableSales.Sales_UsageReportPositionTable.productDesc.name, mat.getMatDescription());
            map.put(CITableSales.Sales_UsageReportPositionTable.uoM.name, jsUoM);
            map.put(CITableSales.Sales_UsageReportPositionTable.quantityInStock.name,
                            getStock4ProductInStorage(_parameter, mat.getMatInstance(), storageInst));
            values.add(map);
        }
        final Set<String> noEscape = new HashSet<String>();
        noEscape.add("uoM");

        js.append(getTableRemoveScript(_parameter, "positionTable", false, false))
                        .append(getTableAddNewRowsScript(_parameter, "positionTable", values,
                                        null, false, false, noEscape));
        return js;
    }

    @Override
    public int getWeight()
    {
        return 0;
    }

    protected String getStock4ProductInStorage(final Parameter _parameter,
                                               final Instance _productinst,
                                               final Instance _storageInst)
        throws EFapsException
    {
        return new Clase().getStock4ProductInStorage(_parameter, _productinst, _storageInst);
    }

    public class Clase
        extends UsageReport
    {

        @Override
        protected String getStock4ProductInStorage(final Parameter _parameter,
                                                   final Instance _productinst,
                                                   final Instance _storageInst)
            throws EFapsException
        {
            return super.getStock4ProductInStorage(_parameter, _productinst, _storageInst);
        }

    }

}
