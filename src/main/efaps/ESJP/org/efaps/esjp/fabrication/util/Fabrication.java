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

package org.efaps.esjp.fabrication.util;

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.api.annotation.EFapsSysConfAttribute;
import org.efaps.api.annotation.EFapsSysConfLink;
import org.efaps.api.annotation.EFapsSystemConfiguration;
import org.efaps.esjp.admin.common.systemconfiguration.BooleanSysConfAttribute;
import org.efaps.esjp.admin.common.systemconfiguration.StringSysConfAttribute;
import org.efaps.esjp.admin.common.systemconfiguration.SysConfLink;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("f69676f4-e795-4f30-8cf5-c746df81bed0")
@EFapsApplication("eFapsApp-Fabrication")
@EFapsSystemConfiguration("660188ee-c160-44a1-879b-81595594bfa6")
public final class Fabrication
{

    /** The base. */
    public static final String BASE = "org.efaps.fabrication.";

    /** Fabrication-Configuration. */
    public static final UUID SYSCONFUUID = UUID.fromString("660188ee-c160-44a1-879b-81595594bfa6");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute ACTIVATE = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "Activate")
                    .description("Activate fabrication");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute PROCESSONEPRODUCT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "Process.OneProductOnly")
                    .description("Validate that there is only one product used per process.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final StringSysConfAttribute PROCESSPROSORDRPRT = new StringSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "Process.ProcessingOrderJasperReport")
                    .description("JasperReport.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final StringSysConfAttribute PROCESSPROSORDMIME = new StringSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "Process.ProcessingOrderMime")
                    .description("Mime for the JasperReport.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute PRODORDERCONTACT = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "ProductionOrder.HasContact")
                    .description("Permit that a production Order can have a contact assigned.");

    /** See description. */
    @EFapsSysConfAttribute
    public static final BooleanSysConfAttribute PRODORDERDETAIL = new BooleanSysConfAttribute()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "ProductionOrder.ShowDetail")
                    .description("Set which table to show.");

    /** See description. */
    @EFapsSysConfLink
    public static final SysConfLink CURRENCY4PROCESSREPORT = new SysConfLink()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "Currency4ProcessReport")
                    .description("Set which table to show.");

    /** See description. */
    @EFapsSysConfLink
    public static final SysConfLink USAGERPTSTOR4IND = new SysConfLink()
                    .sysConfUUID(SYSCONFUUID)
                    .key(BASE + "UsageReport.Storage4Individual")
                    .description("The storage to be used 4 individual products.");

    /**
     * Singelton.
     */
    private Fabrication()
    {
    }

    /**
     * @return the SystemConfigruation for ImportExport
     * @throws CacheReloadException on error
     */
    public static SystemConfiguration getSysConfig()
        throws CacheReloadException
    {
        // Fabrication-Configuration
        return SystemConfiguration.get(SYSCONFUUID);
    }
}
