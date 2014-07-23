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


package org.efaps.esjp.fabrication;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.BOMBean;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.ProductBean;
import org.efaps.esjp.products.Storage;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("110c1dbe-6c62-418a-9385-e12bada57e13")
@EFapsRevision("$Rev$")
public abstract class Process_Base
    extends CommonDocument
{

    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Create create = new Create()
        {
            @Override
            protected void add2basicInsert(final Parameter _parameter,
                                           final Insert _insert)
                throws EFapsException
            {
                _insert.add(CIFabrication.Process.Name, getDocName4Create(_parameter));
            }

            @Override
            public void connect(Parameter _parameter,
                                Instance _instance)
                throws EFapsException
            {
                CreatedDoc createdDoc = new CreatedDoc(_instance);
                connect2Object(_parameter, createdDoc);
            }

        };

        return create.execute(_parameter);
    }

    public Return createUsageReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();

        final Map<Instance, BOMBean> inst2bom = getInstance2BOMMap(_parameter);
        final Insert insert = new Insert(CISales.UsageReport);
        final String name = getDocName4Create(_parameter);
        insert.add(CISales.UsageReport.Name, name);
        insert.add(CISales.UsageReport.Date, new DateTime());
        insert.add(CISales.UsageReport.Status, Status.find(CISales.UsageReportStatus.Open));
        insert.execute();
        int i = 0;
        for(BOMBean bom : inst2bom.values()) {
            final Insert insPos = new Insert(CISales.UsageReportPosition);
            insPos.add(CISales.UsageReportPosition.PositionNumber, i++);
            insPos.add(CISales.UsageReportPosition.DocumentAbstractLink, insert.getInstance());
            insPos.add(CISales.UsageReportPosition.Product, bom.getMatInstance());
            insPos.add(CISales.UsageReportPosition.ProductDesc, bom.getMatDescription());
            insPos.add(CISales.UsageReportPosition.Quantity, bom.getQuantity());
            insPos.add(CISales.UsageReportPosition.UoM, bom.getUomID());
            insPos.execute();
        }
        final Insert insert2 = new Insert(CIFabrication.Process2UsageReport);
        insert2.add(CIFabrication.Process2UsageReport.FromLink, _parameter.getInstance());
        insert2.add(CIFabrication.Process2UsageReport.ToLink, insert.getInstance());
        insert2.execute();
        return ret;
    }

    public Map<Instance, BOMBean> getInstance2BOMMap(final Parameter _parameter)
        throws EFapsException
    {
        final Instance instance = _parameter.getInstance();
        final Map<Instance, ProductBean> dataMap = new HashMap<>();

        final QueryBuilder queryBldr = new QueryBuilder(CISales.PositionAbstract);
        final QueryBuilder oAttrQueryBldr = new QueryBuilder(CISales.ProductionOrder);
        oAttrQueryBldr.addWhereAttrNotEqValue(CISales.ProductionOrder.Status,
                        Status.find(CISales.ProductionOrderStatus.Canceled));

        final QueryBuilder pAttrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionOrder);
        pAttrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink, instance);
        pAttrQueryBldr.addWhereAttrInQuery(CIFabrication.Process2ProductionOrder.ToLink,
                        oAttrQueryBldr.getAttributeQuery(CISales.ProductionOrder.ID));

        queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                        pAttrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder prodSel = SelectBuilder.get().linkto(CISales.PositionAbstract.Product);
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
                bean = new ProductBean();
                dataMap.put(prodInst, bean);
                bean.setInstance(prodInst);
                bean.setName(multi.<String>getSelect(prodNameSel));
                bean.setDescription(multi.<String>getSelect(prodDescSel));
            }
            bean.addQuantity(multi.<BigDecimal>getAttribute(CISales.PositionAbstract.Quantity));
        }

        final Map<Instance, BOMBean> inst2bom = new HashMap<>();
        for (final ProductBean prodBean : dataMap.values()) {
            final List<BOMBean> bom = prodBean.getBom();
            for (final BOMBean bean : bom) {
                BOMBean beanTmp;
                if (inst2bom.containsKey(bean.getMatInstance())) {
                    beanTmp = inst2bom.get(bean.getMatInstance());
                    beanTmp.setQuantity(beanTmp.getQuantity().add(bean.getQuantity()));
                } else {
                    beanTmp = new BOMBean();
                    inst2bom.put(bean.getMatInstance(), beanTmp);
                    beanTmp.setMatInstance(bean.getMatInstance());
                    beanTmp.setMatDescription(bean.getMatDescription());
                    beanTmp.setMatName(bean.getMatName());
                    beanTmp.setQuantity(bean.getQuantity());
                    beanTmp.setUom(bean.getUom());
                    beanTmp.setUomID(bean.getUomID());
                    beanTmp.setDimension(bean.getDimension());
                }
            }
        }
        return inst2bom;
    }

    public Return autoComplete4Storage(final Parameter _parameter)
        throws EFapsException
    {
        final Storage storage = new Storage();
        return storage.autoComplete4Storage(_parameter);
    }

}
