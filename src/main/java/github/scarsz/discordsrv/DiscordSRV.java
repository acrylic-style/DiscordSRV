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

import alexh.weak.Dynamic;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.WebSocketFactory;
import github.scarsz.configuralize.DynamicConfig;
import github.scarsz.configuralize.Language;
import github.scarsz.configuralize.ParseException;
import github.scarsz.discordsrv.api.ApiManager;
import github.scarsz.discordsrv.api.events.DiscordReadyEvent;
import github.scarsz.discordsrv.hooks.PluginHook;
import github.scarsz.discordsrv.listeners.DiscordAccountLinkListener;
import github.scarsz.discordsrv.listeners.DiscordChatListener;
import github.scarsz.discordsrv.listeners.DiscordConsoleListener;
import github.scarsz.discordsrv.listeners.DiscordDisconnectListener;
import github.scarsz.discordsrv.modules.alerts.AlertListener;
import github.scarsz.discordsrv.modules.requirelink.RequireLinkModule;
import github.scarsz.discordsrv.objects.ConsoleMessage;
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.objects.log4j.ConsoleAppender;
import github.scarsz.discordsrv.objects.log4j.JdaFilter;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.objects.managers.CommandManager;
import github.scarsz.discordsrv.objects.managers.link.FileAccountLinkManager;
import github.scarsz.discordsrv.objects.managers.link.JdbcAccountLinkManager;
import github.scarsz.discordsrv.objects.threads.ChannelTopicUpdater;
import github.scarsz.discordsrv.objects.threads.ConsoleMessageQueueWorker;
import github.scarsz.discordsrv.objects.threads.NicknameUpdater;
import github.scarsz.discordsrv.objects.threads.PresenceUpdater;
import github.scarsz.discordsrv.util.ConfigUtil;
import github.scarsz.discordsrv.util.DebugUtil;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageFormatResolver;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import github.scarsz.discordsrv.util.UpdateUtil;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.utils.IOUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import okhttp3.internal.tls.OkHostnameVerifier;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.jetbrains.annotations.NotNull;
import org.minidns.DnsClient;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.record.Record;

import javax.net.ssl.SSLContext;
import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * DiscordSRV's main class, can be accessed via {@link #getPlugin()}.
 *
 * @see #getAccountLinkManager()
 */
@SuppressWarnings({"unused", "WeakerAccess", "ConstantConditions"})
public class DiscordSRV extends Plugin {

    public static final ApiManager api = new ApiManager();
    public static boolean isReady = false;
    public static boolean updateIsAvailable = false;
    public static boolean updateChecked = false;
    public static boolean invalidBotToken = false;
    private static boolean offlineUuidAvatarUrlNagged = false;
    public static String version = "";

    // Managers
    @Getter private AccountLinkManager accountLinkManager;
    @Getter private CommandManager commandManager = new CommandManager();

    // Threads
    @Getter private ChannelTopicUpdater channelTopicUpdater;
    @Getter private ConsoleMessageQueueWorker consoleMessageQueueWorker;
    @Getter private NicknameUpdater nicknameUpdater;
    @Getter private PresenceUpdater presenceUpdater;
    @Getter private ScheduledExecutorService updateChecker = null;

    // Modules
    @Getter private AlertListener alertListener = null;
    @Getter private RequireLinkModule requireLinkModule;

    // Config
    @Getter private final Map<String, String> channels = new LinkedHashMap<>(); // <in-game channel name, discord channel>
    @Getter private final Map<String, String> roleAliases = new LinkedHashMap<>(); // key always lowercase
    @Getter private final Map<Pattern, String> consoleRegexes = new HashMap<>();
    @Getter private final Map<Pattern, String> gameRegexes = new HashMap<>();
    @Getter private final Map<Pattern, String> discordRegexes = new HashMap<>();
    private final DynamicConfig config;

    // Console
    @Getter private final Deque<ConsoleMessage> consoleMessageQueue = new LinkedList<>();
    @Getter private ConsoleAppender consoleAppender;

    @Getter private final long startTime = System.currentTimeMillis();
    @Getter private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    @Getter private final Set<PluginHook> pluginHooks = new HashSet<>();

    // Files
    @Getter private final File configFile = new File(getDataFolder(), "config.yml");
    @Getter private final File messagesFile = new File(getDataFolder(), "messages.yml");
    @Getter private final File voiceFile = new File(getDataFolder(), "voice.yml");
    @Getter private final File linkingFile = new File(getDataFolder(), "linking.yml");
    @Getter private final File synchronizationFile = new File(getDataFolder(), "synchronization.yml");
    @Getter private final File alertsFile = new File(getDataFolder(), "alerts.yml");
    @Getter private final File debugFolder = new File(getDataFolder(), "debug");
    @Getter private final File logFolder = new File(getDataFolder(), "discord-console-logs");
    @Getter private final File linkedAccountsFile = new File(getDataFolder(), "linkedaccounts.json");

    // JDA & JDA related
    @Getter private JDA jda = null;
    private ExecutorService callbackThreadPool;
    private JdaFilter jdaFilter;

