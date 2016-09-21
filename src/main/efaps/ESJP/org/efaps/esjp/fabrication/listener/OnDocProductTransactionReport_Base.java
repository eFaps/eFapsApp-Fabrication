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
import org.efaps.esjp.sales.report.DocProductTransactionReport_Base.DynDocProductTransactionReport;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.util.EFapsException;

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

    @Override
    public void updateValues(final Parameter _parameter,
                             final DynDocProductTransactionReport _dynReport,
                             final Collection<Map<String, ?>> _values)
        throws EFapsException
    {
        if (Sales.REPORT_DOCPRODTRANS_FAB.get()) {
            final Map<String, Object> filter = _dynReport.getFilteredReport().getFilterMap(_parameter);
            boolean hasInd = filter.containsKey("analyzeFabrication")
                            && BooleanUtils.isTrue((Boolean) filter.get("analyzeFabrication"));
            final Set<Map<String, ?>> prevDup = new HashSet<>();
            while (hasInd) {
                final Iterator<Map<String, ?>> iter = _values.iterator();
                hasInd = false;
                final List<Map<String, ?>> newValues = new ArrayList<>();
                while (iter.hasNext()) {
                    final Map<String, ?> map = iter.next();
                    final Instance prodInst = (Instance) map.get("productInst");
                    final BigDecimal moved = (BigDecimal) map.get("quantity");
                    if (InstanceUtils.isKindOf(prodInst, CIProducts.ProductIndividualAbstract)) {
                        hasInd = true;
                        BigDecimal produced = BigDecimal.ZERO;
                        final Set<Instance> prInsts = new HashSet<>();
                        // get the produced quantity
                        final QueryBuilder pqQueryBldr = new QueryBuilder(CISales.ProductionReport);
                        pqQueryBldr.addWhereAttrNotEqValue(CISales.ProductionReport.Status,
                                        Status.find(CISales.ProductionReportStatus.Canceled));

                        final QueryBuilder pqransQueryBldr = new QueryBuilder(CIProducts.TransactionIndividualInbound);
                        pqransQueryBldr.addWhereAttrEqValue(CIProducts.TransactionIndividualInbound.Product, prodInst);
                        pqransQueryBldr.addWhereAttrInQuery(CIProducts.TransactionIndividualInbound.Document,
                                        pqQueryBldr.getAttributeQuery(CISales.ProductionReport.ID));
                        final MultiPrintQuery pqmulti = pqransQueryBldr.getPrint();
                        final SelectBuilder selPRInst = SelectBuilder.get()
                                        .linkto(CIProducts.TransactionAbstract.Document)
                                        .instance();
                        pqmulti.addSelect(selPRInst);
                        pqmulti.addAttribute(CIProducts.TransactionAbstract.Quantity);
                        pqmulti.executeWithoutAccessCheck();
                        while (pqmulti.next()) {
                            produced = produced.add(pqmulti.<BigDecimal>getAttribute(
                                            CIProducts.TransactionAbstract.Quantity));
                            prInsts.add(pqmulti.getSelect(selPRInst));
                        }
                        if (prInsts.isEmpty()) {
                            hasInd = false;
                            // prevent duplicated adding due to looping
                            if (!prevDup.contains(map)) {
                                prevDup.add(map);
                                newValues.add(map);
                            }
                        } else {
                            // get a factor for moved/produced
                            final BigDecimal factor = BigDecimal.ZERO.compareTo(produced) == 0
                                            ? BigDecimal.ONE
                                            : moved.setScale(8, RoundingMode.HALF_UP)
                                                            .divide(produced, RoundingMode.HALF_UP);

                            final QueryBuilder pro2repQueryBldr = new QueryBuilder(
                                            CIFabrication.Process2ProductionReport);
                            pro2repQueryBldr.addWhereAttrEqValue(CIFabrication.Process2ProductionReport.ToLink,
                                            prInsts.toArray());

                            final QueryBuilder pro2UsageQueryBldr = new QueryBuilder(CIFabrication.Process2UsageReport);
                            pro2UsageQueryBldr.addWhereAttrInQuery(CIFabrication.Process2UsageReport.FromLink,
                                            pro2repQueryBldr.getAttributeQuery(
                                                            CIFabrication.Process2UsageReport.FromLink));

                            final QueryBuilder usageTransQueryBldr = new QueryBuilder(CIProducts.TransactionOutbound);
                            usageTransQueryBldr.addType(CIProducts.TransactionIndividualOutbound);
                            usageTransQueryBldr.addWhereAttrInQuery(CIProducts.TransactionOutbound.Document,
                                            pro2UsageQueryBldr.getAttributeQuery(
                                                            CIFabrication.Process2UsageReport.ToLink));

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
                            final Map<Instance, Map<String, Object>> added = new HashMap<>();
                            final Set<Instance> duplicated = new HashSet<>();
                            while (multi.next()) {
                                final Instance productInst = multi.getSelect(selProductInst);
                                final BigDecimal quantity = multi.getAttribute(CIProducts.TransactionAbstract.Quantity);

                                final Map<String, Object> newMap;
                                if (added.containsKey(productInst)) {
                                    newMap = added.get(productInst);
                                } else {
                                    newMap = new HashMap<>();
                                    newMap.putAll(map);
                                    final String productName = multi.getSelect(selProductName);
                                    final String productDescr = multi.getSelect(selProductDescr);
                                    newMap.put("productInst", productInst);
                                    newMap.put("product", productName);
                                    newMap.put("productDescr", productDescr);
                                    newMap.put("quantity", BigDecimal.ZERO);
                                }
                                newMap.put("quantity", ((BigDecimal) newMap.get("quantity")).add(quantity));
                                added.put(productInst, newMap);

                                if (multi.getCurrentInstance().getType().isCIType(
                                                CIProducts.TransactionIndividualOutbound)) {
                                    duplicated.add(new Product().getProduct4Individual(_parameter, productInst));
                                }
                            }
                            iter.remove();

                            for (final Entry<Instance, Map<String, Object>> entry : added.entrySet()) {
                                if (!duplicated.contains(entry.getKey())) {
                                    final Map<String, Object> lastMap = entry.getValue();
                                    lastMap.put("quantity", ((BigDecimal) lastMap.get("quantity")).multiply(factor));
                                    newValues.add(lastMap);
                                }
                            }
                        }
                    } else {
                        iter.remove();
                        newValues.add(map);
                    }
                }
                _values.addAll(newValues);
            }
        }
    }

    @Override
    public int getWeight()
    {
        return 0;
    }
}
