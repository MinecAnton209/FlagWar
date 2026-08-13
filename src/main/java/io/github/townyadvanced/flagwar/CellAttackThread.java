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

package io.github.townyadvanced.flagwar;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Town;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.objects.CellUnderAttack;
import io.github.townyadvanced.flagwar.war.WarManager;
import io.github.townyadvanced.flagwar.war.WarPhase;
import io.github.townyadvanced.flagwar.war.WarState;
import java.util.TimerTask;

/**
 * Each {@link CellUnderAttack}'s attack thread, extending the {@link TimerTask}.
 */
public class CellAttackThread extends TimerTask {

    /** Holds the relevant {@link CellUnderAttack}, assigned by the constructor. */
    private final CellUnderAttack cell;

    /**
     * Constructs the {@link CellAttackThread} for a given {@link CellUnderAttack}.
     * @param cellUnderAttack to assign the CellAttackThread to.
     */
    public CellAttackThread(final CellUnderAttack cellUnderAttack) {

        this.cell = cellUnderAttack;
    }

    /**
     * Updates the war flag within the {@link CellUnderAttack}, and if {@link CellUnderAttack#hasEnded()} becomes true,
     * runs {@link FlagWar#attackWon(CellUnderAttack)}.
     * <p>
     * During a truce, armistice, or online shortage the timer is frozen in place: the flag keeps its current phase
     * and resumes from the same point once hostilities are allowed again.
     * </p>
     */
    @Override
    public void run() {

        if (!isCombatAllowed()) {
            return;
        }
        var warState = getWarState();
        if (warState != null) {
            WarManager.getInstance().accumulateCombatTime(warState);
            // A fatigue truce may have just begun; do not advance the flag during it.
            if (warState.getPhase() != WarPhase.ACTIVE) {
                return;
            }
        }
        cell.changeFlag();
        if (cell.hasEnded()) {
            FlagWar.attackWon(cell);
        }
    }

    private WarState getWarState() {
        Town town = TownyAPI.getInstance().getTown(cell.getFlagBaseBlock().getLocation());
        if (town == null) {
            return null;
        }
        try {
            Nation defendingNation = town.getNation();
            var resident = TownyAPI.getInstance().getResident(cell.getNameOfFlagOwner());
            if (resident == null || !resident.hasNation()) {
                return null;
            }
            return WarManager.getInstance().findWarBetween(resident.getNation(), defendingNation).orElse(null);
        } catch (com.palmergames.bukkit.towny.exceptions.TownyException e) {
            return null;
        }
    }

    private boolean isCombatAllowed() {
        if (!FlagWarConfig.isWarHooksEnabled()) {
            return true;
        }
        Town town = TownyAPI.getInstance().getTown(cell.getFlagBaseBlock().getLocation());
        if (town == null) {
            return true;
        }
        try {
            Nation defendingNation = town.getNation();
            var resident = TownyAPI.getInstance().getResident(cell.getNameOfFlagOwner());
            if (resident == null || !resident.hasNation()) {
                return true;
            }
            return WarManager.getInstance().isCombatAllowed(resident.getNation(), defendingNation);
        } catch (com.palmergames.bukkit.towny.exceptions.TownyException e) {
            return true;
        }
    }
}
