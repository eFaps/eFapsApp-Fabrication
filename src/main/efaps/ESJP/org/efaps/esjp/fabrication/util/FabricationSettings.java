/*
 * Copyright 2003 - 2013 The eFaps Team
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

import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;


/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("9e6fb984-9406-41d5-a59a-6068e7e340e6")
@EFapsRevision("$Rev$")
public interface FabricationSettings
{
    /**
     * Base key.
     */
    String BASE = "org.efaps.fabrication.";

    /**
     * validate that there is only one product used per process.
     */
    String ONEPROD4PROCESS = FabricationSettings.BASE + "OneProductPerProcess";

    /**
     * validate that there is only one product used per process.
     */
    String PRODORDERCONTACT = FabricationSettings.BASE + "ProductionOrderHasContact";

    /**
     * Boolean.
     * Set which table to show.
     */
    String PRODORDERDETAIL = FabricationSettings.BASE + "ProductionOrderShowDetail";

}
