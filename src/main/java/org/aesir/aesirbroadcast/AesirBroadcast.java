package org.aesir.aesirbroadcast;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Plugin(id = "aesirbroadcast", name = "AesirBroadcast", version = "1.0", authors = {"AesirMC"})
public class AesirBroadcast {

    private final ProxyServer server;
    private final Logger logger;
    private final Path configPath;
    private List<String> broadcastMessages;
    private String titleMessage;
    private String titleMainMessage;
    private String customTitleMainMessage;
    private String hoverMessage; // Hover message for broadcast texts.
    private String broadcastURL; // URL to open when clicking broadcast messages.
    private String youtubeApiKey;
    private String youtubeChannelId;
    private int youtubeCheckInterval;
    private boolean isLive = false;
    private int broadcastInterval;
    private boolean isBroadcastRunning = false;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;

    @Inject
    public AesirBroadcast(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.configPath = dataDirectory.resolve("config.yml");
    }

    // Called when the plugin is initialized; registers commands and starts the YouTube live checker.
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig(); // Load configuration file.
        server.getCommandManager().register("ab", new AesirBroadcastCommand(), "aesirbroadcast");
        startYouTubeLiveChecker();
    }

    // Reads and loads the configuration file.
    private void loadConfig() {
        try {
            Files.createDirectories(configPath.getParent()); // Create directory if it doesn't exist.
            if (!Files.exists(configPath)) {
                // Create a default configuration file with detailed comments explaining each setting.
                String defaultConfig = """
                # Broadcast: List of messages that will be sent to players.
                # These messages are used when a live broadcast is detected or when a broadcast command is executed.
                Broadcast:
                  - "&aAesirMC is now live!"
                  - "&bJoin the adventure now!"
                
                # TitleMainMessage: The main title message displayed on players' screens.
                # This appears as the primary title when the title command is used.
                TitleMainMessage: "<red>Announcement!</red>"
                
                # CustomTitleMainMessage: The main section of the title for custom messages.
                # This is used when executing the /ab ozeltitle command.
                CustomTitleMainMessage: "<blue>Custom Announcement!</blue>"
                
                # TitleMessage: The subtitle text that appears beneath the main title.
                TitleMessage: "<green>AesirMC is now live!</green>"
                
                # TitleFadeIn: Duration (in seconds) for the title to fade in.
                TitleFadeIn: 1
                
                # TitleStay: Duration (in seconds) for which the title remains on the screen.
                TitleStay: 3
                
                # TitleFadeOut: Duration (in seconds) for the title to fade out.
                TitleFadeOut: 1
                
                # BroadcastInterval: Interval (in seconds) between automatic broadcast messages
                # when using the broadcaststart command.
                BroadcastInterval: 900
                
                # YouTubeAPIKey: Your YouTube API key, required to check the live stream status.
                YouTubeAPIKey: "YOUR_YOUTUBE_API_KEY"
                
                # YouTubeChannelID: Your YouTube channel ID, used to verify the live stream status via the API.
                YouTubeChannelID: "YOUR_CHANNEL_ID"
                
                # YouTubeCheckInterval: Interval (in seconds) at which the plugin checks if your YouTube channel is live.
                YouTubeCheckInterval: 60
                
                # HoverMessage: The message shown when a player hovers over a broadcast message.
                # Supports MiniMessage formatting.
                HoverMessage: "Click <red>here</red> for more details!"
                
                # BroadcastURL: The URL that opens when a player clicks on the broadcast message.
                BroadcastURL: "https://store.aesirmc.com"
                """;
                Files.write(configPath, defaultConfig.getBytes(), StandardOpenOption.CREATE);
            }

            // Load configuration from YAML.
            @SuppressWarnings("unchecked")
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(Files.newBufferedReader(configPath));
            Object broadcastObj = config.get("Broadcast");
            if (broadcastObj instanceof List<?>) {
                broadcastMessages = ((List<?>) broadcastObj).stream()
                        .filter(item -> item instanceof String)
                        .map(item -> (String) item)
                        .toList();
            } else {
                broadcastMessages = List.of("&aAesirMC is now live!");
            }
            titleMainMessage = (String) config.getOrDefault("TitleMainMessage", "<red>Announcement!</red>");
            customTitleMainMessage = (String) config.getOrDefault("CustomTitleMainMessage", "<blue>Custom Announcement!</blue>");
            titleMessage = (String) config.getOrDefault("TitleMessage", "<green>AesirMC is now live!</green>");
            titleFadeIn = (int) config.getOrDefault("TitleFadeIn", 1);
            titleStay = (int) config.getOrDefault("TitleStay", 3);
            titleFadeOut = (int) config.getOrDefault("TitleFadeOut", 1);
            if (broadcastMessages == null) broadcastMessages = List.of("AesirMC is now live!");
            if (titleMainMessage == null) titleMainMessage = "<red>Announcement!</red>";
            if (customTitleMainMessage == null) customTitleMainMessage = "<blue>Custom Announcement!</blue>";
            if (titleMessage == null) titleMessage = "<green>AesirMC is now live!</green>";
            broadcastInterval = (int) config.getOrDefault("BroadcastInterval", 900);
            youtubeApiKey = (String) config.getOrDefault("YouTubeAPIKey", "YOUR_YOUTUBE_API_KEY");
            youtubeChannelId = (String) config.getOrDefault("YouTubeChannelID", "YOUR_CHANNEL_ID");
            youtubeCheckInterval = (int) config.getOrDefault("YouTubeCheckInterval", 60);
            hoverMessage = (String) config.getOrDefault("HoverMessage", "Click here for more details!");
            broadcastURL = (String) config.getOrDefault("BroadcastURL", "https://store.aesirmc.com");
            logger.info("Configuration reloaded successfully.");
        } catch (IOException e) {
            logger.error("Error loading configuration file!", e);
            broadcastMessages = List.of("AesirMC is now live!");
            titleMessage = "Announcement! AesirMC is now live!";
            hoverMessage = "Click here for more details!";
            broadcastURL = "https://store.aesirmc.com";
        }
    }

    // Starts a task that periodically checks if the YouTube channel is live.
    private void startYouTubeLiveChecker() {
        server.getScheduler().buildTask(this, () -> {
            boolean currentlyLive = checkIfLiveOnYouTube();
            if (currentlyLive && !isLive) {
                isLive = true;
                broadcastYouTubeLive();
            } else if (!currentlyLive && isLive) {
                isLive = false;
            }
        }).repeat(Duration.ofSeconds(youtubeCheckInterval)).schedule();
    }

    private boolean checkIfLiveOnYouTube() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&channelId="
                    + youtubeChannelId + "&type=video&eventType=live&key=" + youtubeApiKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // Add a user agent to the request.
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // If not OK, read the error stream for details.
                BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                String line;
                StringBuilder errorResponse = new StringBuilder();
                while ((line = err.readLine()) != null) {
                    errorResponse.append(line);
                }
                err.close();
                logger.error("Error checking YouTube live status: {} - {}", responseCode, errorResponse.toString());
                return false;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString().contains("\"liveBroadcastContent\":\"live\"");
        } catch (IOException e) {
            logger.error("Error checking YouTube live status", e);
            return false;
        }
    }

    private void broadcastYouTubeLive() {
        for (String message : broadcastMessages) {
            Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(hoverMessage)
                    ))
                    .clickEvent(ClickEvent.openUrl(broadcastURL));
            server.getAllPlayers().forEach(p -> p.sendMessage(component));
        }
        logger.info("Broadcasting YouTube live event to players.");
    }

    private class AesirBroadcastCommand implements SimpleCommand {
        @Override
        public List<String> suggest(Invocation invocation) {
            if (invocation.arguments().length == 0) {
                return List.of("broadcast", "title", "reload", "ozeltitle");
            }
            String subcommand = invocation.arguments()[0].toLowerCase();
            if (subcommand.equals("ozeltitle") && invocation.arguments().length == 1) {
                return List.of("<your_message_here>");
            }
            return List.of();
        }
        @Override
        public void execute(Invocation invocation) {
            if (invocation.arguments().length == 0) {
                invocation.source().sendMessage(Component.text("Usage: /ab <broadcast|title|reload|ozeltitle>"));
                return;
            }
            String subcommand = invocation.arguments()[0].toLowerCase();
            switch (subcommand) {
                case "broadcaststart":
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.broadcaststart")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    if (isBroadcastRunning) {
                        invocation.source().sendMessage(Component.text("Broadcast is already running!"));
                        return;
                    }
                    isBroadcastRunning = true;
                    server.getScheduler().buildTask(AesirBroadcast.this, () -> {
                        if (isBroadcastRunning) {
                            for (String message : broadcastMessages) {
                                Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
                                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(hoverMessage)
                                        ))
                                        .clickEvent(ClickEvent.openUrl(broadcastURL));
                                server.getAllPlayers().forEach(p -> p.sendMessage(component));
                            }
                            logger.info("Broadcast sent automatically.");
                        }
                    }).repeat(Duration.ofSeconds(broadcastInterval)).schedule();
                    invocation.source().sendMessage(Component.text("Broadcast started every " + broadcastInterval + " seconds."));
                    break;
                case "broadcaststop":
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.broadcaststop")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    if (!isBroadcastRunning) {
                        invocation.source().sendMessage(Component.text("No active broadcast to stop!"));
                        return;
                    }
                    isBroadcastRunning = false;
                    invocation.source().sendMessage(Component.text("Broadcast stopped."));
                    break;
                case "broadcast":
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.broadcast")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    for (String message : broadcastMessages) {
                        Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(hoverMessage)
                                ))
                                .clickEvent(ClickEvent.openUrl(broadcastURL));
                        server.getAllPlayers().forEach(p -> p.sendMessage(component));
                    }
                    logger.info("Broadcast messages sent: " + broadcastMessages);
                    break;
                case "title":
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.title")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    Component title = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(titleMainMessage);
                    Component subtitle = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(titleMessage);
                    Title velocityTitle = Title.title(title, subtitle, Title.Times.of(Duration.ofSeconds(titleFadeIn), Duration.ofSeconds(titleStay), Duration.ofSeconds(titleFadeOut)));
                    server.getAllPlayers().forEach(p -> p.showTitle(velocityTitle));
                    logger.info("Title message sent: " + titleMessage);
                    break;
                case "reload":
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.reload")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    loadConfig();
                    invocation.source().sendMessage(Component.text("Configuration reloaded successfully!"));
                    break;
                case "ozeltitle":
                    if (invocation.arguments().length < 2) {
                        invocation.source().sendMessage(Component.text("Usage: /ab ozeltitle <message>"));
                        return;
                    }
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.ozeltitle")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    String customTitleMessage = String.join(" ", invocation.arguments()).replaceFirst("ozeltitle ", "");
                    Component customTitle = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(customTitleMainMessage);
                    Component customSubtitle = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(customTitleMessage);
                    Title customVelocityTitle = Title.title(customTitle, customSubtitle, Title.Times.of(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1)));
                    server.getAllPlayers().forEach(p -> p.showTitle(customVelocityTitle));
                    logger.info("Custom title sent: " + customTitleMessage);
                    break;
                default:
                    invocation.source().sendMessage(Component.text("Unknown subcommand. Usage: /ab <broadcast|title|reload|ozeltitle>"));
                    break;
            }
        }
    }
}
