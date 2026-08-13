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
 * Event fired when one side breaks an active truce, triggering betrayal consequences.
 */
public class TruceBrokenEvent extends Event {

    /** Holds the {@link HandlerList} for the {@link TruceBrokenEvent}. */
    private static final HandlerList HANDLERS = new HandlerList();
    /** The declaring nation. */
    private final Nation attacker;
    /** The target nation. */
    private final Nation defender;
    /** The nation that broke the truce. */
    private final Nation traitor;

    /**
     * Constructs the {@link TruceBrokenEvent}.
     * @param attackingNation the declaring nation.
     * @param defendingNation the target nation.
     * @param traitorNation the nation that broke the truce.
     */
    public TruceBrokenEvent(final Nation attackingNation, final Nation defendingNation,
                            final Nation traitorNation) {
        super();
        this.attacker = attackingNation;
        this.defender = defendingNation;
        this.traitor = traitorNation;
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

    /** @return the nation that broke the truce. */
    public Nation getTraitor() {
        return traitor;
    }
}
