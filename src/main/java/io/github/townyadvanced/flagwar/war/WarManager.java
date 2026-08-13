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

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.WorldCoord;
import com.palmergames.bukkit.towny.object.metadata.LongDataField;
import com.palmergames.bukkit.towny.object.metadata.StringDataField;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.events.PeaceSignedEvent;
import io.github.townyadvanced.flagwar.events.PlotCapturedEvent;
import io.github.townyadvanced.flagwar.events.TruceBrokenEvent;
import io.github.townyadvanced.flagwar.events.TruceStartedEvent;
import io.github.townyadvanced.flagwar.events.WarDeclaredEvent;
import io.github.townyadvanced.flagwar.events.WarStartedEvent;
import io.github.townyadvanced.flagwar.i18n.Translate;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry and state machine for all wars between nations.
 * <p>
 * A war is keyed by the pair {@code attackerNationName:defenderNationName} in declaration order, so each
 * ordered pair can hold exactly one {@link WarState}. The registry is rebuilt on demand from the metadata
 * attached to the attacking nation, so wars survive restarts without extra files.
 * </p>
 */
public final class WarManager {

    /** Metadata key on the attacker nation holding the name of the defender. */
    private static final String META_DEFENDER = "flagwar_war_defender";
    /** Metadata key on the attacker nation holding the phase name. */
    private static final String META_PHASE = "flagwar_war_phase";
    /** Metadata key on the attacker nation holding the war's activeAt timestamp. */
    private static final String META_ACTIVE_AT = "flagwar_war_active_at";
    /** Metadata key on the attacker nation holding the truceEndsAt timestamp. */
    private static final String META_TRUCE_ENDS_AT = "flagwar_war_truce_ends_at";
    /** Metadata key on the attacker nation holding preserved capture seconds. */
    private static final String META_REMAINING_CAPTURE = "flagwar_war_remaining_capture";
    /** Metadata key on the attacker nation holding accumulated combat seconds. */
    private static final String META_COMBAT_SECONDS = "flagwar_war_combat_seconds";
    /** Metadata key on a traitor nation holding until when it is marked as a betrayer. */
    private static final String META_TRAITOR_UNTIL = "flagwar_traitor_until";
    /** Metadata key on an offended nation holding until when it may attack without the delay. */
    private static final String META_VENGEANCE_UNTIL = "flagwar_vengeance_until";
    /** Metadata key on a nation holding until when it may not declare war or truces. */
    private static final String META_SANCTIONED_UNTIL = "flagwar_sanctioned_until";
    /** Metadata key on a nation holding when its last truce ended, driving the truce cooldown. */
    private static final String META_LAST_TRUCE_END = "flagwar_last_truce_end";

    /** Singleton instance. */
    private static WarManager instance;

    /** Registry of active war states, keyed by {@link #stateKey(Nation, Nation)}. */
    private final Map<String, WarState> wars = new HashMap<>();
    /** Optional external policy provider; while null every command is allowed. */
    private WarPolicyProvider policyProvider;
    /** The active diplomacy channel, if any pair is negotiating. */
    private DiplomacyChannel activeChannel;

    private WarManager() {
    }

    /** @return the singleton instance, creating it on first access. */
    public static synchronized WarManager getInstance() {
        if (instance == null) {
            instance = new WarManager();
        }
        return instance;
    }

    /** @param provider the political core hook, or null to clear it. */
    public void setPolicyProvider(final WarPolicyProvider provider) {
        this.policyProvider = provider;
    }

    /** @return the registered political core hook, or null. */
    public WarPolicyProvider getPolicyProvider() {
        return policyProvider;
    }

    /** @return the active diplomacy channel, or null when none is open. */
    public DiplomacyChannel getActiveChannel() {
        return activeChannel;
    }

