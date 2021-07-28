/*-
 * LICENSE
 * DiscordSRV
 * -------------
 * Copyright (C) 2016 - 2021 Austin "Scarsz" Shapiro
 * -------------
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * END
 */

package github.scarsz.discordsrv.modules.requirelink;

import alexh.weak.Dynamic;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.WrappedEventPriority;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RequireLinkModule implements Listener {

    public RequireLinkModule() {
        ProxyServer.getInstance().getPluginManager().registerListener(DiscordSRV.getPlugin(), this);
    }

    private void check(String eventType, byte priority, String playerName, UUID playerUuid, Consumer<String> disallow) {
        if (!isEnabled()) return;
        if (!eventType.equals(DiscordSRV.config().getString("Require linked account to play.Listener event"))) return;

        String requestedPriority = DiscordSRV.config().getString("Require linked account to play.Listener priority");
        byte targetPriority = Arrays.stream(WrappedEventPriority.values())
                .filter(p -> p.name().equalsIgnoreCase(requestedPriority))
                .findFirst().map(WrappedEventPriority::getValue).orElse(EventPriority.LOWEST);
        if (priority != targetPriority) return;

        try {
            if (getBypassNames().contains(playerName)) {
                DiscordSRV.debug("Player " + playerName + " is on the bypass list, bypassing linking checks");
                return;
            }

            if (!DiscordSRV.isReady) {
                DiscordSRV.debug("Player " + playerName + " connecting before DiscordSRV is ready, denying login");
                disallow.accept(MessageUtil.translateLegacy(getDiscordSRVStillStartingKickMessage()));
                return;
            }

            String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(playerUuid);
            if (discordId == null) {
                Member botMember = DiscordSRV.getPlugin().getMainGuild().getSelfMember();
                String botName = botMember.getEffectiveName() + "#" + botMember.getUser().getDiscriminator();
                String code = DiscordSRV.getPlugin().getAccountLinkManager().generateCode(playerUuid);
                String inviteLink = DiscordSRV.config().getString("DiscordInviteLink");

                DiscordSRV.debug("Player " + playerName + " is NOT linked to a Discord account, denying login");
                disallow.accept(
                        MessageUtil.translateLegacy(DiscordSRV.config().getString("Require linked account to play.Not linked message"))
                                .replace("{BOT}", botName)
                                .replace("{CODE}", code)
                                .replace("{INVITE}", inviteLink)
                );
                return;
            }

            Dynamic mustBeInDiscordServerOption = DiscordSRV.config().dget("Require linked account to play.Must be in Discord server");
            if (mustBeInDiscordServerOption.is(Boolean.class)) {
                boolean mustBePresent = mustBeInDiscordServerOption.as(Boolean.class);
                boolean isPresent = DiscordUtil.getMemberById(discordId) != null;
                if (mustBePresent && !isPresent) {
                    disallow.accept(
                            MessageUtil.translateLegacy(DiscordSRV.config().getString("Require linked account to play.Messages.Not in server"))
                                    .replace("{INVITE}", DiscordSRV.config().getString("DiscordInviteLink"))
                    );
                    return;
                }
            } else {
                Set<String> targets = new HashSet<>();

                if (mustBeInDiscordServerOption.isList()) {
                    mustBeInDiscordServerOption.children().forEach(dynamic -> targets.add(dynamic.toString()));
                } else {
                    targets.add(mustBeInDiscordServerOption.convert().intoString());
                }

                for (String guildId : targets) {
                    try {
                        Guild guild = DiscordUtil.getJda().getGuildById(guildId);
                        if (guild != null) {
                            boolean inServer = guild.getMemberById(discordId) != null;
                            if (!inServer) {
                                disallow.accept(
                                        MessageUtil.translateLegacy(DiscordSRV.config().getString("Require linked account to play.Messages.Not in server"))
                                                .replace("{INVITE}", DiscordSRV.config().getString("DiscordInviteLink"))
                                );
                                return;
                            }
                        } else {
                            DiscordSRV.debug("Failed to get Discord server by ID " + guildId + ": bot is not in server");
                        }
                    } catch (NumberFormatException e) {
                        DiscordSRV.debug("Failed to get Discord server by ID " + guildId + ": not a parsable long");
                    }
                }
            }

            List<String> subRoleIds = DiscordSRV.config().getStringList("Require linked account to play.Subscriber role.Subscriber roles");
            if (isSubRoleRequired() && !subRoleIds.isEmpty()) {
                int failedRoleIds = 0;
                int matches = 0;

                for (String subRoleId : subRoleIds) {
                    if (StringUtils.isBlank(subRoleId)) {
                        failedRoleIds++;
                        continue;
                    }

                    Role role = null;
                    try {
                        role = DiscordUtil.getJda().getRoleById(subRoleId);
                    } catch (Throwable ignored) {}
                    if (role == null) {
                        failedRoleIds++;
                        continue;
                    }

                    Member member = role.getGuild().getMemberById(discordId);
                    if (member != null && member.getRoles().contains(role)) {
                        matches++;
                    }
                }

                if (failedRoleIds == subRoleIds.size()) {
                    DiscordSRV.error("Tried to authenticate " + playerName + " but no valid subscriber role IDs are found and thats a requirement; login will be denied until this is fixed.");
                    disallow.accept(MessageUtil.translateLegacy(getFailedToFindRoleKickMessage()));
                    return;
                }

                if (getAllSubRolesRequired() ? matches < subRoleIds.size() : matches == 0) {
                    DiscordSRV.debug("Player " + playerName + " does NOT match subscriber role requirements, denying login");
                    disallow.accept(MessageUtil.translateLegacy(getSubscriberRoleKickMessage()));
                }
            }
        } catch (Exception exception) {
            DiscordSRV.error("Failed to check player: " + playerName, exception);
            disallow.accept(MessageUtil.translateLegacy(getUnknownFailureKickMessage()));
        }
    }

    public void noticePlayerUnlink(ProxiedPlayer player) {
        if (!isEnabled()) return;
        if (getBypassNames().contains(player.getName())) return;

        DiscordSRV.info("Kicking player " + player.getName() + " for unlinking their accounts");
        ProxyServer.getInstance().getScheduler().schedule(DiscordSRV.getPlugin(), () -> player.disconnect(TextComponent.fromLegacyText(MessageUtil.translateLegacy(getUnlinkedKickMessage()))), 1, TimeUnit.MILLISECONDS);
    }

    private boolean getAllSubRolesRequired() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Subscriber role.Require all of the listed roles");
    }
    private boolean isEnabled() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Enabled");
    }
    private boolean isSubRoleRequired() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Subscriber role.Require subscriber role to join");
    }
    private Set<String> getBypassNames() {
        return new HashSet<>(DiscordSRV.config().getStringList("Require linked account to play.Bypass names"));
    }
    private String getDiscordSRVStillStartingKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.DiscordSRV still starting");
    }
    private String getFailedToFindRoleKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.Failed to find subscriber role");
    }
    private String getSubscriberRoleKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Subscriber role.Kick message");
    }
    private String getUnknownFailureKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.Failed for unknown reason");
    }
    private String getUnlinkedKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.Kicked for unlinking");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEventLowest(LoginEvent event) {
        if (event.isCancelled()) {
            DiscordSRV.debug("LoginEvent cancellation status for " + "LoginEvent" + " = " + event.isCancelled() + ", skipping");
            return;
        }
        check(event.getClass().getSimpleName(), EventPriority.LOWEST, event.getConnection().getName(), event.getConnection().getUniqueId(), (message) -> {
            event.setCancelReason(TextComponent.fromLegacyText(message));
            event.setCancelled(true);
        });
    }
    @EventHandler(priority = EventPriority.LOW)
    public void onEventLow(LoginEvent event) {
        if (event.isCancelled()) {
            DiscordSRV.debug("LoginEvent cancellation status for " + "LoginEvent" + " = " + event.isCancelled() + ", skipping");
            return;
        }
        check(event.getClass().getSimpleName(), EventPriority.LOW, event.getConnection().getName(), event.getConnection().getUniqueId(), (message) -> {
            event.setCancelReason(TextComponent.fromLegacyText(message));
            event.setCancelled(true);
        });
    }
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEventNormal(LoginEvent event) {
        if (event.isCancelled()) {
            DiscordSRV.debug("LoginEvent cancellation status for " + "LoginEvent" + " = " + event.isCancelled() + ", skipping");
            return;
        }
        check(event.getClass().getSimpleName(), EventPriority.NORMAL, event.getConnection().getName(), event.getConnection().getUniqueId(), (message) -> {
            event.setCancelReason(TextComponent.fromLegacyText(message));
            event.setCancelled(true);
        });
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onEventHigh(LoginEvent event) {
        if (event.isCancelled()) {
            DiscordSRV.debug("LoginEvent cancellation status for " + "LoginEvent" + " = " + event.isCancelled() + ", skipping");
            return;
        }
        check(event.getClass().getSimpleName(), EventPriority.HIGH, event.getConnection().getName(), event.getConnection().getUniqueId(), (message) -> {
            event.setCancelReason(TextComponent.fromLegacyText(message));
            event.setCancelled(true);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEventHighest(LoginEvent event) {
        if (event.isCancelled()) {
            DiscordSRV.debug("LoginEvent cancellation status for " + "LoginEvent" + " = " + event.isCancelled() + ", skipping");
            return;
        }
        check(event.getClass().getSimpleName(), EventPriority.HIGHEST, event.getConnection().getName(), event.getConnection().getUniqueId(), (message) -> {
            event.setCancelReason(TextComponent.fromLegacyText(message));
            event.setCancelled(true);
        });
    }
}
