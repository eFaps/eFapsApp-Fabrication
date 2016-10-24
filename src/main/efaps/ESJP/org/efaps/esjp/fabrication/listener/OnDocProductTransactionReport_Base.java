/*
 * Copyright 2003 - 2016 The eFaps Team
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
package org.efaps.esjp.fabrication.listener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.BooleanUtils;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.products.Product;
import org.efaps.esjp.sales.listener.IOnDocProductTransactionReport;
import org.efaps.esjp.sales.report.DocProductTransactionReport_Base.DataBean;
import org.efaps.esjp.sales.report.DocProductTransactionReport_Base.DynDocProductTransactionReport;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class OnDocProductTransactionReport_Base.
 *
 * @author The eFaps Team
 */
@EFapsUUID("38830263-2f5d-4a1e-8475-081735a0d487")
@EFapsApplication("eFapsApp-Fabrication")
public abstract class OnDocProductTransactionReport_Base
    implements IOnDocProductTransactionReport
{
    /**
     * Logging instance used in this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(OnDocProductTransactionReport.class);

    /**
     * Analyze bean.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @param _dynReport the dyn report
     * @param _bean the bean
     * @return the collection< data bean>
     * @throws EFapsException on error
     */
    protected Collection<DataBean> analyzeBean(final Parameter _parameter,
                                               final DynDocProductTransactionReport _dynReport,
                                               final DataBean _bean)
        throws EFapsException
    {
        LOG.debug("Analysing : {}", _bean);
        final Collection<DataBean> ret = new ArrayList<>();

        BigDecimal produced = BigDecimal.ZERO;
        final Set<Instance> prInsts = new HashSet<>();

        // get the produced quantity
        final boolean isIndividual = (InstanceUtils.isKindOf(_bean.getProductInst(),
                        CIProducts.ProductIndividualAbstract));

        final QueryBuilder pqQueryBldr = new QueryBuilder(CISales.ProductionReport);
        pqQueryBldr.addWhereAttrNotEqValue(CISales.ProductionReport.Status, Status.find(
                        CISales.ProductionReportStatus.Canceled));

        final QueryBuilder pqransQueryBldr = new QueryBuilder(isIndividual ? CIProducts.TransactionIndividualInbound
                        : CIProducts.TransactionInbound);
        pqransQueryBldr.addWhereAttrEqValue(CIProducts.TransactionAbstract.Product, _bean.getProductInst());
        pqransQueryBldr.addWhereAttrInQuery(CIProducts.TransactionAbstract.Document, pqQueryBldr.getAttributeQuery(
                        CISales.ProductionReport.ID));
        final MultiPrintQuery pqmulti = pqransQueryBldr.getCachedPrint4Request();
        final SelectBuilder selPRInst = SelectBuilder.get().linkto(CIProducts.TransactionAbstract.Document).instance();
        pqmulti.addSelect(selPRInst);
        pqmulti.addAttribute(CIProducts.TransactionAbstract.Quantity);
        pqmulti.executeWithoutAccessCheck();
        while (pqmulti.next()) {
            produced = produced.add(pqmulti.<BigDecimal>getAttribute(CIProducts.TransactionAbstract.Quantity));
            prInsts.add(pqmulti.getSelect(selPRInst));
        }
        if (prInsts.isEmpty()) {
            ret.add(_bean);
        } else {
            // get a factor for moved/produced
            final BigDecimal factor = BigDecimal.ZERO.compareTo(produced) == 0
                            ? BigDecimal.ONE
                            : _bean.getQuantity().setScale(12, RoundingMode.HALF_UP)
                                            .divide(produced, RoundingMode.HALF_UP);
            final QueryBuilder pro2repQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionReport);
            pro2repQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionReport.ToLink, prInsts.toArray());

            final QueryBuilder pro2UsageQueryBldr = new QueryBuilder(CIFabrication.Process2UsageReport);
            pro2UsageQueryBldr.addWhereAttrInQuery(CIFabrication.Process2UsageReport.FromLink,
                            pro2repQueryBldr.getAttributeQuery(CIFabrication.Process2UsageReport.FromLink));

            final QueryBuilder usageTransQueryBldr = new QueryBuilder(CIProducts.TransactionOutbound);
            usageTransQueryBldr.addType(CIProducts.TransactionIndividualOutbound);
            usageTransQueryBldr.addWhereAttrInQuery(CIProducts.TransactionOutbound.Document,
                            pro2UsageQueryBldr.getAttributeQuery(CIFabrication.Process2UsageReport.ToLink));

            final MultiPrintQuery multi = usageTransQueryBldr.getCachedPrint4Request();

            final SelectBuilder selProduct = SelectBuilder.get().linkto(
                            CIProducts.TransactionAbstract.Product);
            final SelectBuilder selProductInst = new SelectBuilder(selProduct).instance();
            final SelectBuilder selProductName = new SelectBuilder(selProduct).attribute(
                            CIProducts.ProductAbstract.Name);
            final SelectBuilder selProductDescr = new SelectBuilder(selProduct).attribute(
                            CIProducts.ProductAbstract.Description);
            multi.addSelect(selProductInst, selProductName, selProductDescr);
            multi.addAttribute(CIProducts.TransactionOutbound.Quantity);
            multi.executeWithoutAccessCheck();
            final Map<Instance, DataBean> added = new HashMap<>();
            final Set<Instance> duplicated = new HashSet<>();
            while (multi.next()) {
                final Instance productInst = multi.getSelect(selProductInst);
                final BigDecimal quantity = multi.getAttribute(CIProducts.TransactionAbstract.Quantity);

                final DataBean dataBean;
                if (added.containsKey(productInst)) {
                    dataBean = added.get(productInst);
                } else {
                    final String productName = multi.getSelect(selProductName);
                    final String productDescr = multi.getSelect(selProductDescr);
                    dataBean = _dynReport.getBean(_parameter)
                                    .setUoM(_bean.getUoM())
                                    .setProduct(productName)
                                    .setProductDescr(productDescr)
                                    .setProductInst(productInst)
                                    .setQuantity(BigDecimal.ZERO)
                                    .setDocContact(_bean.getDocContact())
                                    .setDocName(_bean.getDocName())
                                    .setPartial(_bean.getPartial());
                }
                dataBean.setQuantity(dataBean.getQuantity().add(quantity));
                added.put(productInst, dataBean);

                if (multi.getCurrentInstance().getType().isCIType(
                                CIProducts.TransactionIndividualOutbound)) {
                    duplicated.add(new Product().getProduct4Individual(_parameter, productInst));
                }
            }
            for (final Entry<Instance, DataBean> entry : added.entrySet()) {
                if (!duplicated.contains(entry.getKey()) && !entry.getKey().equals(_bean.getProductInst())) {
                    final BigDecimal newQuantity = entry.getValue().getQuantity().multiply(factor);
                    if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        entry.getValue().setQuantity(newQuantity);
                        ret.add(entry.getValue());
                    }
                }
            }
        }
        LOG.debug("returned : {}", ret);
        return ret;
    }

    @Override
    public void updateValues(final Parameter _parameter,
                             final DynDocProductTransactionReport _dynReport,
                             final Collection<DataBean> _values)
        throws EFapsException
    {
        if (Sales.REPORT_DOCPRODTRANS_FAB.get()) {
            final Map<String, Object> filter = _dynReport.getFilteredReport().getFilterMap(_parameter);
            final boolean analyze = filter.containsKey("analyzeFabrication")
                            && BooleanUtils.isTrue((Boolean) filter.get("analyzeFabrication"));

            if (analyze) {
                boolean retry = true;
                final List<DataBean> finalBeans  = new ArrayList<>();
                final List<DataBean> loopBeans = new ArrayList<>();
                while (retry) {
                    final Iterator<DataBean> iter = loopBeans.isEmpty() ? _values.iterator() : loopBeans.iterator();
                    LOG.debug("Iteration for {} elements.",loopBeans.isEmpty() ? _values.size() : loopBeans.size());
                    final List<DataBean> beans = new ArrayList<>();
                    while (iter.hasNext()) {
                        final DataBean bean = iter.next();
                        final Collection<DataBean> newBeans = analyzeBean(_parameter, _dynReport, bean);
                        if (newBeans.size() == 1 && newBeans.iterator().next().equals(bean)) {
                            finalBeans.add(bean);
                        } else {
                            beans.addAll(newBeans);
                        }
                    }
                    loopBeans.clear();
                    loopBeans.addAll(beans);
                    retry = !loopBeans.isEmpty();
                }
                _values.clear();
                _values.addAll(finalBeans);
            }
        }
    }

    @Override
    public int getWeight()
    {
        return 0;
    }
}
