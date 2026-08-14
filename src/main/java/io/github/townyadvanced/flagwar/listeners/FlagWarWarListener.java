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

package io.github.townyadvanced.flagwar.listeners;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.NationAddEnemyEvent;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import io.github.townyadvanced.flagwar.FlagWar;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.events.CellWonEvent;
import io.github.townyadvanced.flagwar.events.PlotCapturedEvent;
import io.github.townyadvanced.flagwar.war.DiplomacyChannel;
import io.github.townyadvanced.flagwar.war.WarManager;
import io.github.townyadvanced.flagwar.war.WarPhase;
import io.github.townyadvanced.flagwar.war.WarState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Bridges Towny and chat events into the war system.
 * <p>
 * Listens for enemy declarations made outside the {@code /fw} commands, redirects delegation chat into
 * the diplomacy channel, and feeds capture results into war bookkeeping.
 * </p>
 */
public class FlagWarWarListener implements Listener {

    /**
     * Watches for enemy declarations made directly via Towny, keeping the war registry in sync.
     * @param event the Towny enemy-declaration event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNationAddEnemy(final NationAddEnemyEvent event) {
        if (!FlagWarConfig.isWarHooksEnabled()) {
            return;
        }
        Nation attacker = event.getNation();
        Nation defender = event.getEnemy();
        WarManager manager = WarManager.getInstance();
        if (manager.getState(attacker, defender) != null) {
            return;
        }
        // A Towny-side declaration becomes a DECLARED war, honouring the same delay.
        manager.declareWar(attacker, defender, null);
    }

    /**
     * Redirects a delegation member's chat into the active diplomacy channel.
     * @param event the async chat event.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDiplomacyChat(final AsyncPlayerChatEvent event) {
        DiplomacyChannel channel = WarManager.getInstance().getActiveChannel();
        if (channel == null || !channel.isOpen()) {
            return;
        }
        Player player = event.getPlayer();
        if (!channel.getMembers().contains(player.getUniqueId())) {
            return;
        }
        channel.sendMessage(player.getDisplayName(), event.getMessage());
        event.setCancelled(true);
    }

    /**
     * Accounts each captured plot toward the war and fires {@link PlotCapturedEvent} for the political core.
     * @param cellWonEvent the flag-capture victory event.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    @SuppressWarnings("unused")
    public void onCellWon(final CellWonEvent cellWonEvent) {
        if (!FlagWarConfig.isWarHooksEnabled()) {
            return;
        }
        var cell = cellWonEvent.getCellUnderAttack();
        Resident attacker = TownyAPI.getInstance().getResident(cell.getNameOfFlagOwner());
        if (attacker == null || !attacker.hasNation()) {
            return;
        }
        Nation attackingNation;
        try {
            attackingNation = attacker.getNation();
        } catch (com.palmergames.bukkit.towny.exceptions.TownyException e) {
            return;
        }
        var coord = FlagWar.cellToWorldCoordinate(cell);
        var townBlock = coord.getTownBlockOrNull();
        if (townBlock == null || !townBlock.hasTown()) {
            return;
        }
        Nation defendingNation;
        try {
            defendingNation = townBlock.getTown().getNation();
        } catch (com.palmergames.bukkit.towny.exceptions.NotRegisteredException e) {
            return;
        }
        WarManager manager = WarManager.getInstance();
        WarState state = manager.findWarBetween(attackingNation, defendingNation).orElse(null);
        if (state == null) {
            return;
        }
        manager.accumulateCombatTime(state);
        // While negotiating, captured plots become eligible for a treaty return.
        if (state.getPhase() == WarPhase.NEGOTIATING && state.getTreaty() != null) {
            state.getTreaty().addPlotToReturn(coord);
            manager.persist(state);
        }
        Bukkit.getPluginManager().callEvent(new PlotCapturedEvent(coord, defendingNation.getName(),
            attackingNation.getName(), "flag"));
    }
}
