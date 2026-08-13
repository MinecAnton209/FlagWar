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
 * Event fired when a declared war's delay elapses and hostilities begin.
 */
public class WarStartedEvent extends Event {

    /** Holds the {@link HandlerList} for the {@link WarStartedEvent}. */
    private static final HandlerList HANDLERS = new HandlerList();
    /** The declaring nation. */
    private final Nation attacker;
    /** The target nation. */
    private final Nation defender;

    /**
     * Constructs the {@link WarStartedEvent}.
     * @param attackingNation the declaring nation.
     * @param defendingNation the target nation.
     */
    public WarStartedEvent(final Nation attackingNation, final Nation defendingNation) {
        super();
        this.attacker = attackingNation;
        this.defender = defendingNation;
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
}