    /**
     * Opens a diplomacy channel for the given war pair.
     * @param state the war whose delegations should chat.
     * @return the created or existing channel.
     */
    public DiplomacyChannel openChannel(final WarState state) {
        if (activeChannel == null || !activeChannel.isOpen()) {
            activeChannel = new DiplomacyChannel(state.getAttacker(), state.getDefender());
        }
        return activeChannel;
    }

    /** Closes and clears the active diplomacy channel. */
    public void closeChannel() {
        if (activeChannel != null) {
            activeChannel.setOpen(false);
        }
        activeChannel = null;
    }

    /**
     * Builds a stable registry key for an ordered pair of nations.
     * @param attacker the declaring nation.
     * @param defender the target nation.
     * @return the pair key.
     */
    public static String stateKey(final Nation attacker, final Nation defender) {
        return attacker.getName() + ":" + defender.getName();
    }

    /** @return all currently registered war states. */
    public Map<String, WarState> getWars() {
        return wars;
    }

    /**
     * Looks up the war state for an ordered nation pair.
     * @param attacker the declaring nation.
     * @param defender the target nation.
     * @return the war state, or null if none exists.
     */
    public WarState getState(final Nation attacker, final Nation defender) {
        return wars.get(stateKey(attacker, defender));
    }

    /**
     * Finds the war state a nation is part of.
     * @param nation the nation to search for.
     * @return the first matching war state, or null.
     */
    public WarState getStateFor(final Nation nation) {
        return wars.values().stream().filter(state -> state.getAttacker() == nation || state.getDefender() == nation)
            .findFirst().orElse(null);
    }

    /**
     * Registers a new war from a declaration.
     * <p>
     * Persists the state onto the attacker nation's metadata so it survives restarts, adds the enemy pair
     * in Towny, and fires {@link WarDeclaredEvent}.
     * </p>
     *
     * @param attacker the declaring nation.
     * @param defender the target nation.
     * @param initiator the resident who declared, or null for a system declaration.
     */
    public void declareWar(final Nation attacker, final Nation defender, final Resident initiator) {
        String initiatorName = initiator == null ? null : initiator.getName();
        WarDeclaredEvent event = new WarDeclaredEvent(attacker, defender, initiatorName);
        Bukkit.getPluginManager().callEvent(event);

        if (attacker.hasEnemy(defender)) {
            attacker.removeEnemy(defender);
        }
        attacker.addEnemy(defender);
        attacker.save();

        WarState state = new WarState(attacker, defender);
        wars.put(state.getKey(), state);
        persist(state);

        String delay = FlagWarConfig.getFormattedDeclarationDelay();
        String message = Translate.fromPrefixed("war.declared.broadcast",
            attacker.getFormattedName(), defender.getFormattedName(), delay);
        TownyMessaging.sendGlobalMessage(message);
    }

    /**
     * Rebuilds the in-memory registry from nation metadata. Called on plugin enable.
     */
    public void reloadFromMetadata() {
        wars.clear();
        for (Nation nation : TownyAPI.getInstance().getNations()) {
            String defenderName = getMetaString(nation, META_DEFENDER);
            if (defenderName == null) {
                continue;
            }
            Nation defender = TownyAPI.getInstance().getNation(defenderName);
            if (defender == null) {
                continue;
            }
            WarState state = new WarState(nation, defender);
            state.setPhase(parsePhase(getMetaString(nation, META_PHASE)));
            Long activeAt = getMetaLong(nation, META_ACTIVE_AT);
            if (activeAt != null) {
                state.setActiveAt(Instant.ofEpochMilli(activeAt));
            }
            Long truceEndsAt = getMetaLong(nation, META_TRUCE_ENDS_AT);
            if (truceEndsAt != null) {
                state.setTruceEndsAt(Instant.ofEpochMilli(truceEndsAt));
            }
            Long remainingCapture = getMetaLong(nation, META_REMAINING_CAPTURE);
            if (remainingCapture != null) {
                state.setRemainingCaptureSeconds(remainingCapture);
            }
            Long combatSeconds = getMetaLong(nation, META_COMBAT_SECONDS);
            if (combatSeconds != null) {
                state.setCombatSecondsAccumulated(combatSeconds);
            }
            wars.put(state.getKey(), state);
        }
    }

