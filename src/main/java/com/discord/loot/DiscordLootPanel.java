package com.discord.loot;

import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DiscordLootPanel extends PluginPanel {
    private final JTextField webhookField;
    private final DefaultListModel<String> priorityListModel;
    private final DefaultListModel<String> lootListModel;
    private final JCheckBox discordCheckBox;
    private final JCheckBox pmCheckBox;
    private final JCheckBox trayCheckBox;
    private final JCheckBox soundCheckBox;
    private final JCheckBox fortuneCheckBox;
    private final JButton testButton;
    private final JButton addDropButton;
    private final JButton removeDropButton;
    private final JTextField newDropField;

    private final List<String> priorityDrops = new ArrayList<>();
    private final JList<String> lootList;

    public DiscordLootPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JPanel webhookPanel = new JPanel(new BorderLayout());
        webhookPanel.setBorder(BorderFactory.createTitledBorder("Discord Webhook"));
        webhookField = new JTextField();
        webhookPanel.add(webhookField, BorderLayout.CENTER);
        webhookPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        add(webhookPanel);
        add(Box.createVerticalStrut(5));
        JPanel testButtonPanel = new JPanel(new BorderLayout());
        testButton = new JButton("Send Test Notification");
        testButton.addActionListener(e -> sendTestNotification());
        testButtonPanel.add(testButton, BorderLayout.WEST); // left align
        testButtonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        add(testButtonPanel);
        add(Box.createVerticalStrut(10));
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Notifications / Settings"));
        discordCheckBox = new JCheckBox("Discord Notification", true);
        pmCheckBox = new JCheckBox("Private Message", true);
        trayCheckBox = new JCheckBox("Tray Notification", true);
        soundCheckBox = new JCheckBox("Play Sound", true);
        fortuneCheckBox = new JCheckBox("Fortune", true);
        fortuneCheckBox.setToolTipText("If your account has Fortune league perk, enable this for notification of any boxes");
        optionsPanel.add(discordCheckBox);
        optionsPanel.add(pmCheckBox);
        optionsPanel.add(trayCheckBox);
        optionsPanel.add(soundCheckBox);
        optionsPanel.add(fortuneCheckBox);
        optionsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        add(optionsPanel);
        add(Box.createVerticalStrut(10));
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
        if (!drop.isEmpty() && !priorityDrops.contains(drop.toLowerCase())) {
            priorityDrops.add(drop.toLowerCase());
            priorityListModel.addElement(drop);
            newDropField.setText("");
        }
    }

    private void removeSelectedDrop(JList<String> list) {
        int selected = list.getSelectedIndex();
        if (selected != -1) {
            String drop = priorityListModel.get(selected);
            priorityDrops.remove(drop.toLowerCase());
            priorityListModel.remove(selected);
        }
    }

    public void addLootFeed(String itemName, String npcName) {
        String entry = itemName + " from " + npcName;
        SwingUtilities.invokeLater(() -> {
            lootListModel.addElement(entry);
            int lastIndex = lootListModel.getSize() - 1;
            if (lastIndex >= 0)
                lootList.ensureIndexIsVisible(lastIndex);
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

    public List<String> getPriorityDrops() {
        return new ArrayList<>(priorityDrops);
    }
}
