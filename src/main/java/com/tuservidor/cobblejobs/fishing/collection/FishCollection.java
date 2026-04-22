package com.tuservidor.cobblejobs.fishing.collection;

import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

public class FishCollection {

    public static void checkMilestones(ServerPlayer player, PlayerFisherData data) {
        List<FisherConfig.CollectionMilestone> milestones =
            FisherConfig.get().getCollectionMilestones();

        long total = data.getTotalFishCaught();
        int  unique = data.getFishCollection().size();

        for (FisherConfig.CollectionMilestone m : milestones) {
            String key = m.getId();
            if (data.getFishCollection().containsKey("_milestone_" + key)) continue;

            boolean reached = switch (m.getType()) {
                case "total_fish"   -> total  >= m.getRequired();
                case "unique_fish"  -> unique >= m.getRequired();
                case "rare_fish"    -> data.getRareCaught()      >= m.getRequired();
                case "epic_fish"    -> data.getEpicCaught()      >= m.getRequired();
                case "legendary"    -> data.getLegendaryCaught() >= m.getRequired();
                default             -> false;
            };

            if (reached) {
                // CORRECCIÓN 3: Si tiene recompensa económica pero la economía está fallando, 
                // PAUSAMOS el hito para no robarle su dinero al jugador.
                if (m.getMoneyReward() > 0) {
                    if (!EconomyBridge.isAvailable()) {
                        player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Completaste un hito, pero la economía está fuera de línea. Sigue pescando cuando regrese para cobrar tu recompensa."));
                        continue; 
                    }
                    EconomyBridge.pay(player, m.getMoneyReward());
                    data.addMoney(m.getMoneyReward());
                }

                // Sellar el hito SOLAMENTE si la transacción económica ya fue exitosa
                data.getFishCollection().put("_milestone_" + key, 1L);
                
                player.sendSystemMessage(MessageUtil.literal(
                    "\n§6§l╔══ 🏆 COLECCIÓN ══╗\n" +
                    "§e§l  " + m.getTitle() + "\n" +
                    "§7  " + m.getDescription() + "\n" +
                    (m.getMoneyReward() > 0
                        ? "§a  Recompensa: §f$" + String.format("%.0f", m.getMoneyReward()) + "\n"
                        : "") +
                    "§6§l╚═════════════════╝"
                ));
            }
        }
    }

    public static void showCollection(ServerPlayer player, PlayerFisherData data) {
        Map<String, Long> col = data.getFishCollection();
        int unique = (int) col.keySet().stream()
            .filter(k -> !k.startsWith("_milestone_")).count();

        player.sendSystemMessage(MessageUtil.literal(
            "§6§l══ 📖 Pokédex de Peces ══\n" +
            "§7Peces únicos: §f" + unique + "\n" +
            "§7Total capturados: §f" + data.getTotalFishCaught() + "\n" +
            "§7Comunes: §7" + data.getCommonCaught() +
            " §7| Raros: §b" + data.getRareCaught() +
            " §7| Épicos: §d" + data.getEpicCaught() +
            " §7| Legendarios: §6" + data.getLegendaryCaught() + "\n" +
            "§7Récord peso: §f" + String.format("%.2f", data.getRecordWeight()) + " kg" +
            (data.getRecordWeightSpecies().isEmpty() ? ""
                : " §8(" + capitalize(data.getRecordWeightSpecies().replace("cobblemon:","")) + ")") + "\n" +
            "§7Dinero ganado: §a$" + String.format("%.2f", data.getTotalMoneyEarned()) + "\n" +
            "§6§l════════════════════════"
        ));

        if (unique > 0) {
            player.sendSystemMessage(MessageUtil.literal("§7Especies capturadas:"));
            col.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("_milestone_"))
                .forEach(e -> {
                    String name = capitalize(e.getKey().replace("cobblemon:", ""));
                    double rec = data.getFishRecordWeight().getOrDefault(e.getKey(), 0.0);
                    player.sendSystemMessage(MessageUtil.literal(
                        "§8• §b" + name + " §7×" + e.getValue() +
                        " §8(récord: §f" + String.format("%.2f", rec) + " kg§8)"
                    ));
                });
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
