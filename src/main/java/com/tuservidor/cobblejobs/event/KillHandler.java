package com.tuservidor.cobblejobs.event;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public class KillHandler {
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof PokemonEntity pokemon)) return;
            if (!(damageSource.getEntity() instanceof ServerPlayer player)) return;

            String speciesId = "cobblemon:" + pokemon.getPokemon().getSpecies().getName().toLowerCase();
            
            // CORRECCIÓN 4: Se añade el parámetro de multiplicador (1.0) y cantidad (1)
            ItemStack loot = FishItem.create(
                speciesId, 
                0.0, 
                0, 
                false, 
                com.tuservidor.cobblejobs.fishing.rarity.FishRarity.COMMON, 
                1.0, 
                1
            );

            ItemEntity itemEntity = new ItemEntity(player.serverLevel(), entity.getX(), entity.getY(), entity.getZ(), loot);
            player.serverLevel().addFreshEntity(itemEntity);
        });
    }
}