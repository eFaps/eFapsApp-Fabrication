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
import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIFabrication;
import org.efaps.esjp.ci.CIProducts;
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
            while (hasInd) {
                final Iterator<Map<String, ?>> iter = _values.iterator();
                hasInd = false;
                final List<Map<String, ?>> newValues = new ArrayList<>();
                while (iter.hasNext()) {
                    final Map<String, ?> map = iter.next();
                    final Instance prodInst = (Instance) map.get("productInst");
                    if (InstanceUtils.isKindOf(prodInst, CIProducts.ProductIndividualAbstract)) {
                        hasInd = true;
                        final QueryBuilder transQueryBldr = new QueryBuilder(CIProducts.TransactionIndividualInbound);
                        transQueryBldr.addWhereAttrEqValue(CIProducts.TransactionIndividualInbound.Product, prodInst);

                        final QueryBuilder pro2repQueryBldr = new QueryBuilder(CIFabrication.Process2ProductionReport);
                        pro2repQueryBldr.addWhereAttrInQuery(CIFabrication.Process2ProductionReport.ToLink,
                                        transQueryBldr.getAttributeQuery(
                                                        CIProducts.TransactionIndividualInbound.Document));

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
                        multi.addSelect(selProductInst, selProductName);
                        multi.addAttribute(CIProducts.TransactionOutbound.Quantity);
                        multi.executeWithoutAccessCheck();
                        final Map<Instance, Map<String, Object>> added = new HashMap<>();
                        final Set<Instance> duplicated = new HashSet<>();
                        while (multi.next()) {
                            final Map<String, Object> newMap = new HashMap<>();
                            newMap.putAll(map);
                            final BigDecimal quantity = multi.getAttribute(CIProducts.TransactionAbstract.Quantity);
                            final Instance productInst = multi.getSelect(selProductInst);
                            final String productName = multi.getSelect(selProductName);
                            newMap.put("productInst", productInst);
                            newMap.put("quantity", quantity);
                            newMap.put("product", productName);
                            added.put(productInst, newMap);

                            if (multi.getCurrentInstance().getType().isCIType(
                                            CIProducts.TransactionIndividualOutbound)) {
                                duplicated.add(new Product().getProduct4Individual(_parameter, productInst));
                            }
                        }
                        iter.remove();

                        for (final Entry<Instance, Map<String, Object>> entry : added.entrySet()) {
                            if (!duplicated.contains(entry.getKey())) {
                                newValues.add(entry.getValue());
                            }
                        }
                    } else {
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
