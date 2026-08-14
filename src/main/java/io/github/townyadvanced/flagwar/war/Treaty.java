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
 * A peace treaty between two warring nations.
 * <p>
 * A treaty begins as an editable draft while the war is in {@link WarPhase#NEGOTIATING}: both delegations may add
 * or remove plots, set reparations and the neutrality period, or mark the draft as a white peace. Drafts are sent
 * to the other side with {@link #submit()}, signed individually per side with {@link #acceptBy(String)}, and only
 * become final when both sides have accepted. Signing freezes the terms into an immutable snapshot so the peace
 * execution cannot race later edits.
 * </p>
 */
public class Treaty {

    /** Whether the draft has been formally sent to the other delegation. */
    private boolean submitted;
    /** Whether the declaring nation has accepted the submitted draft. */
    private boolean acceptedByAttacker;
    /** Whether the target nation has accepted the submitted draft. */
    private boolean acceptedByDefender;
    /** Timestamp of the final acceptance, used to stamp the neutrality period. */
    private Instant signedAt;
    /** Plots the defender agrees to return to the attacker. */
    private final List<WorldCoord> plotsToReturn = new ArrayList<>();
    /** Reparations the defender agrees to pay to the attacker. */
    private double reparations;
    /** Number of days of mandated peace after signing. */
    private int neutralityDays;
    /** Explicit choice of an unconditional peace. */
    private boolean whitePeace;
    /** Immutable copy of the terms, taken when the treaty becomes final. */
    private List<WorldCoord> signedPlots;
    /** Reparations at the moment of signing. */
    private double signedReparations;
    /** Neutrality days at the moment of signing. */
    private int signedNeutralityDays;

    /** @return true once the draft has been submitted to the other side. */
    public boolean isSubmitted() {
        return submitted;
    }

    /** Marks the draft as formally sent to the other delegation. */
    public void submit() {
        this.submitted = true;
    }

    /** Returns a submitted draft to the editing state, clearing any acceptances. */
    public void reject() {
        this.submitted = false;
        this.acceptedByAttacker = false;
        this.acceptedByDefender = false;
    }

    /** @return whether the declaring nation has accepted the submitted draft. */
    public boolean isAcceptedByAttacker() {
        return acceptedByAttacker;
    }

    /** @param hasAccepted whether the declaring nation has accepted. */
    public void setAcceptedByAttacker(final boolean hasAccepted) {
        this.acceptedByAttacker = hasAccepted;
    }

    /** @return whether the target nation has accepted the submitted draft. */
    public boolean isAcceptedByDefender() {
        return acceptedByDefender;
    }

    /** @param hasAccepted whether the target nation has accepted. */
    public void setAcceptedByDefender(final boolean hasAccepted) {
        this.acceptedByDefender = hasAccepted;
    }

    /** @return true once both sides have accepted the submitted draft. */
    public boolean isAccepted() {
        return acceptedByAttacker && acceptedByDefender;
    }

    /**
     * Marks one side as accepting the submitted draft, stamping the signed terms once both sides agree.
     * @param nationKey the registry key of the accepting nation.
     */
    public void acceptBy(final String nationKey) {
        acceptedByAttacker = acceptedByAttacker || nationKey.equals("attacker");
        acceptedByDefender = acceptedByDefender || nationKey.equals("defender");
        if (isAccepted() && signedAt == null) {
            signedAt = Instant.now();
            signedPlots = List.copyOf(plotsToReturn);
            signedReparations = reparations;
            signedNeutralityDays = neutralityDays;
        }
    }

    /** @return the timestamp of the final acceptance, or null if unsigned. */
    public Instant getSignedAt() {
        return signedAt;
    }

    /**
     * Adds a plot to be returned to the attacker upon signing. No effect once the treaty is signed.
     * @param coord the plot to return.
     */
    public void addPlotToReturn(final WorldCoord coord) {
        if (signedAt == null && !plotsToReturn.contains(coord)) {
            plotsToReturn.add(coord);
        }
    }

    /**
     * Removes a plot from the return list. No effect once the treaty is signed.
     * @param coord the plot to return.
     * @return true when the plot was on the list.
     */
    public boolean removePlotToReturn(final WorldCoord coord) {
        return signedAt == null && plotsToReturn.remove(coord);
    }

    /** @return an unmodifiable view of the plots to return. */
    public List<WorldCoord> getPlotsToReturn() {
        return Collections.unmodifiableList(plotsToReturn);
    }

    /** @return reparations the defender pays to the attacker. */
    public double getReparations() {
        return reparations;
    }

    /**
     * Sets the reparations amount.
     * @param amount the new amount, clamped to zero when negative.
     */
    public void setReparations(final double amount) {
        this.reparations = Math.max(0, amount);
    }

    /** @return days of mandated peace after signing. */
    public int getNeutralityDays() {
        return neutralityDays;
    }

    /**
     * Sets the neutrality period.
     * @param days the number of days, clamped to zero when negative.
     */
    public void setNeutralityDays(final int days) {
        this.neutralityDays = Math.max(0, days);
    }

    /** @return true when the draft is an unconditional white peace. */
    public boolean isWhitePeace() {
        return whitePeace;
    }

    /** @param unconditional whether the draft is an unconditional white peace. */
    public void setWhitePeace(final boolean unconditional) {
        this.whitePeace = unconditional;
    }

    /** @return true when the draft carries at least one concrete condition. */
    public boolean hasConditions() {
        return !plotsToReturn.isEmpty() || reparations > 0;
    }

    /** @return the frozen plot list, or null before signing. */
    public List<WorldCoord> getSignedPlots() {
        return signedPlots;
    }

    /** @return the frozen reparations, or zero before signing. */
    public double getSignedReparations() {
        return signedReparations;
    }

    /** @return the frozen neutrality days, or zero before signing. */
    public int getSignedNeutralityDays() {
        return signedNeutralityDays;
    }
}
