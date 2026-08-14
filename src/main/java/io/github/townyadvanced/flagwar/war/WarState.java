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
import io.github.townyadvanced.flagwar.config.FlagWarConfig;

import java.time.Duration;
import java.time.Instant;

/**
 * Mutable state of a war between an attacking and a defending {@link Nation}.
 * <p>
 * The attacking nation is the one that declared the war; the defending nation is the declared target.
 * A state exists from the moment a war is declared until the pair returns to {@link WarPhase#NONE}.
 * </p>
 */
public class WarState {

    /** How many seconds of combat are required before a fatigue truce triggers. */
    private static final int COMBAT_SECONDS_PER_HOUR = 3600;

    /** The nation that declared the war. */
    private final Nation attacker;
    /** The nation the war was declared against. */
    private final Nation defender;
    /** The current phase of the war. */
    private WarPhase phase;
    /** When the current phase began. */
    private Instant phaseStartedAt;
    /** Instant at which the DECLARED delay elapses and the war becomes {@link WarPhase#ACTIVE}. */
    private Instant activeAt;
    /** Instant at which an active truce ends and the war resumes. */
    private Instant truceEndsAt;
    /** Seconds of ACTIVE-phase combat accumulated, driving the fatigue auto-truce. */
    private long combatSecondsAccumulated;
    /** True when the current truce was forced by fatigue, bypassing the truce cooldown. */
    private boolean autoTruce;
    /** The peace treaty, when the war reaches {@link WarPhase#NEGOTIATING}. */
    private Treaty treaty;
    /** Instant of the last negotiation activity, driving the negotiation timeout. */
    private Instant negotiationLastActivity;

    /**
     * Constructs a fresh DECLARED war state.
     *
     * @param attackerNation the nation declaring the war.
     * @param defenderNation the nation the war is declared against.
     */
    public WarState(final Nation attackerNation, final Nation defenderNation) {
        this.attacker = attackerNation;
        this.defender = defenderNation;
        this.phase = WarPhase.DECLARED;
        this.phaseStartedAt = Instant.now();
        this.activeAt = Instant.now().plus(FlagWarConfig.getWarDeclarationDelay());
    }

    /** @return the nation that declared the war. */
    public Nation getAttacker() {
        return attacker;
    }

    /** @return the nation the war was declared against. */
    public Nation getDefender() {
        return defender;
    }

    /**
     * @return the other party of this war, relative to the given nation.
     * @param nation one of the two warring nations.
     */
    public Nation getOpponent(final Nation nation) {
        return nation == attacker ? defender : attacker;
    }

    /** @return the current phase. */
    public WarPhase getPhase() {
        return phase;
    }

    /** @param newPhase the phase to set. */
    public void setPhase(final WarPhase newPhase) {
        this.phase = newPhase;
    }

    /** @return when the current phase began. */
    public Instant getPhaseStartedAt() {
        return phaseStartedAt;
    }

    /** @param instant when the current phase began. */
    public void setPhaseStartedAt(final Instant instant) {
        this.phaseStartedAt = instant;
    }

    /** @return when the DECLARED delay elapses. */
    public Instant getActiveAt() {
        return activeAt;
    }

    /** @param instant when the DECLARED delay elapses. */
    public void setActiveAt(final Instant instant) {
        this.activeAt = instant;
    }

    /** @return when the current truce ends. */
    public Instant getTruceEndsAt() {
        return truceEndsAt;
    }

    /** @param instant when the current truce ends. */
    public void setTruceEndsAt(final Instant instant) {
        this.truceEndsAt = instant;
    }

    /**
     * @return seconds remaining until the current truce ends, or zero if no truce is in effect.
     */
    public long getTruceRemainingSeconds() {
        return truceEndsAt == null ? 0 : Math.max(0, Duration.between(Instant.now(), truceEndsAt).toSeconds());
    }

    /** @return accumulated ACTIVE-phase combat seconds, before a fatigue truce. */
    public long getCombatSecondsAccumulated() {
        return combatSecondsAccumulated;
    }

    /**
     * @return true if accumulated combat seconds have reached the configured fatigue threshold.
     */
    public boolean isFatigued() {
        long threshold = Math.round(FlagWarConfig.getFatigueCombatHoursBeforePause() * COMBAT_SECONDS_PER_HOUR);
        return threshold > 0 && combatSecondsAccumulated >= threshold;
    }

    /** @param seconds accumulated ACTIVE-phase combat seconds. */
    public void setCombatSecondsAccumulated(final long seconds) {
        this.combatSecondsAccumulated = seconds;
    }

    /** @return true when the current truce was forced by fatigue. */
    public boolean isAutoTruce() {
        return autoTruce;
    }

    /** @param forced true when the current truce was forced by fatigue. */
    public void setAutoTruce(final boolean forced) {
        this.autoTruce = forced;
    }

    /**
     * @return the instant of the last negotiation activity, or null when no activity has occurred yet.
     */
    public Instant getNegotiationLastActivity() {
        return negotiationLastActivity;
    }

    /**
     * Records a negotiation activity, resetting the negotiation timeout.
     * @param instant the timestamp of the activity.
     */
    public void setNegotiationLastActivity(final Instant instant) {
        this.negotiationLastActivity = instant;
    }

    /** @return the peace treaty, or null when none has been proposed. */
    public Treaty getTreaty() {
        return treaty;
    }

    /** @param peaceTreaty the peace treaty. */
    public void setTreaty(final Treaty peaceTreaty) {
        this.treaty = peaceTreaty;
    }

    /** @return a stable registry key for this war pair. */
    public String getKey() {
        return WarManager.stateKey(attacker, defender);
    }
}
