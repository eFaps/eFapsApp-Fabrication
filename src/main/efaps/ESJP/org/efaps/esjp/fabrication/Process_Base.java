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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.efaps.admin.program.esjp.Listener;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIFormFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.common.uisearch.Search;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.erp.AbstractWarning;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.IWarning;
import org.efaps.esjp.erp.WarningUtil;
import org.efaps.esjp.erp.listener.IOnCreateDocument;
import org.efaps.esjp.fabrication.report.ProcessReport;
import org.efaps.esjp.fabrication.report.ProcessReport_Base.DataBean;
import org.efaps.esjp.fabrication.report.ProcessReport_Base.ValuesBean;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.BOMBean;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.ProductBean;
import org.efaps.esjp.fabrication.util.Fabrication;
import org.efaps.esjp.fabrication.util.FabricationSettings;
import org.efaps.esjp.products.Storage;
import org.efaps.esjp.products.Transaction_Base;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: Process_Base.java 13395 2014-07-23 16:30:34Z
 *          luis.moreyra@efaps.org $
 */
@EFapsUUID("110c1dbe-6c62-418a-9385-e12bada57e13")
@EFapsRevision("$Rev$")
public abstract class Process_Base
    extends CommonDocument
{

    protected static String REQUESTKEY = Process.class.getName() + ".RequestKey";


    public Return registerCost(final Parameter _parameter)
        throws EFapsException
    {
        final ProcessReport report = new ProcessReport();
        final ValuesBean values = report.getValues(_parameter);
        values.calculateCost();
        for (final DataBean parentBean : values.getParaMap().values()) {
            final Insert insert = new Insert(CIProducts.ProductCost);
            insert.add(CIProducts.ProductCost.CurrencyLink, report.getCurrencyInstance());
            insert.add(CIProducts.ProductCost.ProductLink, parentBean.getProdInst());
            insert.add(CIProducts.ProductCost.ValidFrom, new DateTime().withTimeAtStartOfDay());
            insert.add(CIProducts.ProductCost.ValidUntil, new DateTime().withTimeAtStartOfDay().plusYears(10));
            insert.add(CIProducts.ProductCost.Price, parentBean.getUnitCost());
            insert.execute();
        }
        return new Return();
    }

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
            public void connect(final Parameter _parameter,
                                final Instance _instance)
                throws EFapsException
            {
                final CreatedDoc createdDoc = new CreatedDoc(_instance);
                connect2Object(_parameter, createdDoc);
            }
        };
        final Instance instance = create.basicInsert(_parameter);
        create.connect(_parameter, instance);

        final CreatedDoc createdDoc = new CreatedDoc();
        createdDoc.setInstance(instance);
        // call possible listeners
        for (final IOnCreateDocument listener : Listener.get().<IOnCreateDocument>invoke(
                        IOnCreateDocument.class)) {
            listener.afterCreate(_parameter, createdDoc);
        }

        final Return ret = new Return();
        ret.put(ReturnValues.INSTANCE, instance);
        return ret;
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
        for (final BOMBean bom : inst2bom.values()) {
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

    /**
     * Autocomplete for the field used to select a project.
     *
     * @param _parameter Parameter as passed from eFaps
     * @return Return containing map needed for an autocomplete field
     * @throws EFapsException on error
     */
    public Return autoComplete4Process(final Parameter _parameter)
        throws EFapsException
    {
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, Map<String, String>> orderMap = new TreeMap<String, Map<String, String>>();

        final String key = containsProperty(_parameter, "Key") ? getProperty(_parameter, "Key") : "OID";

        final QueryBuilder queryBldr = getQueryBldrFromProperties(_parameter);
        queryBldr.addWhereAttrMatchValue(CIFabrication.ProcessAbstract.Name, input + "*").setIgnoreCase(true);

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIFabrication.ProcessAbstract.Name);
        multi.addAttribute(key);
        multi.execute();
        while (multi.next()) {
            final String name = multi.<String>getAttribute(CIFabrication.ProcessAbstract.Name);
            final Map<String, String> map = new HashMap<String, String>();
            map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), multi.getAttribute(key).toString());
            map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
            map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), name);
            orderMap.put(name, map);
        }
        list.addAll(orderMap.values());
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return listmap for fieldupdate event
     * @throws EFapsException on error
     */
    public Return updateField4Process(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final Map<String, Object> map = new HashMap<String, Object>();
        final Instance instance = Instance.get(_parameter.getParameterValue("fabricationProcess"));
        final String projDataField = getProperty(_parameter, "Process_DataField", "fabricationProcessData");

        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIFabrication.ProcessAbstract.Name, CIFabrication.ProcessAbstract.Date);
        print.execute();

        final StringBuilder bldr = new StringBuilder().append(print.getAttribute(CIFabrication.ProcessAbstract.Name))
                        .append(" - ").append(print.<DateTime>getAttribute( CIFabrication.ProcessAbstract.Date)
                                        .toString("dd/MM/yyyy", Context.getThreadContext().getLocale()));

        map.put(projDataField, bldr.toString());
        list.add(map);
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return listmap for fieldupdate event
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return getSummaryFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        Map<Instance, String> values;
        if (Context.getThreadContext().containsRequestAttribute(Transaction_Base.REQUESTKEY)) {
            values = (Map<Instance, String>) Context.getThreadContext().getRequestAttribute(
                            Process.REQUESTKEY);
        } else {
            values = new HashMap<>();
            Context.getThreadContext().setRequestAttribute(Process.REQUESTKEY, values);

            final List<Instance> instances = (List<Instance>) _parameter.get(ParameterValues.REQUEST_INSTANCES);

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionOrder);
            attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink, instances.toArray());

            final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionOrderPosition);
            queryBldr.addWhereAttrInQuery(CISales.ProductionOrderPosition.DocumentAbstractLink,
                            attrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selProcessInst = SelectBuilder.get()
                            .linkto(CISales.ProductionOrderPosition.DocumentAbstractLink)
                            .linkfrom(CIFabrication.Process2ProductionOrder.ToLink)
                            .linkto(CIFabrication.Process2ProductionOrder.FromLink).instance();
            final SelectBuilder selProdName = SelectBuilder.get()
                            .linkto(CISales.ProductionOrderPosition.Product)
                            .attribute(CIProducts.ProductAbstract.Name);
            final SelectBuilder selProdDescr = SelectBuilder.get()
                            .linkto(CISales.ProductionOrderPosition.Product)
                            .attribute(CIProducts.ProductAbstract.Description);
            multi.addSelect(selProcessInst, selProdName, selProdDescr);
            multi.addAttribute(CISales.ProductionOrderPosition.Quantity, CISales.ProductionOrderPosition.UoM);
            multi.executeWithoutAccessCheck();
            while (multi.next()) {
                final Object processObj = multi.getSelect(selProcessInst);
                List<Instance> processInsts = new ArrayList<>();
                if (processObj instanceof Instance) {
                    processInsts.add((Instance) processObj);
                } else {
                    processInsts = (List<Instance>) processObj;
                }
                for (final Instance inst : processInsts) {
                    final StringBuilder str = new StringBuilder();
                    if (values.containsKey(inst)) {
                        str.append(values.get(inst)).append("\n ");
                    }
                    final String prodName = multi.getSelect(selProdName);
                    final String prodDescr = multi.getSelect(selProdDescr);
                    final BigDecimal quanity = multi.getAttribute(CISales.ProductionOrderPosition.Quantity);
                    final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CISales.ProductionOrderPosition.UoM));
                    str.append(DBProperties.getFormatedDBProperty(Process.class.getName() + ".Summary",
                                    new Object[] { quanity, uom.getName(), prodName, prodDescr }));
                    values.put(inst, str.toString());
                }
            }
        }
        ret.put(ReturnValues.VALUES, values.get(_parameter.getInstance()));
        return ret;
    }


    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return listmap for fieldupdate event
     * @throws EFapsException on error
     */
    public Return validate4Connect(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<IWarning> warnings = new ArrayList<IWarning>();
        if (Fabrication.getSysConfig().getAttributeValueAsBoolean(FabricationSettings.ONEPROD4PROCESS)) {
            final List<Instance> prodOrderInsts = new ArrayList<>();
            final String[] oids = _parameter.getParameterValues("selectedRow");
            if (oids != null) {
                for (final String oid : oids) {
                    final Instance docInst = Instance.get(oid);
                    if (docInst.getType().isKindOf(CISales.ProductionOrder)) {
                        prodOrderInsts.add(docInst);
                    }
                }
            }
            if (!prodOrderInsts.isEmpty()) {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionOrder);
                attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink,
                                _parameter.getInstance());
                final QueryBuilder orderQueryBldr = new QueryBuilder(CISales.ProductionOrder);
                orderQueryBldr.addWhereAttrInQuery(CISales.ProductionOrder.ID,
                                attrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));
                prodOrderInsts.addAll(orderQueryBldr.getQuery().execute());

                final QueryBuilder posQueryBldr = new QueryBuilder(CISales.ProductionOrderPosition);
                posQueryBldr.addWhereAttrEqValue(CISales.ProductionOrderPosition.DocumentAbstractLink,
                                prodOrderInsts.toArray());
                final MultiPrintQuery multi = posQueryBldr.getPrint();
                final SelectBuilder sel = SelectBuilder.get().linkto(CISales.ProductionOrderPosition.Product)
                                .instance();
                multi.addSelect(sel);
                multi.execute();
                Instance prodInst = null;
                while (multi.next()) {
                    if (prodInst == null) {
                        prodInst = multi.getSelect(sel);
                    } else if (!prodInst.equals(multi.getSelect(sel))) {
                        warnings.add(new FabricationProcessProductWarning());
                    }
                }
            }
        }
        ;
        if (warnings.isEmpty()) {
            ret.put(ReturnValues.TRUE, true);
        } else {
            ret.put(ReturnValues.SNIPLETT, WarningUtil.getHtml4Warning(warnings).toString());
            if (!WarningUtil.hasError(warnings)) {
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }


    public Return trigger4Rel2ProductionOrder(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selInst = SelectBuilder.get().linkto(CIFabrication.Process2ProductionOrder.ToLinkAbstract)
                        .instance();
        final SelectBuilder selStatus = SelectBuilder.get().linkto(CIFabrication.Process2ProductionOrder.ToLinkAbstract)
                        .attribute(CISales.ProductionOrder.Status);
        print.addSelect(selInst, selStatus);
        print.execute();

        Status newStatus = null;
        final Status currStatus = Status.get(print.<Long>getSelect(selStatus));
        if (currStatus.equals(Status.find(CISales.ProductionOrderStatus.Closed))) {
            newStatus = Status.find(CISales.ProductionOrderStatus.Open);
        } else if (currStatus.equals(Status.find(CISales.ProductionOrderStatus.Open))) {
            newStatus = Status.find(CISales.ProductionOrderStatus.Closed);
        }
        if (newStatus != null) {
            final Update update = new Update(print.<Instance>getSelect(selInst));
            update.add(CISales.ProductionOrder.Status, newStatus);
            update.executeWithoutTrigger();
        }
        return new Return();
    }



    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return listmap for fieldupdate event
     * @throws EFapsException on error
     */
    public Return searchProductionOrder(final Parameter _parameter)
        throws EFapsException
    {
        final Search search = new Search()
        {

            @Override
            protected void add2QueryBuilder(final Parameter _parameter,
                                            final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBuilder(_parameter, _queryBldr);
                if (Fabrication.getSysConfig().getAttributeValueAsBoolean(FabricationSettings.ONEPROD4PROCESS)) {
                    final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionOrder);
                    attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionOrder.FromLink,
                                    _parameter.getInstance());
                    final QueryBuilder posQueryBldr = new QueryBuilder(CISales.ProductionOrderPosition);
                    posQueryBldr.addWhereAttrInQuery(CISales.ProductionOrderPosition.DocumentAbstractLink,
                                    attrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionOrder.ToLink));

                    final QueryBuilder posQueryBldr2 = new QueryBuilder(CISales.ProductionOrderPosition);
                    posQueryBldr2.addWhereAttrInQuery(CISales.ProductionOrderPosition.Product,
                                    posQueryBldr.getAttributeQuery(CISales.ProductionOrderPosition.Product));
                    _queryBldr.addWhereAttrInQuery(CISales.ProductionOrder.ID, posQueryBldr2
                                    .getAttributeQuery(CISales.ProductionOrderPosition.DocumentAbstractLink));
                }
            }
        };
        return search.execute(_parameter);
    }

    public Return getJavaScriptUIValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.SNIPLETT,
                        InterfaceUtils.wrappInScriptTag(_parameter, getJavaScript(_parameter), true, 1500));
        return retVal;
    }


    /**
     * @param _parameter
     * @return
     */
    private CharSequence getJavaScript(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder ret = new StringBuilder();
        final String oid = _parameter.getParameterValue("selectedRow");
        if (oid != null && !oid.isEmpty()) {
            final Instance inst = Instance.get(oid);
            if (inst.isValid() && inst.getType().isCIType(CISales.ProductionOrder)) {
                final PrintQuery print = new PrintQuery(inst);
                print.addAttribute(CISales.ProductionOrder.Name);
                print.execute();
                ret.append(getSetFieldValue(0, CIFormFabrication.Fabrication_ProcessForm.productionOrder.name,
                                inst.getOid(), print.<String>getAttribute(CISales.ProductionOrder.Name)));
            }
        }
        return ret;
    }

    /**
     * Warning for amount greater zero.
     */
    public static class FabricationProcessProductWarning
        extends AbstractWarning
    {
        /**
         * Constructor.
         */
        public FabricationProcessProductWarning()
        {
            setError(true);
        }
    }

}
