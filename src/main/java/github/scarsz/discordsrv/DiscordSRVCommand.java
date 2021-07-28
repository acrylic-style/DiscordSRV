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

package github.scarsz.discordsrv;

import github.scarsz.discordsrv.listeners.DiscordDisconnectListener;
import github.scarsz.discordsrv.util.GamePermissionUtil;
import net.dv8tion.jda.api.requests.CloseCode;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class DiscordSRVCommand extends Command implements TabExecutor {
    public DiscordSRVCommand() {
        super("bdiscord", "discordsrv.command.discord", "bdiscordsrv");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (DiscordSRV.invalidBotToken) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "DiscordSRV is disabled: your bot token is invalid."));
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Please enter a valid token into your config.yml " +
                    "(" + ChatColor.GRAY + "/plugins/DiscordSRV/config.yml" + ChatColor.RED + ")" +
                    " and restart your server to get DiscordSRV to work."));
        } else if (DiscordDisconnectListener.mostRecentCloseCode == CloseCode.DISALLOWED_INTENTS) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "DiscordSRV is disabled: your DiscordSRV bot is lacking required intents."));
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + "Please check your server log " +
                    "(" + ChatColor.GRAY + "/logs/latest.log" + ChatColor.RED + ")" +
                    " for a extended error message during DiscordSRV's startup to get DiscordSRV to work."));
        }

        if (args.length == 0) {
            DiscordSRV.getPlugin().getCommandManager().handle(sender, null, new String[] { });
        } else {
            DiscordSRV.getPlugin().getCommandManager().handle(sender, args[0], Arrays.stream(args).skip(1).toArray(String[]::new));
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        String command = args[0];
        String[] commandArgs = Arrays.stream(args).skip(1).toArray(String[]::new);

        if (command.equals(""))
            return new ArrayList<String>() {{
                for (Map.Entry<String, Method> command : DiscordSRV.getPlugin().getCommandManager().getCommands().entrySet())
                    if (GamePermissionUtil.hasPermission(sender, command.getValue().getAnnotation(github.scarsz.discordsrv.commands.Command.class).permission()))
                        add(command.getKey());
            }};
        if (commandArgs.length == 0)
            return new ArrayList<String>() {{
                for (Map.Entry<String, Method> commandPair : DiscordSRV.getPlugin().getCommandManager().getCommands().entrySet())
                    if (commandPair.getKey().toLowerCase().startsWith(command.toLowerCase()))
                        if (GamePermissionUtil.hasPermission(sender, commandPair.getValue().getAnnotation(github.scarsz.discordsrv.commands.Command.class).permission()))
                            add(commandPair.getKey());
            }};
        return Collections.emptyList();
    }
}
