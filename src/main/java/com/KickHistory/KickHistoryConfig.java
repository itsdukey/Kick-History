package com.KickHistory;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
            keyName = "webhookEnabled",
            name = "Enable Discord Webhook",
            description = "Toggle sending kick logs to a Discord webhook.",
            position = 3
    )
    default boolean webhookEnabled()
    {
        return false;
    }

    @ConfigItem(
            keyName = "webhookUrl",
            name = "Webhook URL",
            description = "The Discord Webhook URL to send messages to.",
            position = 4,
            secret = true
    )
    default String webhookUrl()
    {
        return "";
    }
}