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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.listener.ITypedClass;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.listener.IOnCreateDocument;
import org.efaps.util.EFapsException;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("da56cbac-c191-4d9f-9c12-ffab92c8eefb")
@EFapsRevision("$Rev$")
public abstract class OnCreateDocument_Base
    extends CommonDocument
    implements IOnCreateDocument
{

    /* (non-Javadoc)
     * @see org.efaps.esjp.erp.listener.IOnCreateDocument#afterCreate(org.efaps.admin.event.Parameter, org.efaps.esjp.erp.CommonDocument_Base.CreatedDoc)
     */
    @Override
    public void afterCreate(Parameter _parameter,
                            CreatedDoc _createdDoc)
        throws EFapsException
    {
        // not used
    }

    /* (non-Javadoc)
     * @see org.efaps.esjp.erp.listener.IOnCreateDocument#getJavaScript4Doc(org.efaps.esjp.common.listener.ITypedClass, org.efaps.admin.event.Parameter)
     */
    @Override
    public CharSequence getJavaScript4Doc(ITypedClass _typeClass,
                                          Parameter _parameter)
        throws EFapsException
    {
        CharSequence ret = new String();
        if(_typeClass != null){
            if(CISales.UsageReport.equals(_typeClass.getCIType())){
                ret = getJavaScript4UsageReport(_parameter);
            }
        }
        return ret;
    }

    protected CharSequence getJavaScript4UsageReport(final Parameter _parameter)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();

        return null;

    }

    @Override
    public int getWeight()
    {
        return 0;
    }
}