    private WarPhase parsePhase(final String phaseName) {
        if (phaseName == null) {
            return WarPhase.DECLARED;
        }
        try {
            return WarPhase.valueOf(phaseName);
        } catch (IllegalArgumentException e) {
            return WarPhase.DECLARED;
        }
    }

    /**
     * Applies the state's current phase to the attacker nation's metadata.
     * @param state the state to persist.
     */
    public void persist(final WarState state) {
        Nation attacker = state.getAttacker();
        setMetaString(attacker, META_DEFENDER, state.getDefender().getName());
        setMetaString(attacker, META_PHASE, state.getPhase().name());
        setMetaLong(attacker, META_ACTIVE_AT, epoch(state.getActiveAt()));
        setMetaLong(attacker, META_TRUCE_ENDS_AT, epoch(state.getTruceEndsAt()));
        setMetaLong(attacker, META_REMAINING_CAPTURE, state.getRemainingCaptureSeconds());
        setMetaLong(attacker, META_COMBAT_SECONDS, state.getCombatSecondsAccumulated());
        attacker.save();
    }

    private static Long epoch(final Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * Removes a war pair from the registry and clears the attacker nation's war metadata.
     * @param state the state to end.
     */
    public void endWar(final WarState state) {
        clearWarMetadata(state.getAttacker());
        wars.remove(state.getKey());
    }

    private static void clearWarMetadata(final Nation attacker) {
        attacker.removeMetaData(META_DEFENDER);
        attacker.removeMetaData(META_PHASE);
        attacker.removeMetaData(META_ACTIVE_AT);
        attacker.removeMetaData(META_TRUCE_ENDS_AT);
        attacker.removeMetaData(META_REMAINING_CAPTURE);
        attacker.removeMetaData(META_COMBAT_SECONDS);
        attacker.save();
    }

    /**
     * Advances any war whose scheduled phase transition has elapsed, and expires stale one-off markers.
     * Called periodically by the plugin ticker.
     */
    public void tick() {
        for (WarState state : new HashMap<>(wars).values()) {
            tickPhase(state);
        }
    }

    private void tickPhase(final WarState state) {
        WarPhase phase = state.getPhase();
        if (phase == WarPhase.DECLARED && state.getActiveAt() != null
            && !Instant.now().isBefore(state.getActiveAt())) {
            state.setPhase(WarPhase.ACTIVE);
            state.setPhaseStartedAt(Instant.now());
            persist(state);
            Bukkit.getPluginManager().callEvent(new WarStartedEvent(state.getAttacker(), state.getDefender()));
            TownyMessaging.sendGlobalMessage(Translate.fromPrefixed("war.started.broadcast",
                state.getAttacker().getFormattedName(), state.getDefender().getFormattedName()));
        } else if (phase == WarPhase.TRUCE && state.getTruceEndsAt() != null
            && !Instant.now().isBefore(state.getTruceEndsAt())) {
            recordTruceEnd(state);
            state.setPhase(WarPhase.ACTIVE);
            state.setTruceEndsAt(null);
            state.setAutoTruce(false);
            state.setPhaseStartedAt(Instant.now());
            persist(state);
            TownyMessaging.sendGlobalMessage(Translate.fromPrefixed("war.truce.ended.broadcast",
                state.getAttacker().getFormattedName(), state.getDefender().getFormattedName()));
        } else if (phase == WarPhase.PEACE && state.getTreaty() != null
            && state.getTreaty().getSignedAt() != null
            && !Instant.now().isBefore(state.getTreaty().getSignedAt()
                .plus(Duration.ofDays(state.getTreaty().getNeutralityDays())))) {
            state.setPhase(WarPhase.COOLDOWN);
            state.setPhaseStartedAt(Instant.now());
            persist(state);
        } else if (phase == WarPhase.COOLDOWN
            && !Instant.now().isBefore(state.getPhaseStartedAt().plus(FlagWarConfig.getPeaceCooldown()))) {
            endWar(state);
        }
    }

    /**
     * Decides whether a flag may be placed by the attacking nation against the defending nation's plot.
     * @param attackingNation the nation placing the flag.
     * @param defendingNation the nation owning the plot.
     * @return true when the war phase allows hostilities.
     */
    public boolean isFlagPlacementAllowed(final Nation attackingNation, final Nation defendingNation) {
        if (!FlagWarConfig.isWarHooksEnabled()) {
            return true;
        }
        if (hasVengeanceWindow(attackingNation)) {
            return true;
        }
        WarState state = findWarBetween(attackingNation, defendingNation).orElse(null);
        if (state == null) {
            return false;
        }
        return state.getPhase() == WarPhase.ACTIVE && !isArmisticeNow();
    }

    /**
     * @return true when the current server time falls inside a configured armistice window.
     */
    public boolean isArmisticeNow() {
        if (!FlagWarConfig.isArmisticeEnabled()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now();
        for (var window : FlagWarConfig.getArmisticeWindows()) {
            if (window.getDays().contains(now.getDayOfWeek().name().toLowerCase())) {
                if (!now.toLocalTime().isBefore(window.getStart())
                    && now.toLocalTime().isBefore(window.getEnd())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a capture timer should advance this tick.
     * @param attackingNation the nation that placed the flag.
     * @param defendingNation the nation owning the plot.
     * @return true when combat is allowed to progress.
     */
    public boolean isCombatAllowed(final Nation attackingNation, final Nation defendingNation) {
        if (!FlagWarConfig.isWarHooksEnabled()) {
            return true;
        }
        WarState state = findWarBetween(attackingNation, defendingNation).orElse(null);
        if (state == null) {
            return false;
        }
        return state.getPhase() == WarPhase.ACTIVE && !isArmisticeNow() && isNationOnline(attackingNation)
            && isNationOnline(defendingNation);
    }

    /**
     * Records a second of active combat for the war, triggering a fatigue truce at the threshold.
     * @param state the war state.
     */
    public void accumulateCombatTime(final WarState state) {
        if (state.getPhase() != WarPhase.ACTIVE) {
            return;
        }
        long accumulated = state.getCombatSecondsAccumulated() + 1;
        state.setCombatSecondsAccumulated(accumulated);
        if (state.isFatigued()) {
            startTruce(state, true);
        } else {
            persist(state);
        }
    }

    /**
     * Begins a truce between the two warring nations, using the default configured duration.
     * @param state the war state.
     * @param auto true when forced by fatigue or system rules rather than a request.
     * @return true when the truce started.
     */
    public boolean startTruce(final WarState state, final boolean auto) {
        return startTruce(state, auto, auto
            ? FlagWarConfig.getFatiguePauseDuration() : FlagWarConfig.getTruceDefaultDuration());
    }

    /**
     * Begins a truce between the two warring nations with an explicit duration.
     * <p>
     * A requested duration is clamped to the configured {@code truce.max-duration}.
     * </p>
     *
     * @param state the war state.
     * @param auto true when forced by fatigue or system rules rather than a request.
     * @param duration the truce length.
     * @return true when the truce started.
     */
    public boolean startTruce(final WarState state, final boolean auto, final Duration duration) {
        if (state.getPhase() != WarPhase.ACTIVE) {
            return false;
        }
        if (!auto && isTruceCooldownActive(state.getAttacker())) {
            return false;
        }
        Duration clamped = duration;
        if (!auto && duration.compareTo(FlagWarConfig.getTruceMaxDuration()) > 0) {
            clamped = FlagWarConfig.getTruceMaxDuration();
        }
        state.setPhase(WarPhase.TRUCE);
        state.setTruceEndsAt(Instant.now().plus(clamped));
        state.setPhaseStartedAt(Instant.now());
        state.setAutoTruce(auto);
        persist(state);
        Bukkit.getPluginManager().callEvent(new TruceStartedEvent(state.getAttacker(), state.getDefender(), auto));

        String message = auto
            ? Translate.fromPrefixed("war.truce.fatigue", state.getAttacker().getFormattedName(),
                state.getDefender().getFormattedName())
            : Translate.fromPrefixed("war.truce.started", state.getAttacker().getFormattedName(),
                state.getDefender().getFormattedName(), FlagWarConfig.formatDuration(clamped));
        TownyMessaging.sendGlobalMessage(message);
        return true;
    }

    private boolean isTruceCooldownActive(final Nation nation) {
        Long lastTruceEnd = getMetaLong(nation, META_LAST_TRUCE_END);
        if (lastTruceEnd == null) {
            return false;
        }
        return Instant.now().isBefore(Instant.ofEpochMilli(lastTruceEnd)
            .plus(FlagWarConfig.getTruceCooldown()));
    }

    private void recordTruceEnd(final WarState state) {
        // Fatigue truces are forced and do not put the pair on cooldown from requesting one later.
        if (state.isAutoTruce()) {
            return;
        }
        long end = Instant.now().toEpochMilli();
        setMetaLong(state.getAttacker(), META_LAST_TRUCE_END, end);
        setMetaLong(state.getDefender(), META_LAST_TRUCE_END, end);
        state.getAttacker().save();
        state.getDefender().save();
    }

    /**
     * Breaks an active truce, marking the betraying nation and granting the offended side a vengeance window.
     * @param state the war state.
     * @param traitor the nation breaking the truce.
     */
    public void breakTruce(final WarState state, final Nation traitor) {
        recordTruceEnd(state);
        state.setPhase(WarPhase.ACTIVE);
        state.setTruceEndsAt(null);
        state.setPhaseStartedAt(Instant.now());
        state.setAutoTruce(false);
        persist(state);

        Nation offended = state.getOpponent(traitor);
        Instant now = Instant.now();
        setMetaLong(traitor, META_TRAITOR_UNTIL, now.plus(FlagWarConfig.getTraitorMarkDuration()).toEpochMilli());
        setMetaLong(traitor, META_SANCTIONED_UNTIL, now.plus(FlagWarConfig.getSanctionsDuration()).toEpochMilli());
        setMetaLong(offended, META_VENGEANCE_UNTIL, now.plus(FlagWarConfig.getVengeanceWindow()).toEpochMilli());
        traitor.save();
        offended.save();

        Bukkit.getPluginManager().callEvent(new TruceBrokenEvent(state.getAttacker(), state.getDefender(), traitor));
        TownyMessaging.sendGlobalMessage(Translate.fromPrefixed("war.betray.broadcast",
            traitor.getFormattedName(), offended.getFormattedName()));
    }

    /**
     * @param nation the nation to check.
     * @return true when the nation is marked as a betrayer and the mark has not expired.
     */
    public boolean isMarkedAsTraitor(final Nation nation) {
        Long until = getMetaLong(nation, META_TRAITOR_UNTIL);
        return until != null && Instant.now().isBefore(Instant.ofEpochMilli(until));
    }

    /**
     * @param nation the nation to check.
     * @return true when the nation is under sanctions and may not declare war or truces.
     */
    public boolean isSanctioned(final Nation nation) {
        Long until = getMetaLong(nation, META_SANCTIONED_UNTIL);
        return until != null && Instant.now().isBefore(Instant.ofEpochMilli(until));
    }

    /**
     * @param nation the nation to check.
     * @return true when the nation has an active vengeance window, allowing flags despite non-ACTIVE phases.
     */
    public boolean hasVengeanceWindow(final Nation nation) {
        Long until = getMetaLong(nation, META_VENGEANCE_UNTIL);
        return until != null && Instant.now().isBefore(Instant.ofEpochMilli(until));
    }

    /**
     * Begins peace negotiations for the war, creating a fresh treaty.
     * @param state the war state.
     * @return the created treaty.
     */
    public Treaty proposePeace(final WarState state) {
        Treaty treaty = new Treaty();
        state.setTreaty(treaty);
        state.setPhase(WarPhase.NEGOTIATING);
        state.setPhaseStartedAt(Instant.now());
        persist(state);
        TownyMessaging.sendGlobalMessage(Translate.fromPrefixed("war.negotiating.broadcast",
            state.getAttacker().getFormattedName(), state.getDefender().getFormattedName()));
        return treaty;
    }

    /**
     * Executes a fully accepted treaty: transfers plots, pays reparations, and sets the neutrality phase.
     * @param state the war state.
     * @return true when the peace was signed.
     */
    public boolean signPeace(final WarState state) {
        Treaty treaty = state.getTreaty();
        if (treaty == null || !treaty.isAccepted()) {
            return false;
        }
        executeTreaty(state, treaty);
        state.setPhase(WarPhase.PEACE);
        state.setPhaseStartedAt(Instant.now());
        persist(state);
        Bukkit.getPluginManager().callEvent(new PeaceSignedEvent(state.getAttacker(), state.getDefender(), treaty));
        TownyMessaging.sendGlobalMessage(Translate.fromPrefixed("war.peace.signed",
            state.getAttacker().getFormattedName(), state.getDefender().getFormattedName()));
        return true;
    }

    private void executeTreaty(final WarState state, final Treaty treaty) {
        for (WorldCoord coord : treaty.getPlotsToReturn()) {
            try {
                var townBlock = coord.getTownBlock();
                var oldNation = townBlock.getTown().getNation();
                if (oldNation == state.getDefender()) {
                    townBlock.setTown(state.getAttacker().getCapital());
                    townBlock.save();
                    Bukkit.getPluginManager().callEvent(new PlotCapturedEvent(coord,
                        state.getDefender().getName(), state.getAttacker().getName(), "treaty"));
                }
            } catch (NotRegisteredException | NullPointerException ignored) {
                // Plot was unclaimed or its town has no nation since the treaty was drafted.
            }
        }
        if (treaty.getReparations() > 0) {
            state.getDefender().getAccount().withdraw(treaty.getReparations(),
                "War - Treaty Reparations to " + state.getAttacker().getName());
            state.getAttacker().getAccount().deposit(treaty.getReparations(),
                "War - Treaty Reparations from " + state.getDefender().getName());
        }
    }

    private boolean isNationOnline(final Nation nation) {
        int required = FlagWarConfig.getMinPlayersOnlineInNationForWar();
        if (required <= 0) {
            return true;
        }
        return TownyAPI.getInstance().getOnlinePlayersInNation(nation).size() >= required;
    }

    private static void setMetaString(final Nation nation, final String key, final String value) {
        if (value == null) {
            nation.removeMetaData(key);
            return;
        }
        nation.addMetaData(new StringDataField(key, value));
    }

    private static String getMetaString(final Nation nation, final String key) {
        StringDataField field = nation.getMetadata(key, StringDataField.class);
        return field == null ? null : field.getValue();
    }

    private static void setMetaLong(final Nation nation, final String key, final Long value) {
        if (value == null) {
            nation.removeMetaData(key);
            return;
        }
        nation.addMetaData(new LongDataField(key, value));
    }

    private static Long getMetaLong(final Nation nation, final String key) {
        LongDataField field = nation.getMetadata(key, LongDataField.class);
        return field == null ? null : field.getValue();
    }

    /** @return the number of registered wars. */
    public int getWarCount() {
        return wars.size();
    }

    /**
     * @param firstNation one of the two nations.
     * @param secondNation the other nation.
     * @return an Optional of the war state between the two nations, in either direction.
     */
    public Optional<WarState> findWarBetween(final Nation firstNation, final Nation secondNation) {
        WarState forward = getState(firstNation, secondNation);
        if (forward != null) {
            return Optional.of(forward);
        }
        return Optional.ofNullable(getState(secondNation, firstNation));
    }
}
