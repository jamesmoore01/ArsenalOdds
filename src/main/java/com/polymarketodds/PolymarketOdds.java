package com.polymarketodds;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PolymarketOdds extends JavaPlugin {

    // Arsenal EPL Winner market slug on Polymarket
    private static final String GAMMA_API = "https://gamma-api.polymarket.com/markets?slug=will-arsenal-win-the-2025-26-english-premier-league";
    private static final long INTERVAL_TICKS = 20L * 60 * 5; // 5 minutes

    @Override
    public void onEnable() {
        getLogger().info("PolymarketOdds enabled - fetching Arsenal odds every 5 minutes");

        // Post immediately on startup, then every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                fetchAndPost();
            }
        }.runTaskTimerAsynchronously(this, 20L, INTERVAL_TICKS);
    }

    private void fetchAndPost() {
        try {
            String json = fetchUrl(GAMMA_API);
            double odds = parseOdds(json);

            if (odds < 0) {
                getLogger().warning("Could not parse odds from Polymarket response");
                return;
            }

            int percent = (int) Math.round(odds * 100);
            String bar = buildBar(percent);
            String trend = getTrend(percent);

            // Build the message
            Component message = Component.text()
                .append(Component.text("⚽ ", NamedTextColor.WHITE))
                .append(Component.text("ARSENAL", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" Premier League odds: ", NamedTextColor.GRAY))
                .append(Component.text(percent + "%", oddsColor(percent), TextDecoration.BOLD))
                .append(Component.text(" " + trend, NamedTextColor.GRAY))
                .append(Component.text(" [" + bar + "]", NamedTextColor.DARK_GRAY))
                .append(Component.text(" (Polymarket)", NamedTextColor.DARK_GRAY))
                .build();

            // Broadcast on main thread
            Bukkit.getScheduler().runTask(this, () ->
                Bukkit.broadcast(message)
            );

            getLogger().info("Posted Arsenal odds: " + percent + "%");

        } catch (Exception e) {
            getLogger().warning("Failed to fetch Polymarket odds: " + e.getMessage());
        }
    }

    private String fetchUrl(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        int status = conn.getResponseCode();
        if (status != 200) throw new Exception("HTTP " + status);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }

    private double parseOdds(String json) {
        // Look for "outcomePrices" field which contains [yesPrice, noPrice]
        // e.g. "outcomePrices":"[\"0.51\",\"0.49\"]"
        try {
            int idx = json.indexOf("outcomePrices");
            if (idx == -1) {
                // Try tokens array with price field
                // Look for "outcome":"Yes" and nearby "price"
                idx = json.indexOf("\"Yes\"");
                if (idx == -1) idx = json.indexOf("\"yes\"");
                if (idx == -1) return -1;

                // Find price near this
                int priceIdx = json.indexOf("\"price\"", idx - 50);
                if (priceIdx == -1) priceIdx = json.indexOf("\"price\"", idx);
                if (priceIdx == -1) return -1;

                int colonIdx = json.indexOf(":", priceIdx);
                int commaIdx = json.indexOf(",", colonIdx);
                int braceIdx = json.indexOf("}", colonIdx);
                int end = Math.min(
                    commaIdx == -1 ? Integer.MAX_VALUE : commaIdx,
                    braceIdx == -1 ? Integer.MAX_VALUE : braceIdx
                );
                String priceStr = json.substring(colonIdx + 1, end).trim().replace("\"", "");
                return Double.parseDouble(priceStr);
            }

            // Parse outcomePrices array
            int start = json.indexOf("[", idx);
            int end = json.indexOf("]", start);
            String pricesStr = json.substring(start + 1, end);
            // First value is Yes price
            String[] parts = pricesStr.split(",");
            String firstPrice = parts[0].trim().replace("\"", "").replace("\\", "");
            return Double.parseDouble(firstPrice);

        } catch (Exception e) {
            getLogger().warning("Parse error: " + e.getMessage());
            return -1;
        }
    }

    private String buildBar(int percent) {
        int filled = percent / 10;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }

    private String getTrend(int percent) {
        if (percent >= 70) return "🔥";
        if (percent >= 50) return "📈";
        if (percent >= 30) return "📉";
        return "💀";
    }

    private NamedTextColor oddsColor(int percent) {
        if (percent >= 60) return NamedTextColor.GREEN;
        if (percent >= 40) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }
}
