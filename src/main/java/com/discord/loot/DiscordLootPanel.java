package com.discord.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DiscordLootPanel extends PluginPanel {
    private final JTextField webhookField;
    private final DefaultListModel<String> priorityListModel;
    private final DefaultListModel<String> lootListModel;
    private final JCheckBox discordCheckBox;
    private final JCheckBox pmCheckBox;
    private final JCheckBox trayCheckBox;
    private final JCheckBox soundCheckBox;
    private final JCheckBox petCheckBox;
    private final JCheckBox fortuneCheckBox;
    private final JCheckBox slayerCheckBox;
    private final JButton testButton;
    private final JButton addDropButton;
    private final JButton removeDropButton;
    private final JTextField newDropField;

    private final JList<String> lootList;

    private final Path settingsFile =
            Path.of(System.getProperty("user.home"), ".augustrsps", "plugins", "discord_loot_settings.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private DiscordLootSettings settings;

    public DiscordLootPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Webhook Panel
        JPanel webhookPanel = new JPanel(new BorderLayout());
        webhookPanel.setBorder(BorderFactory.createTitledBorder("Discord Webhook"));
        webhookField = new JTextField();
        webhookPanel.add(webhookField, BorderLayout.CENTER);
        webhookPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        add(webhookPanel);
        add(Box.createVerticalStrut(5));

        // Test & Clear Buttons
        JPanel testButtonPanel = new JPanel();
        testButtonPanel.setLayout(new BoxLayout(testButtonPanel, BoxLayout.Y_AXIS));
        testButtonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        testButton = new JButton("Send Test Notification");
        testButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        testButton.addActionListener(e -> sendTestNotification());
        testButtonPanel.add(testButton);
        testButtonPanel.add(Box.createVerticalStrut(5));

        JButton clearNotificationsButton = new JButton("Clear Notifications");
        clearNotificationsButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        clearNotificationsButton.addActionListener(e -> NpcDropDiscordPlugin.clearNotificationQueue());
        testButtonPanel.add(clearNotificationsButton);

        testButtonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        add(testButtonPanel);
        add(Box.createVerticalStrut(10));

        // Options Panel
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Notifications / Settings"));
        discordCheckBox = new JCheckBox("Discord Notification", true);
        pmCheckBox = new JCheckBox("Private Message", true);
        trayCheckBox = new JCheckBox("Tray Notification", true);
        soundCheckBox = new JCheckBox("Play Sound", true);
        petCheckBox = new JCheckBox("Pet Notifications", true);
        fortuneCheckBox = new JCheckBox("Fortune", true);
        slayerCheckBox = new JCheckBox("Ignore Slayer Boxes", true);
        petCheckBox.setToolTipText("Enable notifications when a pet drops.");
        fortuneCheckBox.setToolTipText("If your account has Fortune league perk, enable this for notification of any boxes");
        slayerCheckBox.setToolTipText("This will disable Slayer box drop notifications");

        optionsPanel.add(discordCheckBox);
        optionsPanel.add(pmCheckBox);
        optionsPanel.add(trayCheckBox);
        optionsPanel.add(soundCheckBox);
        optionsPanel.add(petCheckBox);
        optionsPanel.add(fortuneCheckBox);
        optionsPanel.add(slayerCheckBox);
        optionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        add(optionsPanel);
        add(Box.createVerticalStrut(10));

        // Priority Drops Panel
        JPanel priorityPanel = new JPanel(new BorderLayout(5, 5));
        priorityPanel.setBorder(BorderFactory.createTitledBorder("Priority Drops"));
        priorityListModel = new DefaultListModel<>();
        JList<String> priorityList = new JList<>(priorityListModel);
        priorityPanel.add(new JScrollPane(priorityList), BorderLayout.CENTER);

        JPanel editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        newDropField = new JTextField();
        newDropField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        editPanel.add(newDropField);
        editPanel.add(Box.createVerticalStrut(5));

        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        addDropButton = new JButton("Add");
        removeDropButton = new JButton("Remove");
        buttons.add(addDropButton);
        buttons.add(removeDropButton);
        editPanel.add(buttons);
        priorityPanel.add(editPanel, BorderLayout.SOUTH);

        addDropButton.addActionListener(e -> addPriorityDrop());
        removeDropButton.addActionListener(e -> removeSelectedDrop(priorityList));
        priorityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        add(priorityPanel);
        add(Box.createVerticalStrut(10));

        // Loot Feed Panel
        JPanel lootPanel = new JPanel(new BorderLayout());
        lootPanel.setBorder(BorderFactory.createTitledBorder("Loot Feed"));
        lootListModel = new DefaultListModel<>();
        lootList = new JList<>(lootListModel);
        lootPanel.add(new JScrollPane(lootList), BorderLayout.CENTER);
        lootPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        add(lootPanel);
    }

    private void addPriorityDrop() {
        String drop = newDropField.getText().trim();
        if (!drop.isEmpty() && !settings.priorityDrops.contains(drop.toLowerCase())) {
            settings.priorityDrops.add(drop.toLowerCase());
            priorityListModel.addElement(drop);
            newDropField.setText("");
        }
    }

    private void removeSelectedDrop(JList<String> list) {
        int selected = list.getSelectedIndex();
        if (selected != -1) {
            String drop = priorityListModel.get(selected);
            settings.priorityDrops.remove(drop.toLowerCase());
            priorityListModel.remove(selected);
        }
    }

    public void addLootFeed(String itemName, String npcName) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + time + "] " + itemName + " from " + npcName;
        SwingUtilities.invokeLater(() -> {
            lootListModel.addElement(entry);
            lootList.ensureIndexIsVisible(lootListModel.getSize() - 1);
        });
    }

    public void addLootFeedForPet(String gameMessage) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + time + "] " + gameMessage;
        SwingUtilities.invokeLater(() -> {
            lootListModel.addElement(entry);
            lootList.ensureIndexIsVisible(lootListModel.getSize() - 1);
        });
    }

    private void sendTestNotification() {
        if (discordCheckBox.isSelected()) {
            System.out.println("Discord test notification to: " + webhookField.getText());
            NpcDropDiscordPlugin.sendDiscordNotification("Test Item", "TestNpc", "TestPlayer", 1);
        }

        if (pmCheckBox.isSelected())
            System.out.println("PM test notification sent as Loot Tracker");

        if (trayCheckBox.isSelected())
            NpcDropDiscordPlugin.showTrayNotification("Test", 1);

        if (soundCheckBox.isSelected())
            Toolkit.getDefaultToolkit().beep();

        addLootFeed("TestItem", "TestNPC");
    }

    public void saveSettings() {
        updateSettingsFromUI();
        try (Writer writer = Files.newBufferedWriter(settingsFile)) {
            gson.toJson(settings, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadSettings() {
        if (Files.exists(settingsFile)) {
            try (Reader reader = Files.newBufferedReader(settingsFile)) {
                settings = gson.fromJson(reader, DiscordLootSettings.class);
            } catch (IOException e) {
                e.printStackTrace();
                settings = new DiscordLootSettings();
            }
        } else {
            settings = new DiscordLootSettings();
        }
    }

    void applySettingsToUI() {
        webhookField.setText(settings.webhookUrl);
        discordCheckBox.setSelected(settings.discordEnabled);
        pmCheckBox.setSelected(settings.pmEnabled);
        trayCheckBox.setSelected(settings.trayEnabled);
        soundCheckBox.setSelected(settings.soundEnabled);
        petCheckBox.setSelected(settings.petsEnabled);
        fortuneCheckBox.setSelected(settings.fortuneEnabled);
        slayerCheckBox.setSelected(settings.slayerEnabled);

        priorityListModel.clear();
        for (String drop : settings.priorityDrops) {
            priorityListModel.addElement(drop);
        }
    }

    private void updateSettingsFromUI() {
        settings.webhookUrl = webhookField.getText();
        settings.discordEnabled = discordCheckBox.isSelected();
        settings.pmEnabled = pmCheckBox.isSelected();
        settings.trayEnabled = trayCheckBox.isSelected();
        settings.soundEnabled = soundCheckBox.isSelected();
        settings.petsEnabled = petCheckBox.isSelected();
        settings.fortuneEnabled = fortuneCheckBox.isSelected();
        settings.slayerEnabled = slayerCheckBox.isSelected();
        // settings.priorityDrops already updated by add/remove methods
    }

    public void playSound() {
        Toolkit.getDefaultToolkit().beep();
    }

    public String getWebhookUrl() {
        return webhookField.getText();
    }

    public boolean isDiscordEnabled() {
        return discordCheckBox.isSelected();
    }

    public boolean isPmEnabled() {
        return pmCheckBox.isSelected();
    }

    public boolean isTrayEnabled() {
        return trayCheckBox.isSelected();
    }

    public boolean isSoundEnabled() {
        return soundCheckBox.isSelected();
    }

    public boolean isFortuneEnabled() {
        return fortuneCheckBox.isSelected();
    }

    public boolean isPetsEnabled() {
        return petCheckBox.isSelected();
    }

    public boolean isSlayerEnabled() {
        return slayerCheckBox.isSelected();
    }

    public List<String> getPriorityDrops() {
        return List.copyOf(settings.priorityDrops);
    }
}
