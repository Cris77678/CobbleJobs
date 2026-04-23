package com.tuservidor.cobblejobs.fishing.leaderboard;

import com.google.gson.*;
import com.tuservidor.cobblejobs.CobbleJobs;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LeaderboardManager {

    private static final Path LB_FILE = Paths.get("config", "cobblejobs", "leaderboard.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, LeaderEntry> ENTRIES = new ConcurrentHashMap<>();

    public static void init() {
        load();
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> saveSync());
    }

    public static void update(ServerPlayer player, PlayerFisherData data) {
        String uuidStr = player.getUUID().toString();
        ENTRIES.put(uuidStr, new LeaderEntry(
            player.getName().getString(),
            data.getTotalFishCaught(),
            data.getLevel()
        ));
        saveAll();
    }

    public static void saveAll() {
        try {
            Files.createDirectories(LB_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(LB_FILE)) { GSON.toJson(ENTRIES, w); }
        } catch (Exception e) { CobbleJobs.LOGGER.error("[CobbleJobs] Error al guardar leaderboard", e); }
    }

    private static void saveSync() {
        saveAll();
    }

    private static void load() {
        if (!Files.exists(LB_FILE)) return;
        try (Reader r = Files.newBufferedReader(LB_FILE)) {
            Map<String, LeaderEntry> loaded = GSON.fromJson(r, new com.google.gson.reflect.TypeToken<Map<String, LeaderEntry>>(){}.getType());
            if (loaded != null) ENTRIES.putAll(loaded);
        } catch (Exception e) { CobbleJobs.LOGGER.warn("[CobbleJobs] No se pudo cargar el leaderboard"); }
    }

    public static class LeaderEntry {
        public String playerName; 
        public long totalFish; 
        public int level;
        
        public LeaderEntry(String name, long fish, int lvl) {
            this.playerName = name; 
            this.totalFish = fish; 
            this.level = lvl;
        }
    }
}
