package com.discord.loot;

import com.google.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

@PluginDescriptor(
        name = "August Extended Loot Notifications",
        description = "Send loot information to Discord, PM, tray, and sound."
)
public class NpcDropDiscordPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    private static DiscordLootPanel panel;
    private NavigationButton navButton;
    private static BufferedImage trayIconImage;

    private static final Set<String> PET_MESSAGES = Set.of(
            "You have a funny feeling like you're being followed",
            "You feel something weird sneaking into your backpack",
            "You have a funny feeling like you would have been followed"
    );

    @Override
    protected void startUp() throws Exception {
        panel = new DiscordLootPanel();
        loadTrayIcon();
        setupNavigationButton();
        System.out.println("Discord Loot Notifier started!");
    }

    @Override
    protected void shutDown() throws Exception {
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        System.out.println("Discord Loot Notifier stopped!");
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE)
            return;
        String chatMessage = event.getMessage();
        Objects.requireNonNull(chatMessage);
        if (panel.isPetsEnabled() && PET_MESSAGES.stream().anyMatch(chatMessage::contains)) {
            clientThread.invokeLater(() -> {
                if (panel.isDiscordEnabled()) sendDiscordNotificationForPet(chatMessage);
                if (panel.isPmEnabled()) sendPrivateMessageForPet(chatMessage);
                if (panel.isTrayEnabled()) showTrayNotificationForPet(chatMessage);
                if (panel.isSoundEnabled()) panel.playSound();
                panel.addLootFeedForPet(chatMessage);
            });
        }
    }


    private void loadTrayIcon() throws IOException {
        // Leading '/' = absolute path from resources root
        try (InputStream is = getClass().getResourceAsStream("/icon.png")) {
            if (is == null) {
                throw new IllegalStateException("icon.png not found in resources!");
            }
            trayIconImage = ImageIO.read(is);
        }
    }

    private void setupNavigationButton() {
        navButton = NavigationButton.builder()
                .tooltip("Discord Loot Notifier")
                .icon(trayIconImage != null ? trayIconImage : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
                .priority(1)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(navButton);
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event) {
        NPC npc = event.getNpc();
        String npcName = npc.getName();
        String playerName = client.getLocalPlayer().getName();
        event.getItems().forEach(itemStack -> {
            String itemName = client.getItemDefinition(itemStack.getId()).getName();
            int quantity = itemStack.getQuantity();
            boolean isPriority = panel.getPriorityDrops().contains(itemName.toLowerCase());
            if (!isPriority && panel.isFortuneEnabled()) {
                String lower = itemName.toLowerCase();
                if (lower.contains("box") || lower.contains("cache") || lower.contains("crate") ||
                        lower.contains("pack") || lower.contains("present")) {
                    isPriority = true;
                }
            }

            if (isPriority) {
                clientThread.invokeLater(() -> {
                    if (panel.isDiscordEnabled()) sendDiscordNotification(itemName, npcName, playerName, quantity);
                    if (panel.isPmEnabled()) sendPrivateMessage(itemName, quantity);
                    if (panel.isTrayEnabled()) showTrayNotification(itemName, quantity);
                    if (panel.isSoundEnabled()) panel.playSound();
                    panel.addLootFeed(itemName, npcName);
                });
            }
        });
    }

    public static void sendDiscordNotification(String itemName, String npcName, String playerName, int quantity) {
        String webhook = panel.getWebhookUrl();
        if (webhook == null || webhook.isEmpty())
            return;

        try {
            String json = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"" + escapeJson(itemName) + "\","
                    + "\"description\": \"" + quantity + "x " + escapeJson(itemName) + " dropped by " + escapeJson(npcName)
                    + " for " + escapeJson(playerName) + "\","
                    + "\"color\": 65280"
                    + "}]"
                    + "}";

            URL url = new URL(webhook);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.addRequestProperty("Content-Type", "application/json");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204 && responseCode != 200)
                System.err.println("Discord webhook failed with code: " + responseCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPrivateMessage(String itemName, int quantity) {
        client.addChatMessage(
                ChatMessageType.PRIVATECHAT,
                "<img=46> Loot",
                "You received " + quantity + "x " + itemName + "!",
                null
        );
    }

    public static void showTrayNotification(String itemName, int quantity) {
        SwingUtilities.invokeLater(() -> {
            if (!SystemTray.isSupported()) return;
            try {
                SystemTray tray = SystemTray.getSystemTray();
                TrayIcon icon = new TrayIcon(trayIconImage != null ? trayIconImage : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "Loot Tracker");
                icon.setImageAutoSize(true);
                tray.add(icon);
                icon.displayMessage("Loot Tracker", "You received " + quantity + "x " + itemName + "!", MessageType.INFO);

                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        tray.remove(icon);
                    } catch (Exception ignored) {
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static void sendDiscordNotificationForPet(String chatMessage) {
        String webhook = panel.getWebhookUrl();
        if (webhook == null || webhook.isEmpty()) {
            return;
        }

        try {
            String json = "{"
                    + "\"embeds\": [{"
                    + "\"title\": \"Pet Drop\","
                    + "\"description\": \"" + escapeJson(chatMessage) + "\","
                    + "\"color\": 65280"
                    + "}]"
                    + "}";

            URL url = new URL(webhook);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.addRequestProperty("Content-Type", "application/json");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 204 && responseCode != 200) {
                System.err.println("Discord webhook failed with code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPrivateMessageForPet(String chatMessage) {
        client.addChatMessage(
                ChatMessageType.PRIVATECHAT,
                "<img=46> Loot",
                chatMessage,
                null
        );
    }

    public static void showTrayNotificationForPet(String chatMessage) {
        SwingUtilities.invokeLater(() -> {
            if (!SystemTray.isSupported()) return;
            try {
                SystemTray tray = SystemTray.getSystemTray();
                TrayIcon icon = new TrayIcon(trayIconImage != null ? trayIconImage : new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "Loot Tracker");
                icon.setImageAutoSize(true);
                tray.add(icon);
                icon.displayMessage("Loot Tracker", chatMessage, MessageType.INFO);

                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        tray.remove(icon);
                    } catch (Exception ignored) {
                    }
                }).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
