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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.esjp.ci.CIFormFabrication;
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

    /*
     * (non-Javadoc)
     * @see
     * org.efaps.esjp.erp.listener.IOnCreateDocument#afterCreate(org.efaps.admin
     * .event.Parameter, org.efaps.esjp.erp.CommonDocument_Base.CreatedDoc)
     */
    @Override
    public void afterCreate(Parameter _parameter,
                            CreatedDoc _createdDoc)
        throws EFapsException
    {
        // not used
    }

    /*
     * (non-Javadoc)
     * @see
     * org.efaps.esjp.erp.listener.IOnCreateDocument#getJavaScript4Doc(org.efaps
     * .esjp.common.listener.ITypedClass, org.efaps.admin.event.Parameter)
     */
    @Override
    public CharSequence getJavaScript4Doc(ITypedClass _typeClass,
                                          Parameter _parameter)
        throws EFapsException
    {
        CharSequence ret = new String();
        if (_typeClass != null) {
            if (CISales.UsageReport.equals(_typeClass.getCIType())) {
                ret = getJavaScript4UsageReport(_parameter);
            }
        }
        return ret;
    }

    protected CharSequence getJavaScript4UsageReport(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        final String storage = _parameter
                        .getParameterValue(CIFormFabrication.Fabrication_ProcessTree_CreateUsageReportForm.storage.name);
        final Map<Instance, BOMBean> ins2map = new Process().getInstance2BOMMap(_parameter);
        final DecimalFormat qtyFrmt = NumberFormatter.get().getTwoDigitsFormatter();
        String uomID = null;
        final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();

        for (BOMBean mat : ins2map.values()) {
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
            uomID = String.valueOf(mat.getUomID());
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
