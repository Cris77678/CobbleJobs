package com.tuservidor.cobblejobs.gui;

import com.tuservidor.cobblejobs.economy.EconomyBridge;
import com.tuservidor.cobblejobs.fishing.leaderboard.LeaderboardManager;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SellGui {

    public static void open(ServerPlayer player) {
        if (!EconomyBridge.isAvailable()) {
            player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] La economía no está disponible."));
            return;
        }

        List<Integer> fishSlots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            if (FishItem.read(player.getInventory().getItem(i)) != null) fishSlots.add(i);
        }

        if (fishSlots.isEmpty()) {
            player.sendSystemMessage(MessageUtil.literal("§c[Pescador] No tienes peces para vender."));
            return;
        }

        double totalRaw = 0;
        for (int slot : fishSlots) {
            ItemStack stack = player.getInventory().getItem(slot);
            FishItem.FishData d = FishItem.read(stack);
            if (d == null) continue;
            totalRaw += FishItem.calculatePrice(d) * stack.getCount();
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }

        // CORRECCIÓN 2: Redondeo matemático a 2 decimales para evitar errores de punto flotante
        double totalFinal = Math.round(totalRaw * 100.0) / 100.0;

        EconomyBridge.pay(player, totalFinal);

        PlayerFisherData data = PlayerFisherData.get(player.getUUID());
        data.addMoney(totalFinal);
        LeaderboardManager.update(player, data);
        PlayerFisherData.save(player.getUUID());

        player.sendSystemMessage(MessageUtil.literal("§a§lVenta exitosa: §f$" + String.format("%.2f", totalFinal)));
    }
}
