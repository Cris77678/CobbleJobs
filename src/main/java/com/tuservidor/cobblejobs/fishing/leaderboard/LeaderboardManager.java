package com.tuservidor.cobblejobs.fishing.leaderboard;

import com.google.gson.*;
import com.tuservidor.cobblejobs.CobbleJobs;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LeaderboardManager {

    private static final int DISPLAY_TOP = 10;
    private static final Path LB_FILE = Paths.get("config", "cobblejobs", "leaderboard.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, LeaderEntry> ENTRIES = new ConcurrentHashMap<>();

    public static void init() {
        load();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(srv -> {
            if (srv.getTickCount() % 6000 == 0) saveAll();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> saveSync());
    }

    public static void update(ServerPlayer player, PlayerFisherData data) {
        String uuidStr = player.getUUID().toString();
        ENTRIES.put(uuidStr, new LeaderEntry(
            player.getName().getString(),
            data.getTotalFishCaught(),
            data.getRecordWeight(),
            data.getRecordWeightSpecies(),
            data.getTotalMoneyEarned()
        ));
    }

    public static void show(ServerPlayer player, String category) {
        List<Map.Entry<String, LeaderEntry>> sorted = switch (category.toLowerCase()) {
            case "fish", "peces" -> ENTRIES.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().totalFish, a.getValue().totalFish))
                .limit(DISPLAY_TOP).collect(Collectors.toList());
            case "peso", "weight" -> ENTRIES.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().recordWeight, a.getValue().recordWeight))
                .limit(DISPLAY_TOP).collect(Collectors.toList());
            case "dinero", "money" -> ENTRIES.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().totalMoney, a.getValue().totalMoney))
                .limit(DISPLAY_TOP).collect(Collectors.toList());
            default -> {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Categorías: peces, peso, dinero"));
                yield Collections.emptyList();
            }
        };

        if (sorted.isEmpty()) {
            player.sendSystemMessage(MessageUtil.literal("§7[CobbleJobs] Aún no hay datos."));
            return;
        }

        player.sendSystemMessage(MessageUtil.literal("§6§l══ 🏆 TOP " + DISPLAY_TOP + " — " + category.toUpperCase() + " ══"));
        int rank = 1;
        for (var e : sorted) {
            String uuidStr = e.getKey();
            LeaderEntry le = e.getValue();
            
            // CORRECCIÓN 3: Si el jugador está conectado, usar su nombre actual.
            // Esto evita mostrar nombres antiguos si el jugador cambió de Nickname.
            String displayName = le.playerName;
            ServerPlayer onlinePlayer = player.getServer().getPlayerList().getPlayer(UUID.fromString(uuidStr));
            if (onlinePlayer != null) {
                displayName = onlinePlayer.getName().getString();
                le.playerName = displayName; // Actualizar caché interna
            }

            String medal = switch (rank) { case 1 -> "§6§l🥇"; case 2 -> "§7§l🥈"; case 3 -> "§c§l🥉"; default -> "§7 " + rank + "."; };
            String value = switch (category.toLowerCase()) {
                case "peso", "weight" -> String.format("%.2f kg", le.recordWeight) + (le.recordSpecies.isEmpty() ? "" : " §8(" + MessageUtil.capitalize(le.recordSpecies.replace("cobblemon:", "")) + ")");
                case "dinero", "money" -> "$" + String.format("%.0f", le.totalMoney);
                default -> le.totalFish + " peces";
            };
            player.sendSystemMessage(MessageUtil.literal(medal + " §f" + displayName + " §7— §e" + value));
            rank++;
        }
        player.sendSystemMessage(MessageUtil.literal("§6§l═══════════════════════"));
    }

    public static void saveAll() {
        Map<String, LeaderEntry> snapshot = new HashMap<>(ENTRIES);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(LB_FILE.getParent());
                try (Writer w = Files.newBufferedWriter(LB_FILE)) { GSON.toJson(snapshot, w); }
            } catch (Exception e) { CobbleJobs.LOGGER.error("[CobbleJobs] Error guardando rankings", e); }
        });
    }

    private static void saveSync() {
        try {
            Files.createDirectories(LB_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(LB_FILE)) { GSON.toJson(ENTRIES, w); }
        } catch (Exception e) { CobbleJobs.LOGGER.error("[CobbleJobs] Error guardando rankings", e); }
    }

    private static void load() {
        if (!Files.exists(LB_FILE)) return;
        try (Reader r = Files.newBufferedReader(LB_FILE)) {
            Map<String, LeaderEntry> loaded = GSON.fromJson(r, new com.google.gson.reflect.TypeToken<Map<String, LeaderEntry>>(){}.getType());
            if (loaded != null) ENTRIES.putAll(loaded);
        } catch (Exception e) { CobbleJobs.LOGGER.warn("[CobbleJobs] No se pudo cargar el leaderboard"); }
    }

    public static class LeaderEntry {
        public String playerName; public long totalFish; public double recordWeight; public String recordSpecies; public double totalMoney;
        public LeaderEntry() {}
        public LeaderEntry(String name, long fish, double rw, String rs, double money) {
            this.playerName = name; this.totalFish = fish; this.recordWeight = rw; this.recordSpecies = rs; this.totalMoney = money;
        }
    }
}
