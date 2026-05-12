package com.KickHistory;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.*; // Needed for the webhook HTTP requests

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

@Slf4j
@PluginDescriptor(
        name = "Kick History",
        description = "Logs names of players kicked from Friends Chat",
        tags = {"friends", "chat", "kick", "log"}
)
public class KickHistory extends Plugin
{
    @Inject
    private Client client; // Needed to get the local player's name

    @Inject
    private OkHttpClient okHttpClient; // RuneLite's native HTTP client for webhooks

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private KickHistoryConfig config;

    private KickHistoryPanel panel;
    private NavigationButton navButton;

    private static class PendingKick {
        String name;
        long time;

        PendingKick(String name, long time) {
            this.name = name;
            this.time = time;
        }
    }

    private final LinkedList<PendingKick> pendingKicks = new LinkedList<>();

    @Override
    protected void startUp() throws Exception
    {
        panel = injector.getInstance(KickHistoryPanel.class);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Kick History")
                .icon(icon)
                .priority(10)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        pendingKicks.clear();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption());

        if (option.startsWith("Kick user") || option.startsWith("Kick") || option.equalsIgnoreCase("Exclude"))
        {
            String target = Text.removeTags(event.getMenuTarget());
            if (target.contains("(")) {
                target = target.substring(0, target.indexOf("("));
            }

            String cleanName = target.replace('\u00A0', ' ').trim();

            // OPTIMIZATION: Guard Clause. OSRS names cannot be empty or > 12 characters.
            if (cleanName.isEmpty() || cleanName.length() > 12) {
                return;
            }

            pendingKicks.add(new PendingKick(cleanName, System.currentTimeMillis()));
            log.info("Registered kick target: " + cleanName);
        }
    }

    @Subscribe
    public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
    {
        String rawLeftName = Text.removeTags(event.getMember().getName()).replace('\u00A0', ' ').trim();

        // OPTIMIZATION: Use RuneLite's native standardize tool to ensure perfect matching
        String standardLeftName = Text.standardize(rawLeftName);

        Iterator<PendingKick> it = pendingKicks.iterator();
        while (it.hasNext())
        {
            PendingKick pk = it.next();

            if (System.currentTimeMillis() - pk.time > 5000) {
                it.remove();
                continue;
            }

            // Standardize our queued name so we are comparing apples to apples
            if (Text.standardize(pk.name).equals(standardLeftName))
            {
                log.info("Kick confirmed via FC Leave for: " + pk.name);
                it.remove();

                // 1. Update the Sidebar
                SwingUtilities.invokeLater(() -> panel.addKick(pk.name));

                // 2. Fire the Webhook
                sendWebhook(pk.name);

                break;
            }
        }
    }

    /**
     * Sends a POST request to the configured Discord Webhook URL.
     */
    private void sendWebhook(String kickedPlayer)
    {
        if (!config.webhookEnabled() || config.webhookUrl().isEmpty())
        {
            return;
        }

        // Safely get the current player's name
        String adminName = "Unknown";
        if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
            adminName = client.getLocalPlayer().getName();
        }

        // Build a simple JSON payload formatted for Discord
        String jsonPayload = String.format("{\"content\": \"**Kick Logged:** `%s` was kicked from the chat by `%s`\"}",
                kickedPlayer, adminName);

        Request request = new Request.Builder()
                .url(config.webhookUrl())
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonPayload))
                .build();

        // Enqueue sends the request asynchronously so the game client doesn't freeze
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Error sending KickHistory webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close(); // You must always close the response to prevent memory leaks
            }
        });
    }

    @Provides
    KickHistoryConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KickHistoryConfig.class);
    }
}