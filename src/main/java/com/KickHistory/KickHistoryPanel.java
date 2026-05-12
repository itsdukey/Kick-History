package com.KickHistory;

import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;

public class KickHistoryPanel extends PluginPanel
{
    private final JTextArea logArea = new JTextArea();
    private final KickHistoryConfig config;

    private static final String HEADER = "Date | Time | Username\n-------------------------\n";

    @Inject
    public KickHistoryPanel(KickHistoryConfig config)
    {
        super();
        this.config = config;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Kick History Log");
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        add(title, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(40, 40, 40));
        logArea.setForeground(Color.WHITE);
        logArea.setMargin(new Insets(5, 5, 5, 5));

        logArea.setText(HEADER);

        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);

        JButton copyBtn = new JButton("Copy All");
        copyBtn.addActionListener(e -> {
            StringSelection stringSelection = new StringSelection(logArea.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
        });

        JButton clearBtn = new JButton("Clear Log");
        clearBtn.addActionListener(e -> logArea.setText(HEADER));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonPanel.add(copyBtn);
        buttonPanel.add(clearBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void addKick(String name)
    {
        LocalDateTime now = LocalDateTime.now();
        String dateStr = formatDate(now, config.dateFormat());
        String timeStr = formatTime(now, config.timeFormat());

        String entry = String.format("%s | %s | %s\n", dateStr, timeStr, name);

        SwingUtilities.invokeLater(() -> {
            logArea.append(entry);

            // OPTIMIZATION: Prevent memory leaks by capping the UI log to 500 lines
            if (logArea.getLineCount() > 500) {
                try {
                    int endOfFirstLine = logArea.getLineEndOffset(0);
                    logArea.replaceRange("", 0, endOfFirstLine);
                } catch (Exception ex) {
                    // Ignore UI threading errors on trim
                }
            }

            logArea.setCaretPosition(logArea.getDocument().getLength());
            revalidate();
            repaint();
        });
    }

    private String formatDate(LocalDateTime date, KickHistoryConfig.DateFormat format)
    {
        if (format == KickHistoryConfig.DateFormat.NUMERIC) {
            return date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } else {
            int day = date.getDayOfMonth();
            String suffix = getDaySuffix(day);
            return date.format(DateTimeFormatter.ofPattern("MMMM d")) + suffix;
        }
    }

    private String formatTime(LocalDateTime time, KickHistoryConfig.TimeFormat format)
    {
        if (format == KickHistoryConfig.TimeFormat.TWENTY_FOUR_HOUR) {
            return time.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else {
            return time.format(DateTimeFormatter.ofPattern("h:mm a"));
        }
    }

    private String getDaySuffix(final int n)
    {
        if (n >= 11 && n <= 13) {
            return "th";
        }
        switch (n % 10) {
            case 1:  return "st";
            case 2:  return "nd";
            case 3:  return "rd";
            default: return "th";
        }
    }
}