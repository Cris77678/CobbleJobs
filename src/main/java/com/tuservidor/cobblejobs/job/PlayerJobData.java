package com.tuservidor.cobblejobs.job;

import com.google.gson.*;
import com.tuservidor.cobblejobs.CobbleJobs;
import lombok.Data;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class PlayerJobData {

    public enum Job { NONE, BUTCHER, FISHER }

    private static final Map<UUID, PlayerJobData> CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UUID playerId;
    private Job activeJob = Job.NONE;

    private PlayerJobData() {}

    public static PlayerJobData get(UUID uuid) {
        return CACHE.computeIfAbsent(uuid, id -> {
            PlayerJobData data = load(id);
            data.playerId = id;
            return data;
        });
    }

    public static void save(UUID uuid) {
        PlayerJobData data = CACHE.get(uuid);
        if (data == null) return;
        
        String jsonSnapshot;
        synchronized (data) {
            jsonSnapshot = GSON.toJson(data);
        }
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Path dir = Paths.get("config", "cobblejobs", "players");
                Files.createDirectories(dir);
                Path file = dir.resolve(uuid + "_job.json");
                try (Writer w = Files.newBufferedWriter(file)) {
                    w.write(jsonSnapshot);
                }
            } catch (Exception e) {
                CobbleJobs.LOGGER.error("[CobbleJobs] Could not save player data for {}", uuid, e);
            }
        });
    }

    private static PlayerJobData load(UUID uuid) {
        Path file = Paths.get("config", "cobblejobs", "players", uuid + "_job.json");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                PlayerJobData d = GSON.fromJson(r, PlayerJobData.class);
                if (d != null) return d;
            } catch (Exception ignored) {}
        }
        return new PlayerJobData();
    }

    public static void evict(UUID uuid) {
        save(uuid);
        CACHE.remove(uuid);
    }
}
