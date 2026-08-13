/*
 * Copyright 2021 TownyAdvanced
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.github.townyadvanced.flagwar.war;

import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;

/**
 * Hook for an external political core to gate war-related commands.
 * <p>
 * While no provider is registered, every gate returns {@code true} and the war system works standalone.
 * A future political core implements this interface to constrain who may declare war, break truces,
 * and accept peace based on the nation's governance model.
 * </p>
 */
public interface WarPolicyProvider {

    /**
     * @param resident the resident attempting to declare war.
     * @param attacker the nation on whose behalf war would be declared.
     * @param defender the nation targeted by the declaration.
     * @return true when the resident may declare the war.
     */
    boolean canDeclareWar(Resident resident, Nation attacker, Nation defender);

    /**
     * @param resident the resident attempting to break a truce.
     * @param nation the nation on whose behalf the truce would be broken.
     * @return true when the resident may break the truce.
     */
    boolean canBreakTruce(Resident resident, Nation nation);

    /**
     * @param resident the resident attempting to accept a peace treaty.
     * @param nation the nation on whose behalf the treaty would be accepted.
     * @return true when the resident may accept the peace.
     */
    boolean canAcceptPeace(Resident resident, Nation nation);
}
