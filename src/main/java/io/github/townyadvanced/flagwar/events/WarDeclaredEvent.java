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

package io.github.townyadvanced.flagwar.events;

import com.palmergames.bukkit.towny.object.Nation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a nation declares war on another nation.
 */
public class WarDeclaredEvent extends Event {

    /** Holds the {@link HandlerList} for the {@link WarDeclaredEvent}. */
    private static final HandlerList HANDLERS = new HandlerList();
    /** The nation that declared the war. */
    private final Nation attacker;
    /** The nation the war was declared against. */
    private final Nation defender;
    /** The resident who initiated the declaration, or null for admin/system declarations. */
    private final String initiator;

    /**
     * Constructs the {@link WarDeclaredEvent}.
     * @param attackingNation the declaring nation.
     * @param defendingNation the target nation.
     * @param initiatorName the name of the resident who declared, or null.
     */
    public WarDeclaredEvent(final Nation attackingNation, final Nation defendingNation, final String initiatorName) {
        super();
        this.attacker = attackingNation;
        this.defender = defendingNation;
        this.initiator = initiatorName;
    }

    /** @return the {@link HandlerList} for the event. */
    @Override
    public @NotNull HandlerList getHandlers() {
        return getHandlerList();
    }

    /** @return {@link #HANDLERS} statically. */
    @SuppressFBWarnings("NP_NONNULL_RETURN_VIOLATION")
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** @return the declaring nation. */
    public Nation getAttacker() {
        return attacker;
    }

    /** @return the target nation. */
    public Nation getDefender() {
        return defender;
    }

    /** @return the declaring resident's name, or null for system declarations. */
    public String getInitiator() {
        return initiator;
    }
}
