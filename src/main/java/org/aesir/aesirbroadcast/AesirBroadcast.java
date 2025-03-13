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
import net.kyori.adventure.text.format.TextColor;
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
import java.io.*;
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
    private final String broadcastURL = "https://store.aesirmc.com";
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

    // Plugin başlatıldığında çağrılır ve komutları kaydeder
    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig(); // Konfigürasyon dosyasını yükler
        server.getCommandManager().register("ab", new AesirBroadcastCommand(), "aesirbroadcast");
        startYouTubeLiveChecker();

    }

    // Konfigürasyon dosyasını okur ve yükler
    private void loadConfig() {
        try {
            Files.createDirectories(configPath.getParent()); // Eğer dizin yoksa oluştur
            if (!Files.exists(configPath)) {
                // Varsayılan konfigürasyon dosyasını oluşturur
                String defaultConfig = """
                Broadcast:
                  - "&aAesirMC is now live!"
                  - "&bJoin the adventure now!"
                TitleMainMessage: "<red>Announcement!</red>"
                CustomTitleMainMessage: "<blue>Custom Announcement!</blue>"
                TitleMessage: "<green>AesirMC is now live!</green>"
                TitleFadeIn: 1
                TitleStay: 3
                TitleFadeOut: 1
                BroadcastInterval: 900
                YouTubeAPIKey: "YOUR_YOUTUBE_API_KEY"
                YouTubeChannelID: "YOUR_CHANNEL_ID"
                YouTubeCheckInterval: 60
                """;
                Files.write(configPath, defaultConfig.getBytes(), StandardOpenOption.CREATE);
            }

            // YAML dosyasını okur ve değerlere atar
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
            logger.info("Configuration reloaded successfully.");
        } catch (IOException e) {
            logger.error("Error loading configuration file!", e);
            broadcastMessages = List.of("AesirMC is now live!");
            titleMessage = "Announcement! AesirMC is now live!";
        }
    }

    // /ab komutunu yöneten sınıf
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
        try {
            URL url = new URL("https://www.googleapis.com/youtube/v3/search?part=snippet&channelId=" + youtubeChannelId + "&type=video&eventType=live&key=" + youtubeApiKey);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

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
                    .clickEvent(ClickEvent.openUrl("https://www.youtube.com/channel/" + youtubeChannelId));
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
                invocation.source().sendMessage(Component.text("Usage: /ab <broadcast|title|reload>"));
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
                    // Oyuncunun yetkisi olup olmadığını kontrol eder
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.broadcast")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    // Broadcast mesajlarını tüm oyunculara gönderir
                    for (String message : broadcastMessages) {
                        Component component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(message)
                                .clickEvent(ClickEvent.openUrl(broadcastURL));
                        server.getAllPlayers().forEach(p -> p.sendMessage(component));
                    }
                    logger.info("Broadcast messages sent: " + broadcastMessages);
                    break;

                case "title":
                    // Oyuncunun yetkisi olup olmadığını kontrol eder
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.title")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    // Title mesajını tüm oyunculara gönderir
                    Component title = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(titleMainMessage);
                    Component subtitle = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(titleMessage);
                    Title velocityTitle = Title.title(title, subtitle, Title.Times.of(Duration.ofSeconds(titleFadeIn), Duration.ofSeconds(titleStay), Duration.ofSeconds(titleFadeOut)));
                    server.getAllPlayers().forEach(p -> p.showTitle(velocityTitle));
                    logger.info("Title message sent: " + titleMessage);
                    break;

                case "reload":
                    // Oyuncunun yetkisi olup olmadığını kontrol eder
                    if (invocation.source() instanceof Player player && !player.hasPermission("aesirbroadcast.reload")) {
                        player.sendMessage(Component.text("You do not have permission to use this command!"));
                        return;
                    }
                    loadConfig(); // Konfigürasyonu yeniden yükler
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
