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
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.i18n.Translate;
import org.bukkit.Bukkit;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A one-to-one diplomacy chat between two nations' delegations.
 * <p>
 * When a member of either delegation speaks, their message is redirected from the global chat to this
 * channel instead. Delegations are the nation leader plus any online residents holding the {@code flagwar.diplomat}
 * permission, capped by {@code negotiation.delegation-size} per side.
 * </p>
 */
public class DiplomacyChannel {

    /** The declaring nation. */
    private final Nation attacker;
    /** The target nation. */
    private final Nation defender;
    /** Members currently participating in the channel. */
    private final Set<UUID> members = new LinkedHashSet<>();
    /** Whether the channel is actively accepting traffic. */
    private boolean open;

    /**
     * Constructs the {@link DiplomacyChannel} for a war pair.
     * @param attackingNation the declaring nation.
     * @param defendingNation the target nation.
     */
    public DiplomacyChannel(final Nation attackingNation, final Nation defendingNation) {
        this.attacker = attackingNation;
        this.defender = defendingNation;
        this.open = true;
        populate();
    }

    private void populate() {
        addDelegation(attacker);
        addDelegation(defender);
    }

    private void addDelegation(final Nation nation) {
        members.add(getLeaderUUID(nation));
        int diplomatSlots = Math.max(0, FlagWarConfig.getDiplomacyDelegationSize() - 1);
        var leader = getLeaderUUID(nation);
        for (Resident resident : nation.getResidents()) {
            if (resident.getUUID().equals(leader)) {
                continue;
            }
            if (isDiplomat(resident)) {
                if (diplomatSlots <= 0) {
                    break;
                }
                members.add(resident.getUUID());
                diplomatSlots--;
            }
        }
    }

    private static UUID getLeaderUUID(final Nation nation) {
        return nation.getKing() == null ? nation.getCapital().getMayor().getUUID() : nation.getKing().getUUID();
    }

    private static boolean isDiplomat(final Resident resident) {
        return resident.getPlayer() != null
            && resident.getPlayer().hasPermission("flagwar.diplomat");
    }

    /** @return the currently registered member UUIDs. */
    public Set<UUID> getMembers() {
        return members;
    }

    /** @return whether the channel is open for traffic. */
    public boolean isOpen() {
        return open;
    }

    /**
     * @param firstNation one of the two nations.
     * @param secondNation the other nation.
     * @return true when this channel serves the given pair of nations.
     */
    public boolean isBetween(final Nation firstNation, final Nation secondNation) {
        return (attacker == firstNation && defender == secondNation)
            || (attacker == secondNation && defender == firstNation);
    }

    /**
     * @param channelOpen whether the channel should accept traffic.
     */
    public void setOpen(final boolean channelOpen) {
        this.open = channelOpen;
    }

    /**
     * Routes a channel message to every online member.
     * @param senderName the display name of the sender.
     * @param message the message content.
     */
    public void sendMessage(final String senderName, final String message) {
        String prefixed = FlagWarConfig.getDiplomacyChannelPrefix() + " " + senderName + ": " + message;
        for (UUID uuid : members) {
            var player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(prefixed);
            }
        }
    }

    /**
     * Sends a non-conversational system notice to all channel members.
     * @param message the notice text.
     */
    public void sendNotice(final String message) {
        String prefixed = FlagWarConfig.getDiplomacyChannelPrefix() + " " + Translate.from("war.diplomacy.notice",
            message);
        for (UUID uuid : members) {
            var player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(prefixed);
            }
        }
    }
}
