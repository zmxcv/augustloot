package com.discord.loot;

import java.util.ArrayList;
import java.util.List;

public class DiscordLootSettings {
    public String webhookUrl = "";
    public boolean discordEnabled = true;
    public boolean pmEnabled = true;
    public boolean trayEnabled = true;
    public boolean soundEnabled = true;
    public boolean petsEnabled = true;
    public boolean fortuneEnabled = true;
    public boolean slayerEnabled = true;
    public List<String> priorityDrops = new ArrayList<>();
}