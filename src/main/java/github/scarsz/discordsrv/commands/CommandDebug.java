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

package github.scarsz.discordsrv.commands;

import github.scarsz.discordsrv.util.DebugUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;

public class CommandDebug {

    @Command(commandNames = { "debug" },
            helpMessage = "Dumps DiscordSRV debug information to the bin",
            permission = "discordsrv.debug"
    )
    public static void execute(CommandSender sender, String[] args) {
        String result = DebugUtil.run(sender.getClass().getName().contains("Console") ? "CONSOLE" : sender.getName(), args.length == 0 ? 256 : Integer.parseInt(args[0]));
        sender.sendMessage(TextComponent.fromLegacyText(ChatColor.DARK_AQUA + "Your debug report has been generated and is available at " + ChatColor.AQUA + result));
    }

}
