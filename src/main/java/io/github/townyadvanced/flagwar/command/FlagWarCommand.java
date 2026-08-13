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

package io.github.townyadvanced.flagwar.command;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.i18n.Translate;
import io.github.townyadvanced.flagwar.util.Messaging;
import io.github.townyadvanced.flagwar.war.Treaty;
import io.github.townyadvanced.flagwar.war.WarManager;
import io.github.townyadvanced.flagwar.war.WarPhase;
import io.github.townyadvanced.flagwar.war.WarPolicyProvider;
import io.github.townyadvanced.flagwar.war.WarState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /fw} command tree for war management.
 */
public final class FlagWarCommand implements TabExecutor {

    /** Index of the optional hours argument in a {@code /fw truce} invocation. */
    private static final int TRUCE_HOURS_ARG = 3;

    /**
     * Sends a localized, prefixed message to a {@link CommandSender}.
     * @param sender the recipient.
     * @param key the translation key.
     * @param args the translation arguments.
     */
    private static void send(final CommandSender sender, final String key, final Object... args) {
        Messaging.send(sender, Translate.fromPrefixed(key, args));
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender, @NotNull final Command command,
                             @NotNull final String label, final String[] args) {
        if (args.length == 0) {
            send(sender, "war.command.usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "declare" -> cmdDeclare(sender, args);
            case "status" -> cmdStatus(sender, args);
            case "truce" -> cmdTruce(sender, args);
            case "peace" -> cmdPeace(sender, args);
            case "betray" -> cmdBetray(sender);
            case "chat" -> cmdChat(sender);
            default -> send(sender, "war.command.usage");
        }
        return true;
    }

    private void cmdDeclare(final CommandSender sender, final String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        if (!requirePermission(sender, "flagwar.declare")) {
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        if (args.length < 2) {
            send(sender, "war.command.declare.usage");
            return;
        }
        Nation attacker = resident.getNation();
        Nation defender = TownyAPI.getInstance().getNation(args[1]);
        if (defender == null) {
            send(sender, "war.error.nation-not-found", args[1]);
            return;
        }
        if (defender == attacker) {
            send(sender, "war.error.self-declare");
            return;
        }
        WarPolicyProvider provider = WarManager.getInstance().getPolicyProvider();
        if (provider != null && !provider.canDeclareWar(resident, attacker, defender)) {
            send(sender, "war.error.policy-denied");
            return;
        }
        if (WarManager.getInstance().getState(attacker, defender) != null) {
            send(sender, "war.error.already-declared", defender.getFormattedName());
            return;
        }
        WarManager.getInstance().declareWar(attacker, defender, resident);
        send(sender, "war.command.declare.done", defender.getFormattedName());
    }

    private void cmdStatus(final CommandSender sender, final String[] args) {
        Nation nation = null;
        if (args.length >= 2) {
            nation = TownyAPI.getInstance().getNation(args[1]);
        } else if (sender instanceof Player player) {
            Resident resident = TownyAPI.getInstance().getResident(player);
            if (resident != null && resident.hasNation()) {
                nation = resident.getNation();
            }
        }
        if (nation == null) {
            send(sender, "war.error.nation-not-found", args.length >= 2 ? args[1] : "unknown");
            return;
        }
        WarState state = WarManager.getInstance().getStateFor(nation);
        if (state == null) {
            send(sender, "war.status.idle", nation.getFormattedName());
            return;
        }
        WarPhase phase = state.getPhase();
        String opponent = state.getOpponent(nation).getFormattedName();
        switch (phase) {
            case DECLARED -> send(sender, "war.status.declared", opponent,
                FlagWarConfig.formatDuration(java.time.Duration.between(java.time.Instant.now(),
                    state.getActiveAt())));
            case ACTIVE -> send(sender, "war.status.activenow", opponent);
            case TRUCE -> send(sender, "war.status.truce", opponent,
                FlagWarConfig.formatDuration(java.time.Duration.ofSeconds(state.getTruceRemainingSeconds())));
            case NEGOTIATING -> send(sender, "war.status.negotiating", opponent);
            case PEACE -> send(sender, "war.status.peacenow", opponent);
            case COOLDOWN -> send(sender, "war.status.cooldown", opponent);
            default -> send(sender, "war.status.idle", nation.getFormattedName());
        }
        if (WarManager.getInstance().isMarkedAsTraitor(nation)) {
            send(sender, "war.status.traitor");
        }
    }

    private void cmdTruce(final CommandSender sender, final String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "flagwar.truce")) {
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        if (args.length < 2) {
            send(sender, "war.command.truce.usage");
            return;
        }
        Nation nation = resident.getNation();
        if (WarManager.getInstance().isSanctioned(nation)) {
            send(sender, "war.error.sanctioned");
            return;
        }
        Nation opponent = TownyAPI.getInstance().getNation(args[1]);
        if (opponent == null) {
            send(sender, "war.error.nation-not-found", args[1]);
            return;
        }
        WarState state = WarManager.getInstance().findWarBetween(nation, opponent).orElse(null);
        if (state == null) {
            send(sender, "war.error.no-war", opponent.getFormattedName());
            return;
        }
        java.time.Duration requested = args.length >= TRUCE_HOURS_ARG ? parseHours(args[TRUCE_HOURS_ARG - 1]) : null;
        boolean started = requested == null
            ? WarManager.getInstance().startTruce(state, false)
            : WarManager.getInstance().startTruce(state, false, requested);
        if (started) {
            send(sender, "war.command.truce.done", opponent.getFormattedName());
        } else {
            send(sender, "war.error.truce-denied");
        }
    }

    private static java.time.Duration parseHours(final String hours) {
        try {
            long value = Long.parseLong(hours);
            if (value <= 0) {
                return null;
            }
            return java.time.Duration.ofHours(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void cmdPeace(final CommandSender sender, final String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "flagwar.peace")) {
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        if (args.length < 2) {
            send(sender, "war.command.peace.usage");
            return;
        }
        if (args[1].equalsIgnoreCase("accept")) {
            acceptPeace(sender, resident);
            return;
        }
        Nation nation = resident.getNation();
        Nation opponent = TownyAPI.getInstance().getNation(args[1]);
        if (opponent == null) {
            send(sender, "war.error.nation-not-found", args[1]);
            return;
        }
        WarState state = WarManager.getInstance().findWarBetween(nation, opponent).orElse(null);
        if (state == null) {
            send(sender, "war.error.no-war", opponent.getFormattedName());
            return;
        }
        Treaty treaty = WarManager.getInstance().proposePeace(state);
        if (treaty != null) {
            send(sender, "war.command.peace.proposed", opponent.getFormattedName());
            WarManager.getInstance().openChannel(state);
        }
    }

    private void acceptPeace(final CommandSender sender, final Resident resident) {
        WarPolicyProvider provider = WarManager.getInstance().getPolicyProvider();
        if (provider != null && !provider.canAcceptPeace(resident, resident.getNation())) {
            send(sender, "war.error.policy-denied");
            return;
        }
        WarState state = WarManager.getInstance().getStateFor(resident.getNation());
        if (state == null || state.getPhase() != WarPhase.NEGOTIATING) {
            send(sender, "war.error.not-negotiating");
            return;
        }
        Treaty treaty = state.getTreaty();
        if (treaty == null) {
            send(sender, "war.error.no-treaty");
            return;
        }
        Nation nation = resident.getNation();
        String side = state.getAttacker() == nation ? "attacker" : "defender";
        treaty.acceptBy(side);
        if (treaty.isAccepted()) {
            if (WarManager.getInstance().signPeace(state)) {
                send(sender, "war.command.peace.signed");
                WarManager.getInstance().closeChannel();
            }
        } else {
            send(sender, "war.command.peace.accepted-wait", state.getOpponent(nation).getFormattedName());
        }
    }

    private void cmdBetray(final CommandSender sender) {
        if (!requirePlayer(sender) || !requirePermission(sender, "flagwar.betray")) {
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation nation = resident.getNation();
        WarState state = WarManager.getInstance().getStateFor(nation);
        if (state == null || state.getPhase() != WarPhase.TRUCE) {
            send(sender, "war.error.no-truce");
            return;
        }
        WarPolicyProvider provider = WarManager.getInstance().getPolicyProvider();
        if (provider != null && !provider.canBreakTruce(resident, nation)) {
            send(sender, "war.error.policy-denied");
            return;
        }
        WarManager.getInstance().breakTruce(state, nation);
        send(sender, "war.command.betray.done");
    }

    private void cmdChat(final CommandSender sender) {
        if (!requirePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation nation = resident.getNation();
        WarState state = WarManager.getInstance().getStateFor(nation);
        boolean allowsChat = state != null && (state.getPhase() == WarPhase.TRUCE
            || state.getPhase() == WarPhase.NEGOTIATING);
        if (!allowsChat) {
            send(sender, "war.error.no-chat");
            return;
        }
        WarManager.getInstance().openChannel(state);
        send(sender, "war.command.chat.joined");
    }

    private static boolean requirePlayer(final CommandSender sender) {
        if (!(sender instanceof Player)) {
            Messaging.send(sender, Translate.fromPrefixed("error.command.disabled"));
            return false;
        }
        return true;
    }

    private static boolean requirePermission(final CommandSender sender, final String permission) {
        if (!sender.hasPermission(permission)) {
            Messaging.send(sender, Translate.fromPrefixed("error.command.disabled"));
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                      @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            return Arrays.asList("declare", "status", "truce", "peace", "betray", "chat")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("status")) {
            return TownyAPI.getInstance().getNations().stream().map(Nation::getName)
                .filter(n -> n.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
