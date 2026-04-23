package com.tuservidor.cobblejobs.fishing.minigame;

import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

import java.util.UUID;
import java.util.function.Consumer;

public class ActionCombinedMinigame extends AbstractMinigameSession {

    private enum SubState { WAIT_CLICK, CLICK_NOW, TENSION }

    private final FishRarity rarity;
    private final int maxPhases;
    private int currentPhase = 0;

    private SubState state = SubState.WAIT_CLICK;

    // React params (Click Izquierdo)
    private int waitTicks = 0;
    private int targetWait = 0;
    private int clickTicks = 0;
    private final int reactionTime;

    // Tension params (Shift)
    private ServerBossEvent bossBar;
    private double tension = 0.3;
    private int ticksInGreen = 0;
    private final int targetGreen;
    private final double escapeSpeed;

    private final java.util.Random RNG = new java.util.Random();

    public ActionCombinedMinigame(UUID uuid, FishRarity rarity, int level, Consumer<FishingMinigame.MinigameResult> callback) {
        super(uuid, callback);
        this.rarity = rarity;
        
        // Común = 1 vez, Raro = 2 veces, Épico = 3 veces, Legendario = 5 veces
        this.maxPhases = rarity == FishRarity.LEGENDARY ? 5 : rarity.ordinal() + 1;
        
        this.reactionTime = 40 - (rarity.ordinal() * 5);
        this.targetGreen = 30 + (rarity.ordinal() * 10);
        this.escapeSpeed = 0.010 + (rarity.ordinal() * 0.003);
    }

    @Override
    public void start(ServerPlayer player) {
        player.closeContainer();
        startWaitPhase(player);
    }

    private void startWaitPhase(ServerPlayer player) {
        this.state = SubState.WAIT_CLICK;
        this.waitTicks = 0;
        this.targetWait = 25 + RNG.nextInt(35); // Tiempo aleatorio hasta que salga "AHORA"
        
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(MessageUtil.literal("§e¡Atento!")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(MessageUtil.literal("§7Fase " + (currentPhase + 1) + "/" + maxPhases + " — Da CLIC IZQ cuando diga §cAHORA")));
        player.playNotifySound(SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private void startTensionPhase(ServerPlayer player) {
        this.state = SubState.TENSION;
        this.tension = 0.3;
        this.ticksInGreen = 0;
        
        if (this.bossBar == null) {
            this.bossBar = new ServerBossEvent(
                MessageUtil.literal("§e🎣 ¡Mantén la tensión! (Presiona SHIFT)"),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS
            );
        }
        this.bossBar.addPlayer(player);
        
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(MessageUtil.literal("§b¡Tira de la caña!")));
        player.connection.send(new ClientboundSetSubtitleTextPacket(MessageUtil.literal("§7Da toques de §e§lSHIFT §7para mantener la barra en verde")));
    }

    @Override
    public boolean tick(ServerPlayer player) {
        if (player.hasDisconnected() || player.isRemoved() || !player.isAlive()) {
            fail(player, FishingMinigame.MinigameResult.FAIL);
            return false;
        }

        ticksOpen++;

        switch (state) {
            case WAIT_CLICK -> {
                waitTicks++;
                if (waitTicks > 15 && player.swinging) { // Si hace clic antes de tiempo (hace trampa)
                    player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                    player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡Te adelantaste! Espantaste al pez."));
                    fail(player, FishingMinigame.MinigameResult.FAIL);
                    return false;
                }
                if (waitTicks >= targetWait) {
                    state = SubState.CLICK_NOW;
                    clickTicks = 0;
                    player.connection.send(new ClientboundSetTitlesAnimationPacket(2, reactionTime, 5));
                    player.connection.send(new ClientboundSetTitleTextPacket(MessageUtil.literal("§c§l¡AHORA!")));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(MessageUtil.literal("§f¡Da CLIC IZQUIERDO!")));
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
            case CLICK_NOW -> {
                clickTicks++;
                if (player.swinging) {
                    clearTitle(player);
                    player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 2.0f);
                    startTensionPhase(player);
                } else if (clickTicks > reactionTime) {
                    player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                    player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡Fuiste muy lento en la fase " + (currentPhase+1) + " y escapó!"));
                    fail(player, FishingMinigame.MinigameResult.FAIL);
                    return false;
                }
            }
            case TENSION -> {
                if (player.isCrouching()) tension += 0.03;
                else tension -= escapeSpeed;
                
                tension = Math.max(0.0, Math.min(1.0, tension));
                boolean inGreenZone = tension >= 0.40 && tension <= 0.75;

                if (inGreenZone) {
                    bossBar.setColor(BossEvent.BossBarColor.GREEN);
                    bossBar.setName(MessageUtil.literal("§a🎣 ¡Perfecto! Mantén ahí..."));
                    ticksInGreen++;
                    if (ticksInGreen % 10 == 0) player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 2.0f);
                } else if (tension > 0.85 || tension < 0.15) {
                    bossBar.setColor(BossEvent.BossBarColor.RED);
                    bossBar.setName(MessageUtil.literal("§c🎣 ¡Peligro! ¡Ajusta con SHIFT!"));
                } else {
                    bossBar.setColor(BossEvent.BossBarColor.YELLOW);
                    bossBar.setName(MessageUtil.literal("§e🎣 ¡Sigue así!"));
                }

                bossBar.setProgress((float) tension);

                if (ticksInGreen >= targetGreen) {
                    bossBar.removePlayer(player);
                    currentPhase++;
                    if (currentPhase >= maxPhases) {
                        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 2.0f);
                        callback.accept(FishingMinigame.MinigameResult.SUCCESS);
                        return false;
                    } else {
                        startWaitPhase(player); // Siguiente fase (repite el clic y el shift)
                    }
                } else if (tension >= 1.0 || tension <= 0.0) {
                    player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡El pez rompió el sedal en la fase " + (currentPhase+1) + "!"));
                    fail(player, FishingMinigame.MinigameResult.FAIL);
                    return false;
                }
            }
        }

        // Seguridad por si el jugador se queda AFK con el minijuego abierto
        if (ticksOpen > 1500) {
            fail(player, FishingMinigame.MinigameResult.FAIL);
            return false;
        }

        return true;
    }

    private void fail(ServerPlayer player, FishingMinigame.MinigameResult result) {
        clearTitle(player);
        if (bossBar != null) bossBar.removePlayer(player);
        callback.accept(result);
    }

    private void clearTitle(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 0, 0));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
    }
}
