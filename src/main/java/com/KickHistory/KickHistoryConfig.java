package com.KickHistory;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("kickhistory")
public interface KickHistoryConfig extends Config
{
    enum TimeFormat {
        TWELVE_HOUR,
        TWENTY_FOUR_HOUR
    }

    enum DateFormat {
        NUMERIC,
        NAMED
    }

    @ConfigItem(
            keyName = "timeFormat",
            name = "Time Format",
            description = "Choose between 12-hour (7:12 PM) and 24-hour (19:12) formats.",
            position = 1
    )
    default TimeFormat timeFormat()
    {
        return TimeFormat.TWELVE_HOUR;
    }

    @ConfigItem(
            keyName = "dateFormat",
            name = "Date Format",
            description = "Choose between numeric (05/11/2026) or named (May 11th).",
            position = 2
    )
    default DateFormat dateFormat()
    {
        return DateFormat.NAMED;
    }

    @ConfigItem(
            keyName = "showSidePanel",
            name = "Show Side Panel Icon",
            description = "Toggle the side panel icon on or off. Note: Requires a plugin restart or toggle to update.",
            position = 3
    )
    default boolean showSidePanel()
    {
        return true;
    }

    @ConfigItem(
            keyName = "targetFcName",
            name = "Target FC Name",
            description = "The owner name(s) of the Friends Chat to monitor, separated by commas (e.g., 'zealgains, altaccount').",
            position = 4
    )
    default String targetFcName()
    {
        return "";
    }

    @ConfigItem(
            keyName = "webhookEnabled",
            name = "Enable Webhook",
            description = "Toggle sending Friends Chat kick logs to Discord.",
            position = 5
    )
    default boolean webhookEnabled()
    {
        return false;
    }

    @ConfigItem(
            keyName = "webhookUrl",
            name = "Webhook URL",
            description = "The Discord Webhook URL for the kick logs.",
            position = 6,
            secret = true
    )
    default String webhookUrl()
    {
        return "";
    }

    // =========================================================
    // SECTION: CLEAN NATIVE SETUP GUIDE
    // =========================================================
    @ConfigSection(
            name = "📋 Plugin Setup Guide",
            description = "Click to expand or collapse the setup instructions",
            position = 7
    )
    String setupSection = "setupSection";

    @ConfigItem(
            keyName = "displayGuideText",
            name = "<html><body width='175'>"
                    + "<font color='#A0A0A0' face='sans-serif' size='3'>"
                    + "1. Open a Discord text channel.<br>"
                    + "2. Edit Channel > Integrations > Webhooks.<br>"
                    + "3. Create a New Webhook, copy the URL, and paste it into the box above.<br>"
                    + "4. Enter the exact account name of the FC Owner (e.g. 'zealgains') into the Target FC Name box.<br>"
                    + "<hr color='#2A2A2A'>"
                    + "<i>Note:<br>The plugin will ignore all clicks if you are not currently inside the FC owned by the name(s) listed in the Target box.</i><br><br>"
                    + "<i>You can track multiple chats by separating names with a comma (e.g. 'zealgains, altaccount').</i><br><br>"
                    + "<i>This plugin will not track kicks done by others in FC only your own</i><br><br>"
                    + "</font></body></html>",
            description = "Step-by-step configuration verification checklist",
            position = 8,
            section = setupSection
    )
    default boolean displayGuideText() { return false; }
}