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
package org.efaps.esjp.fabrication;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.esjp.Listener;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIFormFabrication;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.ci.CITableFabrication;
import org.efaps.esjp.ci.CITableSales;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.uiform.Create;
import org.efaps.esjp.common.uisearch.Search;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.AbstractWarning;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.IWarning;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.WarningUtil;
import org.efaps.esjp.erp.listener.IOnCreateDocument;
import org.efaps.esjp.fabrication.report.ProcessReport;
import org.efaps.esjp.fabrication.report.ProcessReport_Base.DataBean;
import org.efaps.esjp.fabrication.report.ProcessReport_Base.ValuesBean;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.BOMBean;
import org.efaps.esjp.fabrication.report.ProductionOrderReport_Base.ProductBean;
import org.efaps.esjp.fabrication.util.Fabrication;
import org.efaps.esjp.products.Storage;
import org.efaps.esjp.products.Transaction_Base;
import org.efaps.esjp.sales.document.ProductionCosting;
import org.efaps.esjp.sales.document.UsageReport;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("110c1dbe-6c62-418a-9385-e12bada57e13")
@EFapsApplication("eFapsApp-Fabrication")
public abstract class Process_Base
    extends CommonDocument
{

    /** The requestkey. */
    protected static final String REQUESTKEY = Process.class.getName() + ".RequestKey";

    /**
     * Logging instance used in this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Process.class);

    /**
     * Register cost.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return registerCost(final Parameter _parameter)
        throws EFapsException
    {
        final String[] productLink = _parameter.getParameterValues(
                        CITableFabrication.Fabrication_ProcessReportRegisterCostTable.productLink.name);
        final String[] currencyLink = _parameter.getParameterValues(
                        CITableFabrication.Fabrication_ProcessReportRegisterCostTable.currencyLink.name);
        final String[] validFrom = _parameter.getParameterValues(
                        CITableFabrication.Fabrication_ProcessReportRegisterCostTable.validFrom.name);
        final String[] validUntil = _parameter.getParameterValues(
                        CITableFabrication.Fabrication_ProcessReportRegisterCostTable.validUntil.name);
        final String[] price = _parameter.getParameterValues(
                        CITableFabrication.Fabrication_ProcessReportRegisterCostTable.price.name);

        for (int idx = 0; idx < productLink.length; idx++) {
            final Insert insert = new Insert(CIProducts.ProductCost);
            insert.add(CIProducts.ProductCost.CurrencyLink, currencyLink[idx]);
            insert.add(CIProducts.ProductCost.ProductLink, productLink[idx]);
            insert.add(CIProducts.ProductCost.ValidFrom, validFrom[idx]);
            insert.add(CIProducts.ProductCost.ValidUntil, validUntil[idx]);
            insert.add(CIProducts.ProductCost.Price, price[idx]);
            insert.execute();
        }
        return new Return();
    }

    /**
     * Gets the java script4 cost ui value.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the java script4 cost ui value
     * @throws EFapsException on error
     */
    public Return getJavaScript4CostUIValue(final Parameter _parameter)
        throws EFapsException
    {

        final Collection<Map<String, Object>> maplist = new ArrayList<>();
        final ProcessReport report = new ProcessReport();
        final ValuesBean values = report.getValues(_parameter, null);

        values.calculateCost(_parameter);
        for (final DataBean parentBean : values.getParaMap().values()) {
            final Map<String, Object> map = new HashMap<>();
            maplist.add(map);
            map.put("productLink", parentBean.getProdInst().getId());
            map.put("productDesc", parentBean.getProdDescription());
            map.put("currencyLink", parentBean.getCurrencyInst().getId());
            map.put("price", parentBean.getUnitCost());
            map.put("validUntil_eFapsDate", new DateTime().plusYears(10));
        }

        final Return retVal = new Return();
        final StringBuilder js = new StringBuilder()
                        .append(getTableRemoveScript(_parameter, "costTable"))
                        .append(getTableAddNewRowsScript(_parameter, "costTable", maplist,
                                        getTableDeactivateScript(_parameter, "costTable", false, false)));
        retVal.put(ReturnValues.SNIPLETT, InterfaceUtils.wrappInScriptTag(_parameter, js, true, 1000));
        return retVal;
    }

    /**
     * Creates the.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
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
            listener.afterCreate(_parameter, createdDoc.getInstance());
        }

        final Return ret = new Return();
        ret.put(ReturnValues.INSTANCE, instance);
        return ret;
    }


    /**
     * Creates the usage report.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return createUsageReport(final Parameter _parameter)
        throws EFapsException
    {
        final UsageReport usRep = new UsageReport();
        return usRep.create(_parameter);
    }

    /**
     * Gets the instance2 bom map.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the instance2 bom map
     * @throws EFapsException on error
     */
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
                final BOMBean beanTmp;
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

    /**
     * Auto complete4 storage.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
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
        final List<Map<String, String>> list = new ArrayList<>();
        final Map<String, Map<String, String>> orderMap = new TreeMap<>();

        final String key = containsProperty(_parameter, "Key") ? getProperty(_parameter, "Key") : "OID";

        final QueryBuilder queryBldr = getQueryBldrFromProperties(_parameter);
        queryBldr.addWhereAttrMatchValue(CIFabrication.ProcessAbstract.Name, input + "*").setIgnoreCase(true);

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIFabrication.ProcessAbstract.Name);
        multi.addAttribute(key);
        multi.execute();
        while (multi.next()) {
            final String name = multi.<String>getAttribute(CIFabrication.ProcessAbstract.Name);
            final Map<String, String> map = new HashMap<>();
            map.put("eFapsAutoCompleteKEY", multi.getAttribute(key).toString());
            map.put("eFapsAutoCompleteVALUE", name);
            map.put("eFapsAutoCompleteCHOICE", name);
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
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        final Instance instance = Instance.get(_parameter.getParameterValue("fabricationProcess"));
        final String projDataField = getProperty(_parameter, "Process_DataField", "fabricationProcessData");

        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CIFabrication.ProcessAbstract.Name, CIFabrication.ProcessAbstract.Date);
        print.execute();

        final StringBuilder bldr = new StringBuilder()
                        .append(print.<String>getAttribute(CIFabrication.ProcessAbstract.Name))
                        .append(" - ").append(print.<DateTime>getAttribute(CIFabrication.ProcessAbstract.Date)
                                        .toString("dd/MM/yyyy", Context.getThreadContext().getLocale()));

        map.put(projDataField, bldr.toString());
        list.add(map);
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    /**
     * Update fields4 production order.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return updateFields4ProductionOrder(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final List<Map<String, Object>> list = new ArrayList<>();
        final Map<String, Object> map = new HashMap<>();
        final Instance instance = Instance.get(_parameter.getParameterValue(
                        CIFormFabrication.Fabrication_ProcessForm.productionOrder.name));

        final PrintQuery print = new PrintQuery(instance);
        print.addAttribute(CISales.ProductionOrder.Note);
        print.execute();

        map.put(CIFormFabrication.Fabrication_ProcessForm.note.name, print.getAttribute(CISales.ProductionOrder.Note));
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
        final Map<Instance, String> values;
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
        final List<IWarning> warnings = new ArrayList<>();
        if (Fabrication.PROCESSONEPRODUCT.get()) {
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


    /**
     * Trigger4 rel2 production order.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
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
                if (Fabrication.PROCESSONEPRODUCT.get()) {
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

    /**
     * Gets the java script ui value.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the java script ui value
     * @throws EFapsException on error
     */
    public Return getJavaScriptUIValue(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        retVal.put(ReturnValues.SNIPLETT,
                        InterfaceUtils.wrappInScriptTag(_parameter, getJavaScript(_parameter), true, 1500));
        return retVal;
    }


    /**
     * Gets the java script.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the java script
     * @throws EFapsException on error
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
                print.addAttribute(CISales.ProductionOrder.Name, CISales.ProductionOrder.Note);
                print.execute();
                ret.append(getSetFieldValue(0, CIFormFabrication.Fabrication_ProcessForm.productionOrder.name,
                                inst.getOid(), print.<String>getAttribute(CISales.ProductionOrder.Name)))
                    .append(getSetFieldValue(0, CIFormFabrication.Fabrication_ProcessForm.note.name,
                                    print.<String>getAttribute(CISales.ProductionOrder.Note)));
            } else if (inst.isValid() && inst.getType().isCIType(CISales.ProductionOrderPosition)) {
                final PrintQuery print = new PrintQuery(inst);
                final SelectBuilder selProdOrd = SelectBuilder.get()
                                .linkto(CISales.ProductionOrderPosition.ProductionOrderLink);
                final SelectBuilder selInst = new SelectBuilder(selProdOrd).instance();
                final SelectBuilder selName = new SelectBuilder(selProdOrd).attribute(CISales.ProductionOrder.Name);
                final SelectBuilder selNote = new SelectBuilder(selProdOrd).attribute(CISales.ProductionOrder.Note);
                print.addSelect(selInst, selName, selNote);
                print.execute();
                ret.append(getSetFieldValue(0, CIFormFabrication.Fabrication_ProcessForm.productionOrder.name,
                                print.<Instance>getSelect(selInst).getOid(),
                                print.<String>getSelect(selName)))
                    .append(getSetFieldValue(0, CIFormFabrication.Fabrication_ProcessForm.note.name,
                                                print.<String>getSelect(selNote)));
            }
        }
        return ret;
    }

    /**
     * Creates the production costing.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return createProductionCosting(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret;
        if (InstanceUtils.isValid(_parameter.getInstance())) {
            ret = createProductionCosting(_parameter, _parameter.getInstance());
        } else {
            ret = new Return();
            for (final Instance inst : getSelectedInstances(_parameter)) {
                createProductionCosting(ParameterUtil.clone(_parameter, ParameterValues.INSTANCE, inst), inst);
            }
        }
        return ret;
    }

    /**
     * Cancel previos production costing.
     *
     * @param _parameter the parameter
     * @param _processInstance the process instance
     * @throws EFapsException the eFaps exception
     */
    protected void cancelPreviosProductionCosting(final Parameter _parameter,
                                                  final Instance _processInstance)
        throws EFapsException
    {
        final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionCosting);
        attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionCosting.FromLink, _processInstance);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionCosting);
        queryBldr.addWhereAttrEqValue(CISales.ProductionCosting.Status,
                        Status.find(CISales.ProductionCostingStatus.Draft),
                        Status.find(CISales.ProductionCostingStatus.Open));
        queryBldr.addWhereAttrInQuery(CISales.ProductionCosting.ID,
                        attrQueryBldr.getAttributeQuery(CIFabrication.Process2ProductionCosting.ToLink));
        final InstanceQuery query = queryBldr.getQuery();
        query.executeWithoutAccessCheck();
        while (query.next()) {
            final Update update = new Update(query.getCurrentValue());
            update.add(CISales.ProductionCosting.Status, Status.find(CISales.ProductionCostingStatus.Canceled));
            update.executeWithoutAccessCheck();
        }
    }

    /**
     * Creates the production costing.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _processInstance the process instance
     * @return the return
     * @throws EFapsException on error
     */
    protected Return createProductionCosting(final Parameter _parameter,
                                             final Instance _processInstance)
        throws EFapsException
    {
        Return ret = new Return();
        if (InstanceUtils.isKindOf(_processInstance, CIFabrication.ProcessAbstract)) {
            final Parameter parameter = ParameterUtil.clone(_parameter);
            cancelPreviosProductionCosting(parameter, _processInstance);
            if (parameter.getParameterValue(CIFormSales.Sales_ProductionCostingForm.date.name) == null) {
                ParameterUtil.setParameterValues(parameter, CIFormSales.Sales_ProductionCostingForm.date.name,
                               new DateTime().toString());
            }
            ParameterUtil.setParameterValues(parameter, CIFormSales.Sales_ProductionCostingForm.fabricationProcess.name,
                            _processInstance.getOid());

            final DecimalFormat qtyFrmt = NumberFormatter.get().getTwoDigitsFormatter();
            final DecimalFormat numFrmt = NumberFormatter.get().getFrmt4UnitPrice(
                            CISales.ProductionCostingPosition.getType());

            final QueryBuilder docAttrQueryBldr = new QueryBuilder(CISales.ProductionReport);
            docAttrQueryBldr.addWhereAttrNotEqValue(CISales.ProductionReport.Status,
                            Status.find(CISales.ProductionReportStatus.Canceled));

            final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionReport);
            attrQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionReport.FromLink, _processInstance);

            final QueryBuilder queryBldr = new QueryBuilder(CISales.ProductionReportPosition);
            queryBldr.addWhereAttrInQuery(CISales.ProductionReportPosition.DocumentAbstractLink, attrQueryBldr
                            .getAttributeQuery(CIFabrication.Process2ProductionReport.ToLink));
            queryBldr.addWhereAttrInQuery(CISales.ProductionReportPosition.DocumentAbstractLink,
                            docAttrQueryBldr.getAttributeQuery(CISales.ProductionReport.ID));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = SelectBuilder.get().linkto(
                            CISales.ProductionReportPosition.DocumentAbstractLink).instance();
            final SelectBuilder selProd = SelectBuilder.get().linkto(CISales.ProductionReportPosition.Product);
            final SelectBuilder selProdInst = new SelectBuilder(selProd).instance();
            final SelectBuilder selProdIndividual = new SelectBuilder(selProd).attribute(
                            CIProducts.ProductAbstract.Individual);
            final SelectBuilder selProdDescr = new SelectBuilder(selProd).attribute(
                            CIProducts.ProductAbstract.Description);
            multi.addSelect(selDocInst, selProdInst, selProdDescr, selProdIndividual);
            multi.addAttribute(CISales.ProductionReportPosition.UoM, CISales.ProductionReportPosition.Quantity);
            multi.execute();
            final Map<Instance, Map<String, String>> valueMap = new HashMap<>();

            final ProcessReport report = new ProcessReport();
            final List<Instance> currencies = report.getCurrencies(parameter);
            final ValuesBean values = report.getValues(parameter, currencies.get(0));
            values.calculateCost(parameter);
            final Set<Instance> instances = new HashSet<>();
            while (multi.next()) {
                instances.add(multi.<Instance>getSelect(selDocInst));
                final Instance prodInst = multi.getSelect(selProdInst);
                if (valueMap.containsKey(prodInst)) {
                    try {
                        final Map<String, String> map = valueMap.get(prodInst);
                        final BigDecimal quant = (BigDecimal) qtyFrmt.parse(map.get(
                                        CITableSales.Sales_ProductionCostingPositionTable.quantity.name));
                        map.put(CITableSales.Sales_ProductionCostingPositionTable.quantity.name, qtyFrmt.format(multi
                                        .<BigDecimal>getAttribute(CISales.ProductionReportPosition.Quantity).add(
                                                        quant)));
                    } catch (final ParseException e) {
                        LOG.error("Catched ParseException", e);
                    }
                } else {
                    final Map<String, String> map = new HashMap<>();
                    final UoM uom = Dimension.getUoM(multi.<Long>getAttribute(CISales.ProductionReportPosition.UoM));

                    map.put(CITableSales.Sales_ProductionCostingPositionTable.quantity.name, qtyFrmt.format(multi
                                    .<BigDecimal>getAttribute(CISales.ProductionOrderPosition.Quantity)));
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.product.name, multi.<Instance>getSelect(
                                    selProdInst).getOid());
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.productDesc.name, multi.<String>getSelect(
                                    selProdDescr));
                    map.put(CITableSales.Sales_ProductionCostingPositionTable.uoM.name, String.valueOf(uom.getId()));

                    for (final DataBean parentBean : values.getParaMap().values()) {
                        if (parentBean.getProdInst().equals(prodInst)) {
                            map.put(CITableSales.Sales_ProductionCostingPositionTable.netUnitPrice.name, numFrmt.format(
                                            parentBean.getUnitCost()));
                        }
                    }
                    valueMap.put(prodInst, map);
                }
            }

            for (final Map<String, String> map : valueMap.values()) {
                for (final Entry<String, String> entry : map.entrySet()) {
                    ParameterUtil.addParameterValues(parameter, entry.getKey(), entry.getValue());
                }
            }

            if (!values.getParaMap().isEmpty()) {
                // more than one currency ==> force an artificial rate
                if (currencies.size() > 1) {
                    final Instance rateCurrencyInst = currencies.get(0);
                    final ValuesBean baseValues = report.getValues(parameter, Currency.getBaseCurrency());
                    baseValues.calculateCost(parameter);
                    BigDecimal rate = BigDecimal.ZERO;
                    for (final Entry<Instance, DataBean> entry : values.getParaMap().entrySet()) {
                        final DataBean baseBean = baseValues.getParaMap().get(entry.getKey());
                        final DataBean rateBean = entry.getValue();
                        if (baseBean.getCost().compareTo(BigDecimal.ZERO) > 0) {
                            rate = rate.add(rateBean.getCost().divide(baseBean.getCost(), RoundingMode.HALF_UP));
                        }
                    }
                    if (rate.compareTo(BigDecimal.ZERO) == 0) {
                        rate = BigDecimal.ONE;
                    }
                    rate = rate.divide(new BigDecimal(values.getParaMap().size()), RoundingMode.HALF_UP);
                    if (CurrencyInst.get(rateCurrencyInst).isInvert()) {
                        rate = BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP);
                    }
                    ParameterUtil.setParameterValues(parameter,
                                    CIFormSales.Sales_ProductionCostingForm.rateCurrencyId.name, String.valueOf(
                                                    rateCurrencyInst.getId()));

                    ParameterUtil.setParameterValues(parameter, CIFormSales.Sales_ProductionCostingForm.rate.name,
                                    numFrmt.format(rate));
                    ParameterUtil.setParameterValues(parameter, CIFormSales.Sales_ProductionCostingForm.rate.name
                                    + "_eFapsRateInverted", String.valueOf(CurrencyInst.get(rateCurrencyInst)
                                                    .isInvert()));
                }

                for (final Instance instance : instances) {
                    ParameterUtil.addParameterValues(parameter, "derived", instance.getOid());
                }

                ret = new ProductionCosting()
                {

                    @Override
                    protected Type getType4DocCreate(final Parameter _parameter)
                        throws EFapsException
                    {
                        return CISales.ProductionCosting.getType();
                    }
                }.create(parameter);
            }
        }
        return ret;
    }

    /**
     * Process without costing multi print.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return processWithoutCostingMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint multi = new MultiPrint()
        {

            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                super.add2QueryBldr(_parameter, _queryBldr);

                final QueryBuilder pcQueryBldr = new QueryBuilder(CISales.ProductionCosting);
                pcQueryBldr.addWhereAttrNotEqValue(CISales.ProductionCosting.Status, Status.find(
                                CISales.ProductionCostingStatus.Canceled));

                final QueryBuilder attrQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionCosting);
                attrQueryBldr.addWhereAttrInQuery(CIFabrication.Process2ProductionCosting.ToLink, pcQueryBldr
                                .getAttributeQuery(CISales.ProductionCosting.ID));

                _queryBldr.addWhereAttrNotInQuery(CIFabrication.Process.ID, attrQueryBldr.getAttributeQuery(
                                CIFabrication.Process2ProductionCosting.FromLink));
            }
        };
        return multi.execute(_parameter);
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
