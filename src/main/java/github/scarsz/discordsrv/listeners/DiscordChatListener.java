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

package github.scarsz.discordsrv.listeners;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DiscordGuildMessageReceivedEvent;
import github.scarsz.discordsrv.objects.SingleCommandSender;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public class DiscordChatListener extends ListenerAdapter {

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        // if message is from null author or self do not process
        if (event.getMember() == null || DiscordUtil.getJda() == null || event.getAuthor().equals(DiscordUtil.getJda().getSelfUser()))
            return;

        // canned responses
        for (Map.Entry<String, String> entry : DiscordSRV.getPlugin().getCannedResponses().entrySet()) {
            if (event.getMessage().getContentRaw().toLowerCase().startsWith(entry.getKey().toLowerCase())) {
                String discordMessage = entry.getValue();
                discordMessage = PlaceholderUtil.replacePlaceholdersToDiscord(discordMessage);

                DiscordUtil.sendMessage(event.getChannel(), MessageUtil.strip(discordMessage));
                return; // found a canned response, return so the message doesn't get processed further
            }
        }

        DiscordSRV.api.callEvent(new DiscordGuildMessageReceivedEvent(event));

        // if message from text channel other than a linked one return
        if (DiscordSRV.getPlugin().getDestinationGameChannelNameForTextChannel(event.getChannel()) == null) return;

        // sanity & intention checks
        String message = event.getMessage().getContentRaw();
        if (StringUtils.isBlank(message) && event.getMessage().getAttachments().size() == 0) return;
        if (processPlayerListCommand(event, message)) return;
        processConsoleCommand(event, event.getMessage().getContentRaw());
    }

    private boolean processPlayerListCommand(GuildMessageReceivedEvent event, String message) {
        if (!DiscordSRV.config().getBoolean("DiscordChatChannelListCommandEnabled")) return false;
        if (!StringUtils.trimToEmpty(message).equalsIgnoreCase(DiscordSRV.config().getString("DiscordChatChannelListCommandMessage"))) return false;

        if (PlayerUtil.getOnlinePlayers().size() == 0) {
            DiscordUtil.sendMessage(event.getChannel(), LangUtil.Message.PLAYER_LIST_COMMAND_NO_PLAYERS.toString(), DiscordSRV.config().getInt("DiscordChatChannelListCommandExpiration") * 1000);
        } else {
            String playerListMessage = "";
            playerListMessage += LangUtil.Message.PLAYER_LIST_COMMAND.toString().replace("%playercount%", PlayerUtil.getOnlinePlayers().size() + "/???");
            playerListMessage += "\n```\n";

            StringJoiner players = new StringJoiner(LangUtil.Message.PLAYER_LIST_COMMAND_ALL_PLAYERS_SEPARATOR.toString());

            List<String> playerList = new LinkedList<>();
            for (ProxiedPlayer player : PlayerUtil.getOnlinePlayers()) {
                String playerFormat = LangUtil.Message.PLAYER_LIST_COMMAND_PLAYER.toString()
                        .replace("%username%", player.getName())
                        .replace("%displayname%", MessageUtil.strip(player.getDisplayName()));
                playerList.add(playerFormat);
            }

            playerList.sort(Comparator.naturalOrder());
            for (String playerFormat : playerList) {
                players.add(playerFormat);
            }
            playerListMessage += players.toString();

            if (playerListMessage.length() > 1996) playerListMessage = playerListMessage.substring(0, 1993) + "...";
            playerListMessage += "\n```";
            DiscordUtil.sendMessage(event.getChannel(), playerListMessage, DiscordSRV.config().getInt("DiscordChatChannelListCommandExpiration") * 1000);
        }

        // expire message after specified time
        if (DiscordSRV.config().getInt("DiscordChatChannelListCommandExpiration") > 0 && DiscordSRV.config().getBoolean("DiscordChatChannelListCommandExpirationDeleteRequest")) {
            new Thread(() -> {
                try {
                    Thread.sleep(DiscordSRV.config().getInt("DiscordChatChannelListCommandExpiration") * 1000L);
                } catch (InterruptedException ignored) {}
                DiscordUtil.deleteMessage(event.getMessage());
            }).start();
        }
        return true;
    }

    private boolean processConsoleCommand(GuildMessageReceivedEvent event, String message) {
        if (!DiscordSRV.config().getBoolean("DiscordChatChannelConsoleCommandEnabled")) return false;

        String prefix = DiscordSRV.config().getString("DiscordChatChannelConsoleCommandPrefix");
        if (!StringUtils.startsWithIgnoreCase(message, prefix)) return false;
        String command = message.substring(prefix.length()).trim();

        // check if user has a role able to use this
        Set<String> rolesAllowedToConsole = new HashSet<>();
        rolesAllowedToConsole.addAll(DiscordSRV.config().getStringList("DiscordChatChannelConsoleCommandRolesAllowed"));
        rolesAllowedToConsole.addAll(DiscordSRV.config().getStringList("DiscordChatChannelConsoleCommandWhitelistBypassRoles"));
        boolean allowed = event.isWebhookMessage() || DiscordUtil.memberHasRole(Objects.requireNonNull(event.getMember()), rolesAllowedToConsole);
        if (!allowed) {
            // tell user that they have no permission
            if (DiscordSRV.config().getBoolean("DiscordChatChannelConsoleCommandNotifyErrors")) {
                String e = LangUtil.Message.CHAT_CHANNEL_COMMAND_ERROR.toString()
                        .replace("%user%", event.getAuthor().getName())
                        .replace("%error%", "no permission");
                event.getAuthor().openPrivateChannel().queue(dm -> dm.sendMessage(e).queue(null, t -> {
                    DiscordSRV.debug("Failed to send DM to " + event.getAuthor() + ": " + t.getMessage());
                    event.getChannel().sendMessage(e).queue();
                }), t -> {
                    DiscordSRV.debug("Failed to open DM conversation with " + event.getAuthor() + ": " + t.getMessage());
                    event.getChannel().sendMessage(e).queue();
                });
            }
            return true;
        }

        // check if user has a role that can bypass the white/blacklist
        boolean canBypass = false;
        for (String roleName : DiscordSRV.config().getStringList("DiscordChatChannelConsoleCommandWhitelistBypassRoles")) {
            boolean isAble = DiscordUtil.memberHasRole(Objects.requireNonNull(event.getMember()), Collections.singleton(roleName));
            canBypass = isAble || canBypass;
        }

        // check if requested command is white/blacklisted
        boolean commandIsAbleToBeUsed;

        if (canBypass) {
            commandIsAbleToBeUsed = true;
        } else {
            // Check the white/black list
            String requestedCommand = command.split(" ")[0];
            boolean whitelistActsAsBlacklist = DiscordSRV.config().getBoolean("DiscordChatChannelConsoleCommandWhitelistActsAsBlacklist");

            List<String> commandsToCheck = DiscordSRV.config().getStringList("DiscordChatChannelConsoleCommandWhitelist");
            boolean isListed = commandsToCheck.contains(requestedCommand);

            commandIsAbleToBeUsed = isListed ^ whitelistActsAsBlacklist;
        }

        if (!commandIsAbleToBeUsed) {
            // tell user that the command is not able to be used
            if (DiscordSRV.config().getBoolean("DiscordChatChannelConsoleCommandNotifyErrors")) {
                String e = LangUtil.Message.CHAT_CHANNEL_COMMAND_ERROR.toString()
                        .replace("%user%", event.getAuthor().getName())
                        .replace("%error%", "command is not able to be used");
                event.getAuthor().openPrivateChannel().queue(dm -> {
                    dm.sendMessage(e).queue(null, t -> {
                        DiscordSRV.debug("Failed to send DM to " + event.getAuthor() + ": " + t.getMessage());
                        event.getChannel().sendMessage(e).queue();
                    });
                }, t -> {
                    DiscordSRV.debug("Failed to open DM conversation with " + event.getAuthor() + ": " + t.getMessage());
                    event.getChannel().sendMessage(e).queue();
                });
            }
            return true;
        }

        // log command to console log file, if this fails the command is not executed for safety reasons unless this is turned off
        File logFile = DiscordSRV.getPlugin().getLogFile();
        if (logFile != null) {
            try {
                FileUtils.writeStringToFile(
                    logFile,
                    "[" + TimeUtil.timeStamp() + " | ID " + event.getAuthor().getId() + "] " + event.getAuthor().getName() + ": " + event.getMessage().getContentRaw() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    true
                );
            } catch (IOException e) {
                DiscordSRV.error(LangUtil.InternalMessage.ERROR_LOGGING_CONSOLE_ACTION + " " + logFile.getAbsolutePath() + ": " + e.getMessage());
                if (DiscordSRV.config().getBoolean("CancelConsoleCommandIfLoggingFailed")) return true;
            }
        }

        // at this point, the user has permission to run commands at all and is able to run the requested command, so do it
        ProxyServer.getInstance().getScheduler().schedule(
                DiscordSRV.getPlugin(),
                () -> ProxyServer.getInstance().getPluginManager().dispatchCommand(new SingleCommandSender(event, ProxyServer.getInstance().getConsole()), command),
                1,
                TimeUnit.MILLISECONDS
        );

        return true;
    }

}
