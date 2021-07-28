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

package github.scarsz.discordsrv.util;

import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializer;
import dev.vankka.mcdiscordreserializer.minecraft.MinecraftSerializerOptions;
import dev.vankka.mcdiscordreserializer.rules.DiscordMarkdownRules;
import dev.vankka.simpleast.core.node.Node;
import dev.vankka.simpleast.core.parser.Rule;
import dev.vankka.simpleast.core.simple.SimpleMarkdownRules;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.objects.DiscordSRVMinecraftRenderer;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for sending & editing messages from/for CommandSenders.
 * Utilizes both MiniMessage and Minecraft's legacy formatting style.
 */
public class MessageUtil {

    /**
     * The default pattern for URLs, used to make them clickable.
     */
    public static final Pattern DEFAULT_URL_PATTERN = Pattern.compile("(?:(https?)://)?([-\\w_.]+\\.\\w{2,})(/\\S*)?");

    /**
     * The pattern for MiniMessage components.
     */
    public static final Pattern MINIMESSAGE_PATTERN = Pattern.compile("(?!<@)((?<start><)(?<token>[^<>]+(:(?<inner>['\"]?([^'\"](\\\\\\\\['\"])?)+['\"]?))*)(?<end>>))+?");

    /**
     * The minecraft legacy section character.
     */
    public static final Character LEGACY_SECTION = ChatColor.COLOR_CHAR;

    /**
     * Utility pattern for %message%.*
     */
    public static final Pattern MESSAGE_PLACEHOLDER = Pattern.compile("%message%.*");

    /**
     * Pattern for capturing both ampersand and the legacy section sign color codes.
     * @see #LEGACY_SECTION
     */
    public static final Pattern STRIP_PATTERN = Pattern.compile("(?<!<@)[&§](?i)[0-9a-fklmnorx]");

    /**
     * Pattern for capturing section sign color codes.
     * @see #LEGACY_SECTION
     */
    public static final Pattern STRIP_SECTION_ONLY_PATTERN = Pattern.compile("(?<!<@)§(?i)[0-9a-fklmnorx]");

    /**
     * Pattern for translating color codes (legacy & adventure), excluding role mentions ({@code <@&role id>}).
     */
    public static final Pattern TRANSLATE_PATTERN = Pattern.compile("(?<!<@)(&)(?i)(?:[0-9a-fklmnorx]|#[0-9a-f]{6})");

    /**
     * MCDiscordReserializer's serializer for converting markdown from Discord -> Minecraft
     */
    public static final MinecraftSerializer MINECRAFT_SERIALIZER;

    /**
     * @see #MINECRAFT_SERIALIZER
     */
    public static final MinecraftSerializer LIMITED_MINECRAFT_SERIALIZER;

    static {
        // add escape + mention + text rules
        List<Rule<Object, Node<Object>, Object>> rules = new ArrayList<>();
        rules.add(SimpleMarkdownRules.createEscapeRule());
        rules.addAll(DiscordMarkdownRules.createMentionRules());
        rules.add(DiscordMarkdownRules.createSpecialTextRule());

        MinecraftSerializerOptions<Component> options = MinecraftSerializerOptions
                .defaults().addRenderer(new DiscordSRVMinecraftRenderer());
        MinecraftSerializerOptions<String> escapeOptions = MinecraftSerializerOptions.escapeDefaults();

        MINECRAFT_SERIALIZER = new MinecraftSerializer(options, escapeOptions);
        LIMITED_MINECRAFT_SERIALIZER = new MinecraftSerializer(options.withRules(rules), escapeOptions);
    }

    private MessageUtil() {}

    /**
     * Translates the plain message from legacy section sign format or MiniMessage format to a {@link Component} and sends it to the provided {@link CommandSender}.
     *
     * @param commandSender the command sender to send the component to
     * @param plainMessage the legacy or section sign format or MiniMessage formatted message
     */
    public static void sendMessage(CommandSender commandSender, String plainMessage) {
        commandSender.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(plainMessage));
    }

    /**
     * Translates the plain message from legacy section sign format or MiniMessage format to a {@link Component} and sends it to the provided {@link CommandSender}s.
     *
     * @param commandSenders the command senders to send the component to
     * @param plainMessage the legacy or section sign format or MiniMessage formatted message
     */
    public static void sendMessage(Iterable<? extends CommandSender> commandSenders, String plainMessage) {
        commandSenders.forEach(s -> s.sendMessage(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(plainMessage)));
    }

    /**
     * Strips the given String of legacy Minecraft coloring (both & and §).
     *
     * @param text the given String to strip colors and formatting from
     * @return the given String with coloring and formatting stripped
     * @see #stripLegacy(String)
     */
    public static String strip(String text) {
        return stripLegacy(text);
    }

    /**
     * Strip the given String of legacy Minecraft coloring (both & and §). Useful for sending things to Discord.
     *
     * @param text the given String to strip colors from
     * @return the given String with coloring stripped
     * @see #STRIP_PATTERN
     * @see #stripLegacySectionOnly(String)
     */
    public static String stripLegacy(String text) {
        if (StringUtils.isBlank(text)) {
            DiscordSRV.debug("Tried stripping blank message");
            return "";
        }

        return STRIP_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Strip the given String of legacy Minecraft coloring (§ only). Useful for sending things to Discord.
     *
     * @param text the given String to strip colors from
     * @return the given String with coloring stripped
     * @see #STRIP_SECTION_ONLY_PATTERN
     */
    public static String stripLegacySectionOnly(String text) {
        return STRIP_SECTION_ONLY_PATTERN.matcher(text).replaceAll("");
    }


    /**
     * Translates ampersand (&) characters into section signs (§) for color codes. Ignores role mentions.
     *
     * @param text the input text
     * @return the output text
     */
    public static String translateLegacy(String text) {
        if (text == null) return null;
        Matcher matcher = TRANSLATE_PATTERN.matcher(text);

        StringBuilder stringBuilder = new StringBuilder(text);
        while (matcher.find()) stringBuilder.setCharAt(matcher.start(1), LEGACY_SECTION);
        return stringBuilder.toString();
    }
}
