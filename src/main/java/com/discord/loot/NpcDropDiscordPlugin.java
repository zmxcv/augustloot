package com.discord.loot;

import com.google.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    private static final BlockingQueue<String> notifQueue = new LinkedBlockingQueue<>();
    private static volatile boolean workerRunning = false;

    private static final Set<String> PET_MESSAGES = Set.of(
            "You have a funny feeling like you're being followed",
            "You feel something weird sneaking into your backpack",
            "You have a funny feeling like you would have been followed"
    );

    @Override
    protected void startUp() throws Exception {
        panel = new DiscordLootPanel();

        panel.loadSettings();       // Explicitly load settings from JSON
        panel.applySettingsToUI();  // Apply loaded settings to checkboxes, webhook, and priority list

        setupNavigationButton("/icon.png");
        System.out.println("Discord Loot Notifier started!");
    }

    @Override
    protected void shutDown() throws Exception {
        if (navButton != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
        }
        panel.saveSettings();
        System.out.println("Discord Loot Notifier stopped!");
    }

    @Subscribe
    public void onClientShutdown(ClientShutdown e) {
        panel.saveSettings();
        System.out.println("Settings saved on client shutdown");
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
                if (panel.isSoundEnabled()) panel.playSound();
                panel.addLootFeedForPet(chatMessage);
            });
        }
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        String npcName = event.getName();
        String playerName = client.getLocalPlayer().getName();
        event.getItems().forEach(itemStack -> {
            String itemName = client.getItemDefinition(itemStack.getId()).getName();
            int quantity = itemStack.getQuantity();
            boolean isPriority = panel.getPriorityDrops().contains(itemName.toLowerCase());

            if (!isPriority && panel.isFortuneEnabled()) {
                String lower = itemName.toLowerCase();
                if (!(panel.isSlayerEnabled() && lower.contains("slayer") && lower.contains("box"))) {
                    if (lower.contains("box") || lower.contains("cache") || lower.contains("crate") ||
                            lower.contains("pack") || lower.contains("present")) {
                        isPriority = true;
                    }
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
        notifQueue.offer(quantity + "x " + itemName);
        startWorker();
    }

    private static synchronized void startWorker() {
        if (workerRunning) return;
        workerRunning = true;

        new Thread(() -> {
            try {
                while (!notifQueue.isEmpty()) {
                    String message = notifQueue.take();

                    SwingUtilities.invokeAndWait(() -> {
                        if (!SystemTray.isSupported()) return;
                        try {
                            SystemTray tray = SystemTray.getSystemTray();
                            TrayIcon icon = new TrayIcon(
                                    trayIconImage != null ? trayIconImage :
                                            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
                                    "Loot Tracker"
                            );
                            icon.setImageAutoSize(true);
                            tray.add(icon);
                            icon.displayMessage("Loot Tracker", "You received " + message + "!", TrayIcon.MessageType.INFO);

                            new Thread(() -> {
                                try {
                                    Thread.sleep(3000);
                                    tray.remove(icon);
                                } catch (Exception ignored) {}
                            }).start();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    Thread.sleep(3500); // wait for this one to finish before next
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                workerRunning = false;
            }
        }, "TrayNotificationWorker").start();
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

    private void setupNavigationButton(String resourcePath)
    {
        BufferedImage icon;
        try
        {
            icon = ImageIO.read(getClass().getResourceAsStream(resourcePath));
            if (icon == null)
            {
                System.err.println("Tray icon resource not found, using placeholder.");
                icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }

        try
        {
            navButton = NavigationButton.builder()
                    .tooltip("Discord Loot Notifier")
                    .icon(icon)
                    .priority(1)
                    .panel(panel)
                    .build();

            clientToolbar.addNavigation(navButton);
        }
        catch (Exception e)
        {
            System.err.println("Failed to add navigation button, plugin will still load.");
            e.printStackTrace();
        }
    }

}
