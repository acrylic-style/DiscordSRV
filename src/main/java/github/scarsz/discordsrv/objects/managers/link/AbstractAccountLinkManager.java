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

package github.scarsz.discordsrv.objects.managers.link;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.AccountLinkedEvent;
import github.scarsz.discordsrv.api.events.AccountUnlinkedEvent;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.PrettyUtil;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public abstract class AbstractAccountLinkManager extends AccountLinkManager {

    @Getter
    protected final Map<String, UUID> linkingCodes = new ConcurrentHashMap<>();

    @Override
    public String generateCode(UUID playerUuid) {
        String codeString;
        do {
            int code = ThreadLocalRandom.current().nextInt(10000);
            codeString = String.format("%04d", code);
        } while (linkingCodes.putIfAbsent(codeString, playerUuid) != null);
        return codeString;
    }

    private final Set<String> nagged = new HashSet<>();
    protected void ensureOffThread(boolean single) {
        //if (!Bukkit.isPrimaryThread()) return;

        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String apiUser = elements[3].toString();
        if (!nagged.add(apiUser)) return;

        if (apiUser.startsWith("github.scarsz.discordsrv")) {
            DiscordSRV.warning("Linked account data requested on main thread, please report this to DiscordSRV: " + apiUser);
            for (StackTraceElement element : elements) DiscordSRV.debug(element.toString());
            return;
        }

        DiscordSRV.warning("API user " + apiUser + " requested linked account information on the main thread while MySQL is enabled in DiscordSRV's settings");
        if (single) {
            DiscordSRV.warning("Requesting data for offline players on the main thread will lead to a exception in the future, if being on the main thread is explicitly required use getDiscordIdBypassCache / getUuidBypassCache");
        } else {
            DiscordSRV.warning("Managing / Requesting bulk linked account data on the main thread will lead to a exception in the future");
        }
        DiscordSRV.debug("Full callstack:");
        for (StackTraceElement element : elements) DiscordSRV.debug(element.toString());
    }

    protected void afterLink(String discordId, UUID uuid) {
        // call link event
        DiscordSRV.api.callEvent(new AccountLinkedEvent(DiscordUtil.getUserById(discordId), uuid));

        // trigger server commands
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        User user = DiscordUtil.getUserById(discordId);
        for (String command : DiscordSRV.config().getStringList("MinecraftDiscordAccountLinkedConsoleCommands")) {
            DiscordSRV.debug("Parsing command /" + command + " for linked commands...");
            command = command
                    .replace("%minecraftplayername%", PrettyUtil.beautifyUsername(player, "[Unknown Player]", false))
                    .replace("%minecraftdisplayname%", PrettyUtil.beautifyNickname(player, "[Unknown Player]", false))
                    .replace("%minecraftuuid%", uuid.toString())
                    .replace("%discordid%", discordId)
                    .replace("%discordname%", user != null ? user.getName() : "")
                    .replace("%discorddisplayname%", PrettyUtil.beautify(user, "", false));
            if (StringUtils.isBlank(command)) {
                DiscordSRV.debug("Command was blank, skipping");
                continue;
            }
            //if (PluginUtil.pluginHookIsEnabled("placeholderapi")) command = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(ProxyServer.getInstance().getPlayer(uuid), command);

            String finalCommand = command;
            DiscordSRV.debug("Final command to be run: /" + finalCommand);
            ProxyServer.getInstance().getScheduler().schedule(DiscordSRV.getPlugin(), () -> ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), finalCommand), 1, TimeUnit.MILLISECONDS);
        }

        String roleName = DiscordSRV.config().getString("MinecraftDiscordAccountLinkedRoleNameToAddUserTo");
        try {
            Role roleToAdd = DiscordUtil.getJda().getRolesByName(roleName, true).stream().findFirst().orElse(null);
            if (roleToAdd != null) {
                Member member = roleToAdd.getGuild().getMemberById(discordId);
                if (member != null) {
                    DiscordUtil.addRoleToMember(member, roleToAdd);
                } else {
                    DiscordSRV.debug("Couldn't find member for " + player.getName() + " in " + roleToAdd.getGuild());
                }
            } else {
                DiscordSRV.debug("Couldn't find \"account linked\" role " + roleName + " to add to " + player.getName() + "'s linked Discord account");
            }
        } catch (Throwable t) {
            DiscordSRV.debug("Couldn't add \"account linked\" role \"" + roleName + "\" due to exception: " + ExceptionUtils.getMessage(t));
        }

        // set user's discord nickname as their in-game name
        if (DiscordSRV.config().getBoolean("NicknameSynchronizationEnabled")) {
            DiscordSRV.getPlugin().getNicknameUpdater().setNickname(DiscordUtil.getMemberById(discordId), player);
        }
    }

    protected void beforeUnlink(UUID uuid, String discordId) {
        try {
            // remove user from linked role
            Role role = DiscordUtil.getJda().getRolesByName(DiscordSRV.config().getString("MinecraftDiscordAccountLinkedRoleNameToAddUserTo"), true).stream().findFirst().orElse(null);
            if (role != null) {
                Member member = role.getGuild().getMemberById(discordId);
                if (member != null) {
                    role.getGuild().removeRoleFromMember(member, role).queue();
                } else {
                    DiscordSRV.debug("Couldn't remove \"linked\" role from null member: " + uuid);
                }
            } else {
                DiscordSRV.debug("Couldn't remove user from null \"linked\" role");
            }
        } catch (Throwable t) {
            DiscordSRV.debug("Failed to remove \"linked\" role from [" + uuid + ":" + discordId + "] during unlink: " + ExceptionUtils.getMessage(t));
        }
    }

    protected void afterUnlink(UUID uuid, String discordId) {
        Member member = DiscordUtil.getMemberById(discordId);

        DiscordSRV.api.callEvent(new AccountUnlinkedEvent(discordId, uuid));

        // run unlink console commands
        ProxiedPlayer offlinePlayer = ProxyServer.getInstance().getPlayer(uuid);
        User user = DiscordUtil.getUserById(discordId);
        for (String command : DiscordSRV.config().getStringList("MinecraftDiscordAccountUnlinkedConsoleCommands")) {
            command = command
                    .replace("%minecraftplayername%", PrettyUtil.beautifyUsername(offlinePlayer, "[Unknown player]", false))
                    .replace("%minecraftdisplayname%", PrettyUtil.beautifyNickname(offlinePlayer, "<Unknown name>", false))
                    .replace("%minecraftuuid%", uuid.toString())
                    .replace("%discordid%", discordId)
                    .replace("%discordname%", user != null ? user.getName() : "")
                    .replace("%discorddisplayname%", PrettyUtil.beautify(user, "", false));
            if (StringUtils.isBlank(command)) continue;
            //if (PluginUtil.pluginHookIsEnabled("placeholderapi")) command = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(ProxyServer.getInstance().getPlayer(uuid), command);

            String finalCommand = command;
            ProxyServer.getInstance().getScheduler().schedule(DiscordSRV.getPlugin(), () -> ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), finalCommand), 50, TimeUnit.MILLISECONDS);
        }

        if (member != null) {
            if (member.getGuild().getSelfMember().canInteract(member)) {
                member.modifyNickname(null).queue();
            } else {
                DiscordSRV.debug("Can't remove nickname from " + member + ", bot is lower in hierarchy");
            }
        }

        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(uuid);
        if (player != null) {
            DiscordSRV.getPlugin().getRequireLinkModule().noticePlayerUnlink(player);
        }
    }
}
