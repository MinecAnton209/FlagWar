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

import com.palmergames.bukkit.towny.object.WorldCoord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A proposed or signed peace treaty between two warring nations.
 */
public class Treaty {

    /** Whether both sides have accepted the treaty. */
    private boolean accepted = false;
    /** Whether the declaring nation has accepted. */
    private boolean acceptedByAttacker = false;
    /** Whether the target nation has accepted. */
    private boolean acceptedByDefender = false;
    /** Timestamp of the final acceptance, used to stamp the neutrality period. */
    private Instant signedAt;
    /** Plots the defender agrees to return to the attacker. */
    private final List<WorldCoord> plotsToReturn = new ArrayList<>();
    /** Reparations the defender agrees to pay to the attacker. */
    private double reparations;
    /** Number of days of mandated peace after signing. */
    private int neutralityDays;

    /** @return true once both sides have accepted the treaty. */
    public boolean isAccepted() {
        return accepted;
    }

    /** @param signed true once both sides have accepted the treaty. */
    public void setAccepted(final boolean signed) {
        this.accepted = signed;
        this.signedAt = signed ? Instant.now() : null;
    }

    /** @return whether the declaring nation has accepted. */
    public boolean isAcceptedByAttacker() {
        return acceptedByAttacker;
    }

    /** @param hasAccepted whether the declaring nation has accepted. */
    public void setAcceptedByAttacker(final boolean hasAccepted) {
        this.acceptedByAttacker = hasAccepted;
    }

    /** @return whether the target nation has accepted. */
    public boolean isAcceptedByDefender() {
        return acceptedByDefender;
    }

    /** @param hasAccepted whether the target nation has accepted. */
    public void setAcceptedByDefender(final boolean hasAccepted) {
        this.acceptedByDefender = hasAccepted;
    }

    /**
     * Marks one side as accepting, and flips {@link #accepted} when both sides have.
     * @param nationKey the registry key of the accepting nation.
     */
    public void acceptBy(final String nationKey) {
        acceptedByAttacker = acceptedByAttacker || nationKey.equals("attacker");
        acceptedByDefender = acceptedByDefender || nationKey.equals("defender");
        if (acceptedByAttacker && acceptedByDefender) {
            setAccepted(true);
        }
    }

    /** @return the timestamp of the final acceptance, or null if unsigned. */
    public Instant getSignedAt() {
        return signedAt;
    }

    /**
     * Adds a plot to be returned to the attacker upon signing.
     * @param coord the plot to return.
     */
    public void addPlotToReturn(final WorldCoord coord) {
        plotsToReturn.add(coord);
    }

    /** @return an unmodifiable view of the plots to return. */
    public List<WorldCoord> getPlotsToReturn() {
        return Collections.unmodifiableList(plotsToReturn);
    }

    /** @return reparations the defender pays to the attacker. */
    public double getReparations() {
        return reparations;
    }

    /** @param amount reparations the defender pays to the attacker. */
    public void setReparations(final double amount) {
        this.reparations = amount;
    }

    /** @return days of mandated peace after signing. */
    public int getNeutralityDays() {
        return neutralityDays;
    }

    /** @param days days of mandated peace after signing. */
    public void setNeutralityDays(final int days) {
        this.neutralityDays = days;
    }
}
