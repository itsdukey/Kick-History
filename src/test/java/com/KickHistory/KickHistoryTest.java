package com.KickHistory;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class KickHistoryTest
{
    public static void main(String[] args) throws Exception
    {
        // This line tells RuneLite to load your plugin specifically
        ExternalPluginManager.loadBuiltin(KickHistory.class);
        RuneLite.main(args);
    }
}