    public static DiscordSRV getPlugin() {
        return (DiscordSRV) ProxyServer.getInstance().getPluginManager().getPlugin("DiscordSRV");
    }
    public static DynamicConfig config() {
        return getPlugin().config;
    }
    public void reloadConfig() {
        try {
            config().loadAll();
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }
    public void reloadChannels() {
        synchronized (channels) {
            channels.clear();
            config().dget("Channels").children().forEach(dynamic ->
                    this.channels.put(dynamic.key().convert().intoString(), dynamic.convert().intoString()));
        }
    }
    public void reloadRoleAliases() {
        synchronized (roleAliases) {
            roleAliases.clear();
            config().dget("DiscordChatChannelRoleAliases").children().forEach(dynamic ->
                    this.roleAliases.put(dynamic.key().convert().intoString().toLowerCase(), dynamic.convert().intoString()));
        }
    }
    public void reloadRegexes() {
        synchronized (consoleRegexes) {
            consoleRegexes.clear();
            loadRegexesFromConfig(config().dget("DiscordConsoleChannelFilters"), consoleRegexes);
        }
        synchronized (gameRegexes) {
            gameRegexes.clear();
            loadRegexesFromConfig(config().dget("DiscordChatChannelGameFilters"), gameRegexes);
        }
        synchronized (discordRegexes) {
            discordRegexes.clear();
            loadRegexesFromConfig(config().dget("DiscordChatChannelDiscordFilters"), discordRegexes);
        }
    }
    private void loadRegexesFromConfig(final Dynamic dynamic, final Map<Pattern, String> map) {
        dynamic.children().forEach(d -> {
            String key = d.key().convert().intoString();
            if (StringUtils.isEmpty(key)) return;
            try {
                Pattern pattern = Pattern.compile(key, Pattern.DOTALL);
                map.put(pattern, d.convert().intoString());
            } catch (PatternSyntaxException e) {
                error("Invalid regex pattern: " + key + " (" + e.getDescription() + ")");
            }
        });
    }
    public String getMainChatChannel() {
        return channels.size() != 0 ? channels.keySet().iterator().next() : null;
    }
    public TextChannel getMainTextChannel() {
        if (channels.isEmpty() || jda == null) return null;
        String firstChannel = channels.values().iterator().next();
        if (StringUtils.isBlank(firstChannel)) return null;
        return DiscordUtil.getTextChannelById(firstChannel);
    }
    public Guild getMainGuild() {
        if (jda == null) return null;

        return getMainTextChannel() != null
                ? getMainTextChannel().getGuild()
                : getConsoleChannel() != null
                    ? getConsoleChannel().getGuild()
                    : jda.getGuilds().size() > 0
                        ? jda.getGuilds().get(0)
                        : null;
    }
    public TextChannel getConsoleChannel() {
        if (jda == null) return null;

        String consoleChannel = config.getString("DiscordConsoleChannelId");
        return StringUtils.isNotBlank(consoleChannel) && StringUtils.isNumeric(consoleChannel)
                ? DiscordUtil.getTextChannelById(consoleChannel)
                : null;
    }
    public TextChannel getDestinationTextChannelForGameChannelName(String gameChannelName) {
        Map.Entry<String, String> entry = channels.entrySet().stream().filter(e -> e.getKey().equals(gameChannelName)).findFirst().orElse(null);
        if (entry != null) return jda.getTextChannelById(entry.getValue()); // found case-sensitive channel

        // no case-sensitive channel found, try case in-sensitive
        entry = channels.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(gameChannelName)).findFirst().orElse(null);
        if (entry != null) return jda.getTextChannelById(entry.getValue()); // found case-insensitive channel

        return null; // no channel found, case-insensitive or not
    }
    public String getDestinationGameChannelNameForTextChannel(TextChannel source) {
        if (source == null) return null;
        return channels.entrySet().stream()
                .filter(entry -> source.getId().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }
    public File getLogFile() {
        String fileName = config().getString("DiscordConsoleChannelUsageLog");
        if (StringUtils.isBlank(fileName)) return null;
        fileName = fileName.replace("%date%", TimeUtil.date());
        return new File(this.getLogFolder(), fileName);
    }

    // log messages
    private static void logThrowable(Throwable throwable, Consumer<String> logger) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));

        for (String line : stringWriter.toString().split("\n")) logger.accept(line);
    }
    public static void info(LangUtil.InternalMessage message) {
        info(message.toString());
    }
    public static void info(String message) {
        getPlugin().getLogger().info(message);
    }
    public static void warning(LangUtil.InternalMessage message) {
        warning(message.toString());
    }
    public static void warning(String message) {
        getPlugin().getLogger().warning(message);
    }
    public static void error(LangUtil.InternalMessage message) {
        error(message.toString());
    }
    public static void error(String message) {
        getPlugin().getLogger().severe(message);
    }
    public static void error(Throwable throwable) {
         logThrowable(throwable, DiscordSRV::error);
    }
    public static void error(String message, Throwable throwable) {
        error(message);
        error(throwable);
    }
    public static void debug(String message) {
        // return if plugin is not in debug mode
        if (DiscordSRV.config().getInt("DebugLevel") == 0) return;

        getPlugin().getLogger().info("[DEBUG] " + message + (DiscordSRV.config().getInt("DebugLevel") >= 2 ? "\n" + DebugUtil.getStackTrace() : ""));
    }
    public static void debug(Throwable throwable) {
        logThrowable(throwable, DiscordSRV::debug);
    }
    public static void debug(Throwable throwable, String message) {
        debug(throwable);
        debug(message);
    }
    public static void debug(Collection<String> message) {
        message.forEach(DiscordSRV::debug);
    }

    public DiscordSRV() {
        super();

        // load config
        getDataFolder().mkdirs();
        config = new DynamicConfig();
        config.addSource(DiscordSRV.class, "config", getConfigFile());
        config.addSource(DiscordSRV.class, "messages", getMessagesFile());
        config.addSource(DiscordSRV.class, "voice", getVoiceFile());
        config.addSource(DiscordSRV.class, "linking", getLinkingFile());
        config.addSource(DiscordSRV.class, "synchronization", getSynchronizationFile());
        config.addSource(DiscordSRV.class, "alerts", getAlertsFile());
        String languageCode = System.getProperty("user.language").toUpperCase();
        Language language = null;
        try {
            Language lang = Language.valueOf(languageCode);
            if (config.isLanguageAvailable(lang)) {
                language = lang;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            String lang = language != null ? language.getName() : languageCode.toUpperCase();
            getLogger().info("Unknown user language " + lang + ".");
            getLogger().info("If you fluently speak " + lang + " as well as English, see the GitHub repo to translate it!");
        }
        if (language == null) language = Language.EN;
        config.setLanguage(language);
        try {
            config.saveAllDefaults();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save default config files", e);
        }
        try {
            config.loadAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
        String forcedLanguage = config.getString("ForcedLanguage");
        if (StringUtils.isNotBlank(forcedLanguage) && !forcedLanguage.equalsIgnoreCase("none")) {
            Arrays.stream(Language.values())
                    .filter(lang -> lang.getCode().equalsIgnoreCase(forcedLanguage) ||
                            lang.getName().equalsIgnoreCase(forcedLanguage)
                    )
                    .findFirst().ifPresent(config::setLanguage);
        }

        // Make discordsrv.sync.x & discordsrv.sync.deny.x permissions denied by default
        // just hope that they're denied by default
        /*
        try {
            PluginDescription description = getDescription();
            Class<?> descriptionClass = description.getClass();

            List<org.bukkit.permissions.Permission> permissions = new ArrayList<>(description.);
            for (String s : getGroupSynchronizables().keySet()) {
                permissions.add(new org.bukkit.permissions.Permission("discordsrv.sync." + s, null, PermissionDefault.FALSE));
                permissions.add(new org.bukkit.permissions.Permission("discordsrv.sync.deny." + s, null, PermissionDefault.FALSE));
            }

            Field permissionsField = descriptionClass.getDeclaredField("permissions");
            permissionsField.setAccessible(true);
            permissionsField.set(description, ImmutableList.copyOf(permissions));

            Class<?> pluginClass = getClass().getSuperclass();
            Field descriptionField = pluginClass.getDeclaredField("description");
            descriptionField.setAccessible(true);
            descriptionField.set(this, description);
        } catch (Exception e) {
            e.printStackTrace();
        }
        */
    }

    @Override
    public void onEnable() {
        if (++DebugUtil.initializationCount > 1) {
            DiscordSRV.error(ChatColor.RED + LangUtil.InternalMessage.PLUGIN_RELOADED.toString());
            PlayerUtil.getOnlinePlayers().stream()
                    .filter(player -> player.hasPermission("discordsrv.admin"))
                    .forEach(player -> MessageUtil.sendMessage(player, ChatColor.RED + LangUtil.InternalMessage.PLUGIN_RELOADED.toString()));
        }

        ConfigUtil.migrate();
        ConfigUtil.logMissingOptions();
        DiscordSRV.debug("Language is " + config.getLanguage().getName());

        version = getDescription().getVersion();
        Thread initThread = new Thread(this::init, "DiscordSRV - Initialization");
        initThread.setUncaughtExceptionHandler((t, e) -> {
            // make DiscordSRV go red in /plugins
            disablePlugin();
            error(e);
            getLogger().severe("DiscordSRV failed to load properly: " + e.getMessage() + ". See " + github.scarsz.discordsrv.util.DebugUtil.run("DiscordSRV") + " for more information. Can't figure it out? Go to https://discordsrv.com/discord for help");
        });
        initThread.start();
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new DiscordSRVCommand());
    }

    public void disablePlugin() {
        // no, it doesn't support disabling plugin
    }

    public void init() {
        if (getProxy().getPluginManager().getPlugin("PlugMan") != null) {
            Plugin plugMan = getProxy().getPluginManager().getPlugin("PlugMan");
            try {
                List<String> ignoredPlugins = (List<String>) plugMan.getClass().getMethod("getIgnoredPlugins").invoke(plugMan);
                if (!ignoredPlugins.contains("DiscordSRV")) {
                    ignoredPlugins.add("DiscordSRV");
                }
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {}
        }

        // check if the person is trying to use the plugin without updating to ASM 5
        try {
            File specialSourceFile = new File("libraries/net/md-5/SpecialSource/1.7-SNAPSHOT/SpecialSource-1.7-SNAPSHOT.jar");
            if (!specialSourceFile.exists()) specialSourceFile = new File("bin/net/md-5/SpecialSource/1.7-SNAPSHOT/SpecialSource-1.7-SNAPSHOT.jar");
            if (specialSourceFile.exists() && DigestUtils.md5Hex(FileUtils.readFileToByteArray(specialSourceFile)).equalsIgnoreCase("096777a1b6098130d6c925f1c04050a3")) {
                DiscordSRV.warning(LangUtil.InternalMessage.ASM_WARNING.toString()
                        .replace("{specialsourcefolder}", specialSourceFile.getParentFile().getPath())
                );
            }
        } catch (IOException e) {
            error(e);
        }

        requireLinkModule = new RequireLinkModule();

        // start the update checker (will skip if disabled)
        if (!isUpdateCheckDisabled()) {
            if (updateChecker == null) {
                final ThreadFactory gatewayThreadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - Update Checker").build();
                updateChecker = Executors.newScheduledThreadPool(1);
            }
            updateChecker.schedule(() -> {
                DiscordSRV.updateIsAvailable = UpdateUtil.checkForUpdates();
                DiscordSRV.updateChecked = true;
            }, 0, TimeUnit.SECONDS);
            updateChecker.scheduleAtFixedRate(() ->
                    DiscordSRV.updateIsAvailable = UpdateUtil.checkForUpdates(false),
                    6, 6, TimeUnit.HOURS
            );
        }

        // shutdown previously existing jda if plugin gets reloaded
        if (jda != null) try { jda.shutdown(); jda = null; } catch (Exception e) { error(e); }

        // set default mention types to never ping everyone/here
        MessageAction.setDefaultMentions(config().getStringList("DiscordChatChannelAllowedMentions").stream()
                .map(s -> {
                    try {
                        return Message.MentionType.valueOf(s.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        DiscordSRV.error("Unknown mention type \"" + s + "\" defined in DiscordChatChannelAllowedMentions");
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet()));
        DiscordSRV.debug("Allowed chat mention types: " + MessageAction.getDefaultMentions().stream().map(Enum::name).collect(Collectors.joining(", ")));

        // set proxy just in case this JVM doesn't have a proxy selector for some reason
        if (ProxySelector.getDefault() == null) {
            ProxySelector.setDefault(new ProxySelector() {
                private final List<Proxy> DIRECT_CONNECTION = Collections.unmodifiableList(Collections.singletonList(Proxy.NO_PROXY));
                public void connectFailed(URI arg0, SocketAddress arg1, IOException arg2) {}
                public List<Proxy> select(URI uri) { return DIRECT_CONNECTION; }
            });
        }

        // set ssl to TLSv1.2
        if (config().getBoolean("ForceTLSv12")) {
            try {
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                context.init(null, null, null);
                SSLContext.setDefault(context);
            } catch (Exception ignored) {}
        }

        // check log4j capabilities
        boolean serverIsLog4jCapable = false;
        boolean serverIsLog4j21Capable = false;
        try {
            serverIsLog4jCapable = Class.forName("org.apache.logging.log4j.core.Logger") != null;
        } catch (ClassNotFoundException e) {
            error("Log4j classes are NOT available, console channel will not be attached");
        }
        try {
            serverIsLog4j21Capable = Class.forName("org.apache.logging.log4j.core.Filter") != null;
        } catch (ClassNotFoundException e) {
            error("Log4j 2.1 classes are NOT available, JDA messages will NOT be formatted properly");
        }

        // add log4j filter for JDA messages
        if (serverIsLog4j21Capable && jdaFilter == null) {
            try {
                Class<?> jdaFilterClass = Class.forName("github.scarsz.discordsrv.objects.log4j.JdaFilter");
                jdaFilter = (JdaFilter) jdaFilterClass.newInstance();
                ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger()).addFilter((org.apache.logging.log4j.core.Filter) jdaFilter);
                debug("JdaFilter applied");
            } catch (Exception e) {
                error("Failed to attach JDA message filter to root logger", e);
            }
        }

        if (config().getBoolean("DebugJDA")) {
            LoggerContext config = ((LoggerContext) LogManager.getContext(false));
            config.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.ALL);
            config.updateLoggers();
        }

        if (config().getBoolean("DebugJDARestActions")) {
            RestAction.setPassContext(true);
        }

        // http client for JDA
        Dns dns = Dns.SYSTEM;
        try {
            List<InetAddress> fallbackDnsServers = new CopyOnWriteArrayList<>(Arrays.asList(
                    // CloudFlare resolvers
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    // Google resolvers
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4")
            ));

            dns = new Dns() {
                // maybe drop minidns in favor of something else
                // https://github.com/dnsjava/dnsjava/blob/master/src/main/java/org/xbill/DNS/SimpleResolver.java
                // https://satreth.blogspot.com/2015/01/java-dns-query.html

                private final DnsClient client = new DnsClient();
                private int failedRequests = 0;
                @NotNull @Override
                public List<InetAddress> lookup(@NotNull String host) throws UnknownHostException {
                    int max = config.getInt("MaximumAttemptsForSystemDNSBeforeUsingFallbackDNS");
                    //  0 = everything falls back (would only be useful when the system dns literally doesn't work & can't be fixed)
                    // <0 = nothing falls back, everything uses system dns
                    // >0 = falls back if goes past that amount of failed requests in a row
                    if (max < 0 || (max > 0 && failedRequests < max)) {
                        try {
                            List<InetAddress> result = Dns.SYSTEM.lookup(host);
                            failedRequests = 0; // reset on successful lookup
                            return result;
                        } catch (Exception e) {
                            failedRequests++;
                            DiscordSRV.error("System DNS FAILED to resolve hostname " + host + ", " +
                                    (max == 0 ? "" : failedRequests >= max ? "using fallback DNS for this request" : "switching to fallback DNS servers") + "!");
                            if (max == 0) {
                                // not using fallback
                                if (e instanceof UnknownHostException) {
                                    throw e;
                                } else {
                                    return null;
                                }
                            }
                        }
                    }
                    return lookupPublic(host);
                }
                private List<InetAddress> lookupPublic(String host) throws UnknownHostException {
                    for (InetAddress dnsServer : fallbackDnsServers) {
                        try {
                            DnsMessage query = client.query(host, Record.TYPE.A, Record.CLASS.IN, dnsServer).response;
                            if (query.responseCode != DnsMessage.RESPONSE_CODE.NO_ERROR) {
                                DiscordSRV.error("DNS server " + dnsServer.getHostAddress() + " failed our DNS query for " + host + ": " + query.responseCode.name());
                            }

                            List<InetAddress> resolved = query.answerSection.stream()
                                    .map(record -> record.payloadData.toString())
                                    .map(s -> {
                                        try {
                                            return InetAddress.getByName(s);
                                        } catch (UnknownHostException e) {
                                            // impossible
                                            error(e);
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .collect(Collectors.toList());
                            if (resolved.size() > 0) {
                                return resolved;
                            } else {
                                DiscordSRV.error("DNS server " + dnsServer.getHostAddress() + " failed to resolve " + host + ": no results");
                            }
                        } catch (Exception e) {
                            DiscordSRV.error("DNS server " + dnsServer.getHostAddress() + " failed to resolve " + host, e);
                        }

                        // this dns server gave us an error so we move this dns server to the end of the
                        // list, effectively making it the last resort for future requests
                        fallbackDnsServers.remove(dnsServer);
                        fallbackDnsServers.add(dnsServer);
                    }

                    // this sleep is here to prevent OkHTTP from repeatedly trying to query DNS servers with no
                    // delay of it's own when internet connectivity is lost. that's extremely bad because it'll be
                    // spitting errors into the console and consuming 100% cpu
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {}

                    UnknownHostException exception = new UnknownHostException("All DNS resolvers failed to resolve hostname " + host + ". Not good.");
                    exception.setStackTrace(new StackTraceElement[]{exception.getStackTrace()[0]});
                    throw exception;
                }
            };
        } catch (Exception e) {
            DiscordSRV.error("Failed to make custom DNS client", e);
        }

        Optional<Boolean> noopHostnameVerifier = config().getOptionalBoolean("NoopHostnameVerifier");
        OkHttpClient httpClient = IOUtil.newHttpClientBuilder()
                .dns(dns)
                // more lenient timeouts (normally 10 seconds for these 3)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .hostnameVerifier(noopHostnameVerifier.isPresent() && noopHostnameVerifier.get()
                        ? (hostname, sslSession) -> true
                        : OkHostnameVerifier.INSTANCE
                )
                .build();

        // set custom RestAction failure handler
        Consumer<? super Throwable> defaultFailure = RestAction.getDefaultFailure();
        RestAction.setDefaultFailure(throwable -> {
            boolean debugRest = config().getBoolean("DebugJDARestActions");
            if (throwable instanceof HierarchyException) {
                DiscordSRV.error("DiscordSRV failed to perform an action due to being lower in hierarchy than the action's target: " + throwable.getMessage());
            } else if (throwable instanceof PermissionException) {
                DiscordSRV.error("DiscordSRV failed to perform an action because the bot is missing the " + ((PermissionException) throwable).getPermission().name() + " permission: " + throwable.getMessage());
            } else if (throwable instanceof RateLimitedException) {
                DiscordSRV.error("DiscordSRV encountered rate limiting. If you are running multiple DiscordSRV instances on the same token, this is considered API abuse and risks your server being IP banned from Discord. Make one bot per server.");
            } else if (throwable instanceof ErrorResponseException) {
                if (((ErrorResponseException) throwable).getErrorCode() == 50013) {
                    // Missing Permissions, too bad we don't know which one
                    DiscordSRV.error("DiscordSRV received a permission error response (50013) from Discord. Unfortunately the specific error isn't provided in that response.");
                    if (debugRest) {
                        DiscordSRV.error(throwable.getCause());
                    } else {
                        DiscordSRV.debug(throwable.getCause());
                    }
                    return;
                }
                DiscordSRV.error("DiscordSRV encountered an unknown Discord error: " + throwable.getMessage());
            } else {
                DiscordSRV.error("DiscordSRV encountered an unknown exception: " + throwable.getMessage() + "\n" + ExceptionUtils.getStackTrace(throwable));
            }

            if (debugRest) {
                Throwable cause = throwable.getCause();
                error(cause);
            }
        });

        File tokenFile = new File(getDataFolder(), ".token");
        String token;
        if (StringUtils.isNotBlank(System.getProperty("DISCORDSRV_TOKEN"))) {
            token = System.getProperty("DISCORDSRV_TOKEN");
            DiscordSRV.debug("Using bot token supplied from JVM property DISCORDSRV_TOKEN");
        } else if (StringUtils.isNotBlank(System.getenv("DISCORDSRV_TOKEN"))) {
            token = System.getenv("DISCORDSRV_TOKEN");
            DiscordSRV.debug("Using bot token supplied from environment variable DISCORDSRV_TOKEN");
        } else if (tokenFile.exists()) {
            try {
                token = FileUtils.readFileToString(tokenFile, StandardCharsets.UTF_8);
                DiscordSRV.debug("Using bot token supplied from " + tokenFile.getPath());
            } catch (IOException e) {
                error(".token file could not be read: " + e.getMessage());
                token = null;
            }
        } else {
            token = config.getString("BotToken");
            DiscordSRV.debug("Using bot token supplied from config");
        }

        if (StringUtils.isBlank(token) || "BOTTOKEN".equalsIgnoreCase(token)) {
            disablePlugin();
            error("No bot token has been set in the config; a bot token is required to connect to Discord.");
            invalidBotToken = true;
            return;
        } else if (token.length() < 59) {
            disablePlugin();
            error("An invalid length bot token (" + token.length() + ") has been set in the config; a valid bot token is required to connect to Discord."
                    + (token.length() == 32 ? " Did you copy the \"Client Secret\" instead of the \"Bot Token\" into the config?" : ""));
            invalidBotToken = true;
            return;
        } else {
            // remove invalid characters
            token = token.replaceAll("[^\\w\\d-_.]", "");
        }

        callbackThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("DiscordSRV - JDA Callback " + worker.getPoolIndex());
            return worker;
        }, null, true);

        final ThreadFactory gatewayThreadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - JDA Gateway").build();
        final ScheduledExecutorService gatewayThreadPool = Executors.newSingleThreadScheduledExecutor(gatewayThreadFactory);

        final ThreadFactory rateLimitThreadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - JDA Rate Limit").build();
        final ScheduledExecutorService rateLimitThreadPool = new ScheduledThreadPoolExecutor(5, rateLimitThreadFactory);

        // log in to discord
        if (config.getBooleanElse("EnablePresenceInformation", false)) {
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_PRESENCES);
            DiscordSRV.api.requireCacheFlag(CacheFlag.ACTIVITY);
            DiscordSRV.api.requireCacheFlag(CacheFlag.CLIENT_STATUS);
        }
        try {
            // see ApiManager for our default intents & cache flags
            jda = JDABuilder.create(api.getIntents())
                    // we disable anything that isn't enabled (everything is enabled by default)
                    .disableCache(Arrays.stream(CacheFlag.values()).filter(cacheFlag -> !api.getCacheFlags().contains(cacheFlag)).collect(Collectors.toList()))
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setCallbackPool(callbackThreadPool, false)
                    .setGatewayPool(gatewayThreadPool, true)
                    .setRateLimitPool(rateLimitThreadPool, true)
                    .setWebsocketFactory(new WebSocketFactory()
                            .setDualStackMode(DualStackMode.IPV4_ONLY)
                    )
                    .setHttpClient(httpClient)
                    .setAutoReconnect(true)
                    .setBulkDeleteSplittingEnabled(false)
                    .setToken(token)
                    .addEventListeners(new DiscordChatListener())
                    .addEventListeners(new DiscordConsoleListener())
                    .addEventListeners(new DiscordAccountLinkListener())
                    .addEventListeners(new DiscordDisconnectListener())
                    .setContextEnabled(false)
                    .build();
            jda.awaitReady(); // let JDA be assigned as soon as we can, but wait until it's ready

            for (Guild guild : jda.getGuilds()) {
                guild.retrieveOwner(true).queue();
                guild.loadMembers()
                        .onSuccess(members -> DiscordSRV.debug("Loaded " + members.size() + " members in guild " + guild))
                        .onError(throwable -> DiscordSRV.error("Failed to retrieve members of guild " + guild, throwable))
                        .get(); // block DiscordSRV startup until members are loaded
            }
        } catch (LoginException e) {
            disablePlugin();
            if (e.getMessage().toLowerCase().contains("the provided token is invalid")) {
                invalidBotToken = true;
                DiscordDisconnectListener.printDisconnectMessage(true, "The bot token is invalid");
            } else {
                DiscordDisconnectListener.printDisconnectMessage(true, e.getMessage());
            }
            return;
        } catch (Exception e) {
            if (e instanceof IllegalStateException && e.getMessage().equals("Was shutdown trying to await status")) {
                // already logged by JDA
                return;
            }
            DiscordSRV.error("An unknown error occurred building JDA...", e);
            return;
        }

        // start presence updater thread
        if (presenceUpdater != null) {
            if (presenceUpdater.getState() != Thread.State.NEW) {
                presenceUpdater.interrupt();
                presenceUpdater = new PresenceUpdater();
            }
            getProxy().getScheduler().schedule(this, () -> presenceUpdater.start(), 5, TimeUnit.SECONDS);
        } else {
            presenceUpdater = new PresenceUpdater();
            presenceUpdater.start();
        }

        // start nickname updater thread
        if (nicknameUpdater != null) {
            if (nicknameUpdater.getState() != Thread.State.NEW) {
                nicknameUpdater.interrupt();
                nicknameUpdater = new NicknameUpdater();
            }
            getProxy().getScheduler().schedule(this, () -> nicknameUpdater.start(), 5, TimeUnit.SECONDS);
        } else {
            nicknameUpdater = new NicknameUpdater();
            nicknameUpdater.start();
        }

        // print the things the bot can see
        if (config().getBoolean("PrintGuildsAndChannels")) {
            for (Guild server : jda.getGuilds()) {
                DiscordSRV.info(LangUtil.InternalMessage.FOUND_SERVER + " " + server);
                for (TextChannel channel : server.getTextChannels()) DiscordSRV.info("- " + channel);
            }
        }

        // show warning if bot wasn't in any guilds
        if (jda.getGuilds().size() == 0) {
            DiscordSRV.error(LangUtil.InternalMessage.BOT_NOT_IN_ANY_SERVERS);
            DiscordSRV.error(jda.getInviteUrl(Permission.ADMINISTRATOR));
            return;
        }

        // see if console channel exists; if it does, tell user where it's been assigned & add console appender
        if (serverIsLog4jCapable) {
            DiscordSRV.info(getConsoleChannel() != null
                    ? LangUtil.InternalMessage.CONSOLE_FORWARDING_ASSIGNED_TO_CHANNEL + " " + getConsoleChannel()
                    : LangUtil.InternalMessage.NOT_FORWARDING_CONSOLE_OUTPUT.toString());

            // attach appender to queue console messages
            consoleAppender = new ConsoleAppender();

            // start console message queue worker thread
            if (consoleMessageQueueWorker != null) {
                if (consoleMessageQueueWorker.getState() != Thread.State.NEW) {
                    consoleMessageQueueWorker.interrupt();
                    consoleMessageQueueWorker = new ConsoleMessageQueueWorker();
                }
            } else {
                consoleMessageQueueWorker = new ConsoleMessageQueueWorker();
            }
            consoleMessageQueueWorker.start();
        }

        reloadChannels();
        reloadRegexes();
        reloadRoleAliases();

        // warn if the console channel is connected to a chat channel
        if (getMainTextChannel() != null && getConsoleChannel() != null && getMainTextChannel().getId().equals(getConsoleChannel().getId())) DiscordSRV.warning(LangUtil.InternalMessage.CONSOLE_CHANNEL_ASSIGNED_TO_LINKED_CHANNEL);

        // send server startup message
        getProxy().getScheduler().schedule(this, () -> {
            DiscordUtil.queueMessage(
                    getOptionalTextChannel("status"),
                    PlaceholderUtil.replacePlaceholdersToDiscord(LangUtil.Message.SERVER_STARTUP_MESSAGE.toString()),
                    true
            );
        }, 1, TimeUnit.SECONDS);

        // big warning about respect chat plugins
        if (!config().getBooleanElse("RespectChatPlugins", true)) DiscordSRV.warning(LangUtil.InternalMessage.RESPECT_CHAT_PLUGINS_DISABLED);

        // load account links
        if (JdbcAccountLinkManager.shouldUseJdbc()) {
            try {
                accountLinkManager = new JdbcAccountLinkManager();
            } catch (SQLException e) {
                StringBuilder stringBuilder = new StringBuilder("JDBC account link backend failed to initialize: ").append(ExceptionUtils.getMessage(e));

                Throwable selected = e.getCause();
                while (selected != null) {
                    stringBuilder.append("\n").append("Caused by: ").append(selected instanceof UnknownHostException ? "UnknownHostException" : ExceptionUtils.getMessage(selected));
                    selected = selected.getCause();
                }

                String message = stringBuilder.toString()
                        .replace(config.getString("Experiment_JdbcAccountLinkBackend"), "<jdbc url>")
                        .replace(config.getString("Experiment_JdbcUsername"), "<jdbc username>");
                if (!StringUtils.isEmpty(config.getString("Experiment_JdbcPassword"))) {
                    message = message.replace(config.getString("Experiment_JdbcPassword"), "");
                }

                DiscordSRV.warning(message);
                DiscordSRV.warning("Account link manager falling back to flat file");
                accountLinkManager = new FileAccountLinkManager();
            }
        } else {
            accountLinkManager = new FileAccountLinkManager();
        }
        getProxy().getPluginManager().registerListener(this, accountLinkManager);

        // plugin hooks
        /*
        for (String hookClassName : Arrays.asList("classes")) {
            try {
                Class<?> hookClass = Class.forName(hookClassName);

                PluginHook pluginHook = (PluginHook) hookClass.getDeclaredConstructor().newInstance();
                if (pluginHook.isEnabled()) {
                    DiscordSRV.info(LangUtil.InternalMessage.PLUGIN_HOOK_ENABLING.toString().replace("{plugin}", pluginHook.getPlugin().getDescription().getName()));
                    getProxy().getPluginManager().registerListener(this, pluginHook);
                    try {
                        pluginHook.hook();
                        pluginHooks.add(pluginHook);
                    } catch (Throwable t) {
                        error("Failed to hook " + hookClassName, t);
                    }
                }
            } catch (Throwable e) {
                // ignore class not found errors
                if (!(e instanceof ClassNotFoundException) && !(e instanceof NoClassDefFoundError)) {
                    DiscordSRV.error("Failed to load " + hookClassName, e);
                }
            }
        }
        */

        // start channel topic updater
        if (channelTopicUpdater != null) {
            if (channelTopicUpdater.getState() != Thread.State.NEW) {
                channelTopicUpdater.interrupt();
                channelTopicUpdater = new ChannelTopicUpdater();
            }
        } else {
            channelTopicUpdater = new ChannelTopicUpdater();
        }
        channelTopicUpdater.start();

        alertListener = new AlertListener();
        jda.addEventListener(alertListener);

        // set ready status
        if (jda.getStatus() == JDA.Status.CONNECTED) {
            isReady = true;
            api.callEvent(new DiscordReadyEvent());
        }
    }

    @Override
    public void onDisable() {
        final long shutdownStartTime = System.currentTimeMillis();

        // prepare the shutdown message
        String shutdownFormat = LangUtil.Message.SERVER_SHUTDOWN_MESSAGE.toString();

        // Check if the format contains a placeholder (Takes long to do cause the server is shutting down)
        // need to run this on the main thread
        if (Pattern.compile("%[^%]+%").matcher(shutdownFormat).find()) {
            shutdownFormat = PlaceholderUtil.replacePlaceholdersToDiscord(shutdownFormat);
        }

        final ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("DiscordSRV - Shutdown").build();
        final ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
        try {
            String finalShutdownFormat = shutdownFormat;
            executor.invokeAll(Collections.singletonList(() -> {
                // set server shutdown topics if enabled
                if (config().getBoolean("ChannelTopicUpdaterChannelTopicsAtShutdownEnabled")) {
                    String time = TimeUtil.timeStamp();
                    String serverVersion = getProxy().getVersion();
                    String totalPlayers = Integer.toString(getTotalPlayerCount());
                    DiscordUtil.setTextChannelTopic(
                            getMainTextChannel(),
                            LangUtil.Message.CHAT_CHANNEL_TOPIC_AT_SERVER_SHUTDOWN.toString()
                                    .replaceAll("%time%|%date%", time)
                                    .replace("%serverversion%", serverVersion)
                                    .replace("%totalplayers%", totalPlayers)
                    );
                    DiscordUtil.setTextChannelTopic(
                            getConsoleChannel(),
                            LangUtil.Message.CONSOLE_CHANNEL_TOPIC_AT_SERVER_SHUTDOWN.toString()
                                    .replaceAll("%time%|%date%", time)
                                    .replace("%serverversion%", serverVersion)
                                    .replace("%totalplayers%", totalPlayers)
                    );
                }

                // we're no longer ready
                isReady = false;

                // shutdown scheduler tasks
                getProxy().getScheduler().cancel(this);

                // stop alerts
                //if (alertListener != null) alertListener.unregister();

                // kill channel topic updater
                if (channelTopicUpdater != null) channelTopicUpdater.interrupt();

                // kill console message queue worker
                if (consoleMessageQueueWorker != null) consoleMessageQueueWorker.interrupt();

                // kill presence updater
                if (presenceUpdater != null) presenceUpdater.interrupt();

                // kill nickname updater
                if (nicknameUpdater != null) nicknameUpdater.interrupt();

                // shutdown the update checker
                if (updateChecker != null) updateChecker.shutdown();

                // serialize account links to disk
                if (accountLinkManager != null) accountLinkManager.save();

                // shutdown the console appender
                if (consoleAppender != null) consoleAppender.shutdown();

                // remove the jda filter
                if (jdaFilter != null) {
                    try {
                        org.apache.logging.log4j.core.Logger logger = ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger());

                        Field configField = null;
                        Class<?> targetClass = logger.getClass();

                        // get a field named config or privateConfig from the logger class or any of it's super classes
                        while (targetClass != null) {
                            try {
                                configField = targetClass.getDeclaredField("config");
                                break;
                            } catch (NoSuchFieldException ignored) {}

                            try {
                                configField = targetClass.getDeclaredField("privateConfig");
                                break;
                            } catch (NoSuchFieldException ignored) {}

                            targetClass = targetClass.getSuperclass();
                        }

                        if (configField != null) {
                            if (!configField.isAccessible()) configField.setAccessible(true);

                            Object config = configField.get(logger);
                            Field configField2 = config.getClass().getDeclaredField("config");
                            if (!configField2.isAccessible()) configField2.setAccessible(true);

                            Object config2 = configField2.get(config);
                            if (config2 instanceof org.apache.logging.log4j.core.filter.Filterable) {
                                ((org.apache.logging.log4j.core.filter.Filterable) config2).removeFilter(jdaFilter);
                                jdaFilter = null;
                                debug("JdaFilter removed");
                            }
                        }
                    } catch (Throwable t) {
                        getLogger().warning("Could not remove JDA Filter: " + t.toString());
                    }
                }

                // Clear JDA listeners
                if (jda != null) jda.getEventManager().getRegisteredListeners().forEach(listener -> jda.getEventManager().unregister(listener));

                // send server shutdown message
                DiscordUtil.sendMessageBlocking(getOptionalTextChannel("status"), finalShutdownFormat, true);

                // try to shut down jda gracefully
                if (jda != null) {
                    CompletableFuture<Void> shutdownTask = new CompletableFuture<>();
                    jda.addEventListener(new ListenerAdapter() {
                        @Override
                        public void onShutdown(@NotNull ShutdownEvent event) {
                            shutdownTask.complete(null);
                        }
                    });
                    jda.shutdownNow();
                    jda = null;
                    try {
                        shutdownTask.get(5, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        getLogger().warning("JDA took too long to shut down, skipping");
                    }
                }

                if (callbackThreadPool != null) callbackThreadPool.shutdownNow();

                DiscordSRV.info(LangUtil.InternalMessage.SHUTDOWN_COMPLETED.toString()
                        .replace("{ms}", String.valueOf(System.currentTimeMillis() - shutdownStartTime))
                );

                return null;
            }), 15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error(e);
        }
        executor.shutdownNow();
    }

    public MessageFormat getMessageFromConfiguration(String key) {
        return MessageFormatResolver.getMessageFromConfiguration(config(), key);
    }

    public static int getLength(Message message) {
        StringBuilder content = new StringBuilder();
        content.append(message.getContentRaw());

        message.getEmbeds().stream().findFirst().ifPresent(embed -> {
            if (embed.getTitle() != null) {
                content.append(embed.getTitle());
            }
            if (embed.getDescription() != null) {
                content.append(embed.getDescription());
            }
            if (embed.getAuthor() != null) {
                content.append(embed.getAuthor().getName());
            }
            for (MessageEmbed.Field field : embed.getFields()) {
                content.append(field.getName()).append(field.getValue());
            }
        });

        return content.toString().replaceAll("[^A-z]", "").length();
    }

    public List<Role> getSelectedRoles(Member member) {
        List<Role> selectedRoles;
        List<String> discordRolesSelection = DiscordSRV.config().getStringList("DiscordChatChannelRolesSelection");
        // if we have a whitelist in the config
        if (DiscordSRV.config().getBoolean("DiscordChatChannelRolesSelectionAsWhitelist")) {
            selectedRoles = member.getRoles().stream()
                    .filter(role -> discordRolesSelection.contains(DiscordUtil.getRoleName(role)))
                    .collect(Collectors.toList());
        } else { // if we have a blacklist in the settings
            selectedRoles = member.getRoles().stream()
                    .filter(role -> !discordRolesSelection.contains(DiscordUtil.getRoleName(role)))
                    .collect(Collectors.toList());
        }
        selectedRoles.removeIf(role -> StringUtils.isBlank(role.getName()));
        return selectedRoles;
    }

    public Map<String, String> getGroupSynchronizables() {
        HashMap<String, String> map = new HashMap<>();
        config.dget("GroupRoleSynchronizationGroupsAndRolesToSync").children().forEach(dynamic ->
                map.put(dynamic.key().convert().intoString(), dynamic.convert().intoString()));
        return map;
    }

    public Map<String, String> getCannedResponses() {
        Map<String, String> responses = new HashMap<>();
        config.dget("DiscordCannedResponses").children()
                .forEach(dynamic -> {
                    String trigger = dynamic.key().convert().intoString();
                    if (StringUtils.isEmpty(trigger)) {
                        DiscordSRV.debug("Skipping canned response with empty trigger");
                        return;
                    }
                    responses.put(trigger, dynamic.convert().intoString());
                });
        return responses;
    }

    public static int getTotalPlayerCount() {
        return 114514;
    }

    /**
     * @return Whether or not file system is limited. If this is {@code true}, DiscordSRV will limit itself to not
     * modifying the server's plugins folder. This is used to prevent uploading of plugins via the console channel.
     */
    public static boolean isFileSystemLimited() {
        return System.getenv("LimitFS") != null || System.getProperty("LimitFS") != null
                || !config().getBooleanElse("DiscordConsoleChannelAllowPluginUpload", false);
    }

    /**
     * @return Whether or not DiscordSRV should disable it's update checker. Doing so is dangerous and can lead to
     * security vulnerabilities. You shouldn't use this.
     */
    public static boolean isUpdateCheckDisabled() {
        return System.getenv("NoUpdateChecks") != null || System.getProperty("NoUpdateChecks") != null ||
                config().getBooleanElse("UpdateCheckDisabled", false);
    }

    /**
     * @return Whether or not DiscordSRV group role synchronization has been enabled in the configuration.
     */
    public boolean isGroupRoleSynchronizationEnabled() {
        return isGroupRoleSynchronizationEnabled(true);
    }

    /**
     * @return Whether or not DiscordSRV group role synchronization has been enabled in the configuration.
     * @param checkPermissions whether or not to check if Vault is available
     */
    public boolean isGroupRoleSynchronizationEnabled(boolean checkPermissions) {
        final Map<String, String> groupsAndRolesToSync = config.getMap("GroupRoleSynchronizationGroupsAndRolesToSync");
        if (groupsAndRolesToSync.isEmpty()) return false;
        for (Map.Entry<String, String> entry : groupsAndRolesToSync.entrySet()) {
            final String group = entry.getKey();
            if (!group.isEmpty()) {
                final String roleId = entry.getValue();
                if (!(roleId.isEmpty() || roleId.equals("000000000000000000"))) return true;
            }
        }
        return false;
    }

    public String getOptionalChannel(String name) {
        return getChannels().containsKey(name)
                ? name
                : getMainChatChannel();
    }
    public TextChannel getOptionalTextChannel(String gameChannel) {
        return getDestinationTextChannelForGameChannelName(getOptionalChannel(gameChannel));
    }

}
