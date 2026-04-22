package com.tuservidor.cobblejobs.fishing.minigame;

import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Consumer;

public class FishingMinigame {

    public enum MinigameResult { SUCCESS, PARTIAL, FAIL }

    private static final Map<UUID, AbstractMinigameSession> SESSIONS = new HashMap<>();

    public static void openRandomMinigame(ServerPlayer player, FishRarity rarity, int level, Consumer<MinigameResult> callback) {
        UUID uuid = player.getUUID();
        if (SESSIONS.containsKey(uuid)) return;

        AbstractMinigameSession session;
        
        // 50/50 de probabilidad entre el de tensión y el de reacción
        if (new Random().nextBoolean()) {
            session = new ActionTensionMinigame(uuid, rarity, level, callback);
        } else {
            session = new ActionReactMinigame(uuid, rarity, level, callback);
        }

        SESSIONS.put(uuid, session);
        session.start(player); 
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        List<UUID> toRemove = new ArrayList<>();

        for (var entry : SESSIONS.entrySet()) {
            UUID uuid = entry.getKey();
            AbstractMinigameSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);

            if (player == null) {
                toRemove.add(uuid);
                continue;
            }

            boolean keepRunning = session.tick(player);
            if (!keepRunning) {
                toRemove.add(uuid);
            }
        }

        toRemove.forEach(SESSIONS::remove);
    }

    public static boolean hasSession(UUID uuid) {
        return SESSIONS.containsKey(uuid);
    }

    public static void cancelSession(UUID uuid) {
        AbstractMinigameSession s = SESSIONS.remove(uuid);
        if (s != null) {
            s.callback.accept(MinigameResult.FAIL); 
        }
    }
}
