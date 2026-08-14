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
import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.WorldCoord;
import io.github.townyadvanced.flagwar.config.FlagWarConfig;
import io.github.townyadvanced.flagwar.i18n.Translate;
import io.github.townyadvanced.flagwar.util.Messaging;
import io.github.townyadvanced.flagwar.war.DiplomacyChannel;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /fw} command tree for war management.
 */
public final class FlagWarCommand implements TabExecutor {

    /** Index of the optional hours argument in a {@code /fw truce} invocation. */
    private static final int TRUCE_HOURS_ARG = 3;
    /** Index of the opponent argument in a {@code /fw treaty} or {@code /fw negotiate} invocation. */
    private static final int TREATY_ACTION_ARG = 1;
    /** Index of the action subcommand in a {@code /fw treaty} invocation. */
    private static final int TREATY_SUB_ARG = 2;
    /** Length of a {@code /fw negotiate abort <nation>} invocation. */
    private static final int NEGOTIATE_ABORT_ARGS = 3;
    /** Length of a {@code /fw peace accept <nation>} invocation. */
    private static final int PEACE_ACCEPT_ARGS = 3;
    /** Length of a {@code /fw treaty <nation> <action>} invocation. */
    private static final int TREATY_SUB_ARGS = 3;
    /** Length of a {@code /fw treaty <nation> plot <word>} invocation, completing add/remove. */
    private static final int TREATY_PLOT_SUB_ARGS = 4;
    /** Length of a {@code /fw treaty plot add|remove <x> <z>} invocation. */
    private static final int TREATY_PLOT_ARGS = 6;
    /** Index of the add/remove word in a {@code /fw treaty} invocation. */
    private static final int PLOT_SUB_ARG = 3;
    /** Index of the x coordinate in a {@code /fw treaty} invocation. */
    private static final int PLOT_X_ARG = 4;
    /** Index of the z coordinate in a {@code /fw treaty} invocation. */
    private static final int PLOT_Z_ARG = 5;

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
            case "negotiate" -> cmdNegotiate(sender, args);
            case "treaty" -> cmdTreaty(sender, args);
            case "betray" -> cmdBetray(sender);
            case "chat" -> cmdChat(sender, args);
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
        Nation attacker = getNation(resident);
        if (attacker == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
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
                nation = getNation(resident);
            }
        }
        if (nation == null) {
            send(sender, "war.error.nation-not-found", args.length >= 2 ? args[1] : "unknown");
            return;
        }
        List<WarState> wars = WarManager.getInstance().getWarsFor(nation);
        if (wars.isEmpty()) {
            send(sender, "war.status.idle", nation.getFormattedName());
            return;
        }
        for (WarState state : wars) {
            String opponent = state.getOpponent(nation).getFormattedName();
            switch (state.getPhase()) {
                case DECLARED -> {
                    Duration remaining = Duration.between(Instant.now(), state.getActiveAt());
                    send(sender, "war.status.declared", opponent,
                        FlagWarConfig.formatDuration(remaining.isNegative() ? Duration.ZERO : remaining));
                }
                case ACTIVE -> send(sender, "war.status.activenow", opponent);
                case TRUCE -> send(sender, "war.status.truce", opponent,
                    FlagWarConfig.formatDuration(Duration.ofSeconds(state.getTruceRemainingSeconds())));
                case NEGOTIATING -> send(sender, "war.status.negotiating", opponent);
                case PEACE -> send(sender, "war.status.peacenow", opponent);
                case COOLDOWN -> send(sender, "war.status.cooldown", opponent);
                default -> send(sender, "war.status.idle", nation.getFormattedName());
            }
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
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
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
        Duration requested = args.length >= TRUCE_HOURS_ARG ? parseHours(args[TRUCE_HOURS_ARG - 1]) : null;
        boolean started = requested == null
            ? WarManager.getInstance().startTruce(state, false)
            : WarManager.getInstance().startTruce(state, false, requested);
        if (started) {
            send(sender, "war.command.truce.done", opponent.getFormattedName());
        } else {
            send(sender, "war.error.truce-denied");
        }
    }

    private static Duration parseHours(final String hours) {
        try {
            long value = Long.parseLong(hours);
            if (value <= 0) {
                return null;
            }
            return Duration.ofHours(value);
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
            if (args.length < PEACE_ACCEPT_ARGS) {
                send(sender, "war.command.peace.usage");
                return;
            }
            Nation opponent = TownyAPI.getInstance().getNation(args[2]);
            if (opponent == null) {
                send(sender, "war.error.nation-not-found", args[2]);
                return;
            }
            acceptPeace(sender, resident, opponent);
            return;
        }
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
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
        if (state.getPhase() == WarPhase.NEGOTIATING) {
            send(sender, "war.command.peace.negotiating", opponent.getFormattedName());
            WarManager.getInstance().openChannel(state);
            return;
        }
        if (state.getPhase() != WarPhase.ACTIVE && state.getPhase() != WarPhase.TRUCE) {
            send(sender, "war.error.peace-not-possible");
            return;
        }
        Treaty treaty = WarManager.getInstance().proposePeace(state);
        if (treaty != null) {
            send(sender, "war.command.peace.proposed", opponent.getFormattedName());
            WarManager.getInstance().openChannel(state);
        }
    }

    private void acceptPeace(final CommandSender sender, final Resident resident, final Nation opponent) {
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        WarPolicyProvider provider = WarManager.getInstance().getPolicyProvider();
        if (provider != null && !provider.canAcceptPeace(resident, nation)) {
            send(sender, "war.error.policy-denied");
            return;
        }
        WarState state = findNegotiatingWar(nation, opponent);
        if (state == null) {
            send(sender, "war.error.not-negotiating");
            return;
        }
        Treaty treaty = state.getTreaty();
        if (treaty == null) {
            send(sender, "war.error.no-treaty");
            return;
        }
        if (!treaty.isSubmitted()) {
            send(sender, "war.error.treaty-not-submitted");
            return;
        }
        if (WarManager.getInstance().acceptTreaty(state, nation)) {
            send(sender, "war.command.peace.signed");
            WarManager.getInstance().closeChannel();
        } else {
            send(sender, "war.command.peace.accepted-wait", state.getOpponent(nation).getFormattedName());
        }
    }

    private void cmdNegotiate(final CommandSender sender, final String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "flagwar.negotiate")) {
            return;
        }
        if (args.length < NEGOTIATE_ABORT_ARGS || !args[1].equalsIgnoreCase("abort")) {
            send(sender, "war.command.negotiate.usage");
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation opponent = TownyAPI.getInstance().getNation(args[2]);
        if (opponent == null) {
            send(sender, "war.error.nation-not-found", args[2]);
            return;
        }
        WarState state = findNegotiatingWar(nation, opponent);
        if (state == null) {
            send(sender, "war.error.not-negotiating");
            return;
        }
        if (WarManager.getInstance().isNegotiationAbortCooldownActive(nation)) {
            send(sender, "war.error.abort-cooldown");
            return;
        }
        WarManager.getInstance().abortNegotiations(state, nation);
        send(sender, "war.command.negotiate.aborted", opponent.getFormattedName());
    }

    private void cmdTreaty(final CommandSender sender, final String[] args) {
        if (!requirePlayer(sender) || !requirePermission(sender, "flagwar.treaty")) {
            return;
        }
        if (args.length < TREATY_ACTION_ARG + 2) {
            send(sender, "war.command.treaty.usage");
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation opponent = TownyAPI.getInstance().getNation(args[TREATY_ACTION_ARG]);
        if (opponent == null) {
            send(sender, "war.error.nation-not-found", args[TREATY_ACTION_ARG]);
            return;
        }
        WarState state = findNegotiatingWar(nation, opponent);
        if (state == null) {
            send(sender, "war.error.not-negotiating");
            return;
        }
        Treaty treaty = state.getTreaty();
        if (treaty == null) {
            send(sender, "war.error.no-treaty");
            return;
        }
        if (!isDiplomatOrLeader(resident)) {
            send(sender, "war.error.leader-only");
            return;
        }
        String action = args[TREATY_ACTION_ARG + 1].toLowerCase(Locale.ROOT);
        if (treaty.isSubmitted()) {
            if (action.equals("accept")) {
                acceptTreatyCommand(sender, state, nation, treaty);
            } else if (action.equals("reject")) {
                rejectTreaty(sender, state, treaty);
            } else {
                send(sender, "war.error.treaty-submitted");
            }
            return;
        }
        switch (action) {
            case "plot" -> treatyPlot(sender, state, treaty, args);
            case "reparations" -> treatyReparations(sender, state, treaty, args);
            case "neutrality" -> treatyNeutrality(sender, state, treaty, args);
            case "whitepeace" -> treatyWhitePeace(sender, state, treaty);
            case "submit" -> treatySubmit(sender, state, treaty);
            default -> send(sender, "war.command.treaty.usage");
        }
    }

    private void acceptTreatyCommand(final CommandSender sender, final WarState state,
                                     final Nation nation, final Treaty treaty) {
        if (WarManager.getInstance().acceptTreaty(state, nation)) {
            send(sender, "war.command.peace.signed");
            WarManager.getInstance().closeChannel();
        } else {
            send(sender, "war.command.peace.accepted-wait", state.getOpponent(nation).getFormattedName());
        }
    }

    private void rejectTreaty(final CommandSender sender, final WarState state, final Treaty treaty) {
        treaty.reject();
        WarManager.getInstance().touchNegotiation(state);
        send(sender, "war.command.treaty.rejected");
    }

    private void treatyPlot(final CommandSender sender, final WarState state,
                            final Treaty treaty, final String[] args) {
        if (args.length < TREATY_PLOT_ARGS) {
            send(sender, "war.command.treaty.plot.usage");
            return;
        }
        boolean add = args[PLOT_SUB_ARG].equalsIgnoreCase("add");
        boolean remove = args[PLOT_SUB_ARG].equalsIgnoreCase("remove");
        if (!add && !remove) {
            send(sender, "war.command.treaty.plot.usage");
            return;
        }
        int x;
        int z;
        try {
            x = Integer.parseInt(args[PLOT_X_ARG]);
            z = Integer.parseInt(args[PLOT_Z_ARG]);
        } catch (NumberFormatException e) {
            send(sender, "war.command.treaty.plot.usage");
            return;
        }
        Player player = (Player) sender;
        WorldCoord coord = new WorldCoord(player.getWorld().getName(), x, z);
        var townBlock = coord.getTownBlockOrNull();
        if (townBlock == null || !townBlock.hasTown() || townBlock.getTownOrNull() == null) {
            send(sender, "war.error.plot-unclaimed", x, z);
            return;
        }
        Nation owner;
        try {
            owner = townBlock.getTown().getNation();
        } catch (com.palmergames.bukkit.towny.exceptions.NotRegisteredException e) {
            send(sender, "war.error.plot-unclaimed", x, z);
            return;
        }
        // Only plots the attacker captured in this war are eligible for return.
        if (owner != state.getAttacker()) {
            send(sender, "war.error.plot-not-owned", x, z);
            return;
        }
        if (add) {
            treaty.addPlotToReturn(coord);
            WarManager.getInstance().touchNegotiation(state);
            send(sender, "war.command.treaty.plot.added", x, z);
        } else if (treaty.removePlotToReturn(coord)) {
            WarManager.getInstance().touchNegotiation(state);
            send(sender, "war.command.treaty.plot.removed", x, z);
        } else {
            send(sender, "war.error.plot-not-in-treaty", x, z);
        }
    }

    private void treatyReparations(final CommandSender sender, final WarState state,
                                   final Treaty treaty, final String[] args) {
        if (args.length < TREATY_SUB_ARG + 2) {
            send(sender, "war.command.treaty.reparations.usage");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[TREATY_SUB_ARG + 1]);
        } catch (NumberFormatException e) {
            send(sender, "war.command.treaty.reparations.usage");
            return;
        }
        if (amount < 0) {
            send(sender, "war.command.treaty.reparations.usage");
            return;
        }
        if (amount > 0 && !state.getDefender().getAccount().canPayFromHoldings(amount)) {
            send(sender, "war.error.reparations-unaffordable",
                formatBalance(state.getDefender().getAccount().getHoldingBalance()));
            return;
        }
        treaty.setReparations(amount);
        WarManager.getInstance().touchNegotiation(state);
        send(sender, "war.command.treaty.reparations.set", formatBalance(amount));
    }

    private void treatyNeutrality(final CommandSender sender, final WarState state,
                                  final Treaty treaty, final String[] args) {
        if (args.length < TREATY_SUB_ARG + 2) {
            send(sender, "war.command.treaty.neutrality.usage");
            return;
        }
        int days;
        try {
            days = Integer.parseInt(args[TREATY_SUB_ARG + 1]);
        } catch (NumberFormatException e) {
            send(sender, "war.command.treaty.neutrality.usage");
            return;
        }
        if (days < 0) {
            send(sender, "war.command.treaty.neutrality.usage");
            return;
        }
        treaty.setNeutralityDays(days);
        WarManager.getInstance().touchNegotiation(state);
        send(sender, "war.command.treaty.neutrality.set", days);
    }

    private void treatyWhitePeace(final CommandSender sender, final WarState state, final Treaty treaty) {
        treaty.setWhitePeace(true);
        WarManager.getInstance().touchNegotiation(state);
        send(sender, "war.command.treaty.whitepeace.set");
    }

    private void treatySubmit(final CommandSender sender, final WarState state, final Treaty treaty) {
        if (!treaty.isWhitePeace() && !treaty.hasConditions()) {
            send(sender, "war.error.treaty-empty");
            return;
        }
        if (WarManager.getInstance().submitTreaty(state)) {
            send(sender, "war.command.treaty.submitted");
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
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        WarState state = findTruceWar(nation);
        if (state == null) {
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

    private void cmdChat(final CommandSender sender, final String[] args) {
        if (!requirePlayer(sender)) {
            return;
        }
        Player player = (Player) sender;
        Resident resident = TownyAPI.getInstance().getResident(player);
        if (resident == null || !resident.hasNation()) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        Nation nation = getNation(resident);
        if (nation == null) {
            send(sender, "error.player-not-in-nation");
            return;
        }
        DiplomacyChannel channel = WarManager.getInstance().getActiveChannel();
        if (args.length >= 2 && args[1].equalsIgnoreCase("leave")) {
            if (channel == null || !channel.removeMember(resident)) {
                send(sender, "war.error.not-in-channel");
                return;
            }
            send(sender, "war.command.chat.left");
            return;
        }
        WarState state;
        if (args.length >= 2) {
            Nation opponent = TownyAPI.getInstance().getNation(args[1]);
            if (opponent == null) {
                send(sender, "war.error.nation-not-found", args[1]);
                return;
            }
            state = WarManager.getInstance().findWarBetween(nation, opponent)
                .filter(s -> s.getPhase() == WarPhase.TRUCE || s.getPhase() == WarPhase.NEGOTIATING)
                .orElse(null);
        } else {
            state = WarManager.getInstance().getWarsFor(nation).stream()
                .filter(s -> s.getPhase() == WarPhase.TRUCE || s.getPhase() == WarPhase.NEGOTIATING)
                .findFirst().orElse(null);
        }
        if (state == null) {
            send(sender, "war.error.no-chat");
            return;
        }
        channel = WarManager.getInstance().openChannel(state);
        if (channel.addMember(resident)) {
            send(sender, "war.command.chat.joined");
        } else {
            send(sender, "war.error.channel-full");
        }
    }

    /**
     * Locates the negotiating war a nation is involved in against a specific opponent.
     * @param nation the nation to search for.
     * @param opponent the other nation in the negotiation.
     * @return the negotiating war state, or null.
     */
    private static WarState findNegotiatingWar(final Nation nation, final Nation opponent) {
        return WarManager.getInstance().findWarBetween(nation, opponent).filter(
            s -> s.getPhase() == WarPhase.NEGOTIATING).orElse(null);
    }

    /**
     * Locates a truce the nation is involved in.
     * @param nation the nation to search for.
     * @return a truce war state, or null.
     */
    private static WarState findTruceWar(final Nation nation) {
        return WarManager.getInstance().getWarsFor(nation).stream()
            .filter(s -> s.getPhase() == WarPhase.TRUCE).findFirst().orElse(null);
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

    private static boolean isDiplomatOrLeader(final Resident resident) {
        return resident.isKing() || resident.isMayor()
            || (resident.getPlayer() != null && resident.getPlayer().hasPermission("flagwar.diplomat"));
    }

    private static String formatBalance(final double balance) {
        return TownyEconomyHandler.isActive()
            ? TownyEconomyHandler.getFormattedBalance(balance)
            : Double.toString(balance);
    }

    /**
     * Reads the resident's nation, which is only safe after {@link Resident#hasNation()} was checked.
     * @param resident the resident to read.
     * @return the resident's nation, or null if it could not be resolved.
     */
    private static Nation getNation(final Resident resident) {
        try {
            return resident.getNation();
        } catch (com.palmergames.bukkit.towny.exceptions.TownyException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull final CommandSender sender, @NotNull final Command command,
                                      @NotNull final String alias, @NotNull final String[] args) {
        if (args.length == 1) {
            return Arrays.asList("declare", "status", "truce", "peace", "negotiate", "treaty", "betray", "chat")
                .stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("negotiate")) {
                return filterList(List.of("abort"), args[1]);
            }
            if (args[0].equalsIgnoreCase("chat")) {
                return filterList(List.of("leave"), args[1]);
            }
            if (args[0].equalsIgnoreCase("peace") && args[1].equalsIgnoreCase("accept")) {
                return filterList(List.of("accept"), args[1]);
            }
            if (!args[0].equalsIgnoreCase("status") && !args[0].equalsIgnoreCase("peace")) {
                return TownyAPI.getInstance().getNations().stream().map(Nation::getName)
                    .filter(n -> n.startsWith(args[1])).toList();
            }
            return List.of();
        }
        if (args.length == TREATY_SUB_ARGS && args[0].equalsIgnoreCase("treaty")) {
            return filterList(List.of("plot", "reparations", "neutrality", "whitepeace", "submit",
                "accept", "reject"), args[TREATY_ACTION_ARG + 1]);
        }
        if (args.length == TREATY_PLOT_SUB_ARGS && args[0].equalsIgnoreCase("treaty")) {
            if (args[TREATY_ACTION_ARG + 1].equalsIgnoreCase("plot")) {
                return filterList(List.of("add", "remove"), args[PLOT_SUB_ARG]);
            }
            return List.of();
        }
        return List.of();
    }

    private static List<String> filterList(final List<String> options, final String prefix) {
        return options.stream().filter(s -> s.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
