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

package github.scarsz.discordsrv.objects;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;

import java.util.Collection;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

public class SingleCommandSender implements CommandSender {

    private GuildMessageReceivedEvent event;
    private CommandSender sender;

    public SingleCommandSender(GuildMessageReceivedEvent event, CommandSender consoleCommandSender) {
        this.event = event;
        this.sender = consoleCommandSender;
    }

    @Override
    public boolean hasPermission(String arg0) {
        return sender.hasPermission(arg0);
    }

    @Override
    public void setPermission(String s, boolean b) {

    }

    @Override
    public Collection<String> getPermissions() {
        return null;
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    private boolean alreadyQueuedDelete = false;

    private StringJoiner messageBuffer = new StringJoiner("\n");
    private boolean bufferCollecting = false;

    // To prevent spam and potential rate-limiting when getting multi-line command responses, responses will be grouped together and sent in as few messages as is practical.
    @Override
    public void sendMessage(String message) {
        if (this.bufferCollecting) { // If the buffer has started collecting messages, we should just add this one to it.
            if (DiscordUtil.escapeMarkdown(this.messageBuffer + "\n" + message).length() > 1998) { // If the message will be too long (allowing for markdown escaping and the newline)
                // Send the message, then clear the buffer and add this message to the empty buffer
                DiscordUtil.sendMessage(event.getChannel(), DiscordUtil.escapeMarkdown(this.messageBuffer.toString()), DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") * 1000);
                this.messageBuffer = new StringJoiner("\n");
            }
            this.messageBuffer.add(message);
        } else { // Messages aren't currently being collected, let's start doing that
            this.bufferCollecting = true;
            this.messageBuffer.add(message); // This message is the first one in the buffer
            ProxyServer.getInstance().getScheduler().schedule(DiscordSRV.getPlugin(), () -> { // Collect messages for 3 ticks, then send
                this.bufferCollecting = false;
                if (this.messageBuffer.length() == 0) return; // There's nothing in the buffer to send, leave it
                DiscordUtil.sendMessage(event.getChannel(), DiscordUtil.escapeMarkdown(this.messageBuffer.toString()), DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") * 1000);
                this.messageBuffer = new StringJoiner("\n");
            }, 150, TimeUnit.MILLISECONDS);
        }


        // expire request message after specified time
        if (!alreadyQueuedDelete && DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") > 0 && DiscordSRV.config().getBoolean("DiscordChatChannelConsoleCommandExpirationDeleteRequest")) {
            ProxyServer.getInstance().getScheduler().runAsync(DiscordSRV.getPlugin(), () -> {
                try { Thread.sleep(DiscordSRV.config().getInt("DiscordChatChannelConsoleCommandExpiration") * 1000L); } catch (InterruptedException ignored) {}
                event.getMessage().delete().queue();
                alreadyQueuedDelete = true;
            });
        }
    }

    @Override
    public void sendMessages(String... messages) {
        for (String msg : messages)
            sendMessage(msg);
    }

    @Override
    public void sendMessage(BaseComponent... baseComponents) {
        sendMessage(BaseComponent.toLegacyText(baseComponents));
    }

    @Override
    public void sendMessage(BaseComponent baseComponent) {
        sendMessage(BaseComponent.toLegacyText(baseComponent));
    }

    @Override
    public Collection<String> getGroups() {
        return sender.getGroups();
    }

    @Override
    public void addGroups(String... strings) {
        sender.addGroups(strings);
    }

    @Override
    public void removeGroups(String... strings) {
        sender.removeGroups(strings);
    }
}
