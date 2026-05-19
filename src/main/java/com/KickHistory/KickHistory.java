package com.KickHistory;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

@Slf4j
@PluginDescriptor(
        name = "Kick History",
        description = "Logs names of players kicked from specific Friends Chats",
        tags = {"friends", "chat", "kick", "log"}
)
public class KickHistory extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private KickHistoryConfig config;

    private KickHistoryPanel panel;
    private NavigationButton navButton;

    private static class PendingKick {
        String displayName; // The original cased name for Discord/UI
        String standardName; // The lowercase name for matching
        long time;
        int world;

        PendingKick(String displayName, String standardName, long time, int world) {
            this.displayName = displayName;
            this.standardName = standardName;
            this.time = time;
            this.world = world;
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

        if (config.showSidePanel())
        {
            clientToolbar.addNavigation(navButton);
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        pendingKicks.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!event.getGroup().equals("kickhistory"))
        {
            return;
        }

        if (event.getKey().equals("showSidePanel"))
        {
            if (config.showSidePanel())
            {
                clientToolbar.addNavigation(navButton);
            }
            else
            {
                clientToolbar.removeNavigation(navButton);
            }
        }
    }

    private boolean isInTargetFC()
    {
        String targetFcConfig = config.targetFcName();
        if (targetFcConfig == null || targetFcConfig.trim().isEmpty()) {
            return false;
        }

        FriendsChatManager fcManager = client.getFriendsChatManager();
        if (fcManager == null || fcManager.getOwner() == null) {
            return false;
        }

        String currentOwner = Text.standardize(fcManager.getOwner());
        String[] approvedOwners = targetFcConfig.split(",");

        for (String approvedOwner : approvedOwners) {
            if (Text.standardize(approvedOwner.trim()).equals(currentOwner)) {
                return true;
            }
        }

        return false;
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option = Text.removeTags(event.getMenuOption());
        String target = Text.removeTags(event.getMenuTarget());

        if (option.startsWith("Kick user") || option.startsWith("Kick") || option.equalsIgnoreCase("Exclude"))
        {
            if (!isInTargetFC()) {
                return;
            }

            if (target.contains("(")) {
                target = target.substring(0, target.indexOf("("));
            }

            // Keep the original capitalization but strip the spaces at the ends
            String displayName = target.trim();

            // Create the lowercase version specifically for logic matching
            String standardName = Text.standardize(displayName);

            // Remember that spaces and hyphens count towards the 12 character limit
            if (displayName.isEmpty() || displayName.length() > 12) {
                return;
            }

            int world = getPlayerWorld(standardName);

            pendingKicks.add(new PendingKick(displayName, standardName, System.currentTimeMillis(), world));
            log.info("Registered FC kick target: " + displayName + " on world " + world);
        }
        else if (option.equalsIgnoreCase("Confirm kick"))
        {
            if (!pendingKicks.isEmpty() && isInTargetFC())
            {
                PendingKick lastKick = pendingKicks.getLast();
                lastKick.time = System.currentTimeMillis();
                log.info("Kick confirmed in dialog. Refreshing timer for: " + lastKick.displayName);
            }
        }
    }

    private int getPlayerWorld(String standardTargetName)
    {
        FriendsChatManager fcManager = client.getFriendsChatManager();
        if (fcManager != null) {
            for (FriendsChatMember member : fcManager.getMembers()) {
                if (Text.standardize(member.getName()).equals(standardTargetName)) {
                    return member.getWorld();
                }
            }
        }
        return -1;
    }

    @Subscribe
    public void onFriendsChatChanged(FriendsChatChanged event)
    {
        // Instantly clear the pending queue if the admin joins or leaves an FC
        if (!pendingKicks.isEmpty())
        {
            log.info("FC state changed. Clearing {} stale kick(s) from memory.", pendingKicks.size());
            pendingKicks.clear();
        }
    }

    @Subscribe
    public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
    {
        if (!isInTargetFC()) {
            return;
        }

        String standardLeftName = Text.standardize(event.getMember().getName());

        Iterator<PendingKick> it = pendingKicks.iterator();
        while (it.hasNext())
        {
            PendingKick pk = it.next();

            if (System.currentTimeMillis() - pk.time > 30000) {
                it.remove();
                continue;
            }

            // Compare using the standardized names
            if (pk.standardName.equals(standardLeftName))
            {
                log.info("Kick confirmed via FC Leave for: " + pk.displayName);
                it.remove();

                // Pass the displayName to the UI and Discord so the capitals remain
                SwingUtilities.invokeLater(() -> panel.addKick(pk.displayName));
                sendWebhook(pk.displayName, pk.world);

                break;
            }
        }
    }

    private void sendWebhook(String displayPlayer, int world)
    {
        if (!config.webhookEnabled() || config.webhookUrl().isEmpty())
        {
            return;
        }

        String adminName = "Unknown";
        if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
            adminName = client.getLocalPlayer().getName();
        }

        // Grab the active Friends Chat name
        String fcOwner = "Unknown FC";
        FriendsChatManager fcManager = client.getFriendsChatManager();
        if (fcManager != null && fcManager.getOwner() != null) {
            fcOwner = Text.removeTags(fcManager.getOwner());
        }

        String worldText = world > 0 ? " (W" + world + ")" : "";

        // Added the fcOwner to the Discord output string
        String jsonPayload = String.format("{\"content\": \"**Kick Logged:** `%s`%s was kicked from `%s` by `%s`\"}",
                displayPlayer, worldText, fcOwner, adminName);

        Request request = new Request.Builder()
                .url(config.webhookUrl())
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonPayload))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Error sending KickHistory webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
            }
        });
    }

    @Provides
    KickHistoryConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KickHistoryConfig.class);
    }
}