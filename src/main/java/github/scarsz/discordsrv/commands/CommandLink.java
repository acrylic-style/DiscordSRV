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

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public class CommandLink {

    @Command(commandNames = { "link" },
            helpMessage = "Generates a code to link your Minecraft account to your Discord account",
            permission = "discordsrv.link"
    )
    public static void execute(CommandSender sender, String[] args) {
        AccountLinkManager manager = DiscordSRV.getPlugin().getAccountLinkManager();
        if (manager == null) {
            MessageUtil.sendMessage(sender, LangUtil.Message.UNABLE_TO_LINK_ACCOUNTS_RIGHT_NOW.toString());
            return;
        }

        ProxyServer.getInstance().getScheduler().runAsync(DiscordSRV.getPlugin(), () -> executeAsync(sender, args, manager));
    }

    private static void executeAsync(CommandSender sender, String[] args, AccountLinkManager manager) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(TextComponent.fromLegacyText(ChatColor.RED + LangUtil.InternalMessage.PLAYER_ONLY_COMMAND.toString()));
            return;
        }
        ProxiedPlayer player = (ProxiedPlayer) sender;

        // prevent people from generating multiple link codes then claiming them all at once to get multiple rewards
        new ArrayList<>(manager.getLinkingCodes().entrySet()).stream()
                .filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .forEach(match -> manager.getLinkingCodes().remove(match.getKey()));

        if (manager.getDiscordId(player.getUniqueId()) != null) {
            MessageUtil.sendMessage(sender, LangUtil.Message.ACCOUNT_ALREADY_LINKED.toString());
        } else {
            String code = manager.generateCode(player.getUniqueId());

            TextComponent text = new TextComponent(TextComponent.fromLegacyText(LangUtil.Message.CODE_GENERATED.toString()
                    .replace("%code%", code)
                    .replace("%botname%", DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName())));
            String clickToCopyCode = LangUtil.Message.CLICK_TO_COPY_CODE.toString();
            if (StringUtils.isNotBlank(clickToCopyCode)) {
                text.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, code));
                text.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Text(clickToCopyCode)));
            }
            sender.sendMessage(text);
        }
    }

}
