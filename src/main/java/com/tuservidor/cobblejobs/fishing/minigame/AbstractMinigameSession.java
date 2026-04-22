package com.tuservidor.cobblejobs.fishing.minigame;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class AbstractMinigameSession {
    protected final UUID uuid;
    protected final Consumer<FishingMinigame.MinigameResult> callback;
    protected int ticksOpen = 0;

    public AbstractMinigameSession(UUID uuid, Consumer<FishingMinigame.MinigameResult> callback) {
        this.uuid = uuid;
        this.callback = callback;
    }

    public abstract void start(ServerPlayer player);

    public abstract boolean tick(ServerPlayer player);
}
