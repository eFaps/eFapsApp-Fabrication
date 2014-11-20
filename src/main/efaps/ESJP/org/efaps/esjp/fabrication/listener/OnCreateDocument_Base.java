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

package org.efaps.esjp.fabrication.listener;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIFormFabrication;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.ci.CITableSales;
import org.efaps.esjp.common.listener.ITypedClass;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.listener.IOnCreateDocument;
import org.efaps.esjp.fabrication.Process;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.BOMBean;
import org.efaps.esjp.sales.document.UsageReport;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: OnCreateDocument_Base.java 13395 2014-07-23 16:30:34Z
 *          luis.moreyra@efaps.org $
 */
@EFapsUUID("da56cbac-c191-4d9f-9c12-ffab92c8eefb")
@EFapsRevision("$Rev$")
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
            }
        }
        return ret;
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
        if (inst.getType().isKindOf(CIFabrication.ProcessAbstract)) {
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
            final SelectBuilder selProdDescr = new SelectBuilder(selProd)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(selProdInst, selProdName, selProdDescr);
            multi.addAttribute(CISales.ProductionOrderPosition.UoM, CISales.ProductionOrderPosition.Quantity);
            multi.execute();
            final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();

            while (multi.next()) {
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

                values.add(map);
            }
            final Set<String> noEscape = new HashSet<String>();
            noEscape.add("uoM");

            final StringBuilder readOnlyFields = getSetFieldReadOnlyScript(_parameter,
                            CITableSales.Sales_ProductionReportPositionTable.quantity.name,
                            CITableSales.Sales_ProductionReportPositionTable.product.name,
                            CITableSales.Sales_ProductionReportPositionTable.productDesc.name,
                            CIFormSales.Sales_ProductionReportForm.fabricationProcess.name);
            js.append(getTableRemoveScript(_parameter, "positionTable", false, false))
                            .append(getTableAddNewRowsScript(_parameter, "positionTable", values,
                                            readOnlyFields, false, false, noEscape));
        }
        return js;
    }

    protected CharSequence getJavaScript4UsageReport(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        final String storage = _parameter
                        .getParameterValue(CIFormFabrication.Fabrication_ProcessTree_CreateUsageReportForm.storage.name);
        final Map<Instance, BOMBean> ins2map = new Process().getInstance2BOMMap(_parameter);
        final DecimalFormat qtyFrmt = NumberFormatter.get().getTwoDigitsFormatter();
        final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();

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
                            getStock4ProductInStorage(_parameter, mat.getMatInstance(), Instance.get(storage)));
            values.add(map);
        }
        final Set<String> noEscape = new HashSet<String>();
        noEscape.add("uoM");

        final StringBuilder readOnlyFields = getSetFieldReadOnlyScript(_parameter,
                        CITableSales.Sales_UsageReportPositionTable.quantity.name,
                        CITableSales.Sales_UsageReportPositionTable.product.name,
                        CITableSales.Sales_UsageReportPositionTable.productDesc.name);
        js.append(getTableRemoveScript(_parameter, "positionTable", false, false))
                        .append(getTableAddNewRowsScript(_parameter, "positionTable", values,
                                        readOnlyFields, false, false, noEscape));
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
