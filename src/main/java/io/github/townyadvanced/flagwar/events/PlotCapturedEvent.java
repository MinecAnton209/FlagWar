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

import com.palmergames.bukkit.towny.object.WorldCoord;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a plot's ownership changes as a result of war.
 * <p>
 * Fired for both flag-capture wins and treaty-ordered plot returns.
 * </p>
 */
public class PlotCapturedEvent extends Event {

    /** Holds the {@link HandlerList} for the {@link PlotCapturedEvent}. */
    private static final HandlerList HANDLERS = new HandlerList();
    /** The plot whose ownership changed. */
    private final WorldCoord coord;
    /** The nation that previously owned the plot. */
    private final String previousNationName;
    /** The nation that now owns the plot. */
    private final String newNationName;
    /** The reason for the transfer. */
    private final String cause;

    /**
     * Constructs the {@link PlotCapturedEvent}.
     * @param plot the plot that changed hands.
     * @param oldNationName the previous owner nation, or null for wilderness.
     * @param newOwnerNationName the new owner nation, or null for wilderness.
     * @param transferCause the cause of the transfer.
     */
    public PlotCapturedEvent(final WorldCoord plot, final String oldNationName,
                             final String newOwnerNationName, final String transferCause) {
        super();
        this.coord = plot;
        this.previousNationName = oldNationName;
        this.newNationName = newOwnerNationName;
        this.cause = transferCause;
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

    /** @return the plot that changed hands. */
    public WorldCoord getCoord() {
        return coord;
    }

    /** @return the previous owner nation, or null for wilderness. */
    public String getPreviousNationName() {
        return previousNationName;
    }

    /** @return the new owner nation, or null for wilderness. */
    public String getNewNationName() {
        return newNationName;
    }

    /** @return the cause of the transfer. */
    public String getCause() {
        return cause;
    }
}
