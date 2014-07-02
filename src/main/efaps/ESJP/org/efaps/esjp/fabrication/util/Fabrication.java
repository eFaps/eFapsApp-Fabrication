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

package org.efaps.esjp.fabrication.util;

import java.util.UUID;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("f69676f4-e795-4f30-8cf5-c746df81bed0")
@EFapsRevision("$Rev$")
public final class Fabrication
{
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
        return SystemConfiguration.get(UUID.fromString("660188ee-c160-44a1-879b-81595594bfa6"));
    }
}
