package com.snowcity.maid_construction_team.network.handler.labor;

import com.snowcity.maid_construction_team.api.labor.ILaborProvider;
import com.snowcity.maid_construction_team.api.labor.LaborProviderRegistry;
import com.snowcity.maid_construction_team.api.contract.ContractBonusManager;
import com.snowcity.maid_construction_team.network.payload.labor.RecallLaborPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端处理器：处理召回/取消激活请求。
 * 根据 providerId 分流：
 *   - "servant_contract" → 取消契约加成
 *   - 其他 → 召回劳动力（宠物等）
 */
public class RecallLaborServerHandler {

    public static void handle(final RecallLaborPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();

            if ("servant_contract".equals(payload.providerId())) {
                // 契约加成取消
                ContractBonusManager.deactivate(payload.laborId(), player);

                playSound(player, "ui.button.click", 1.0f, 1.0f);
                playSound(player, "entity.player.levelup", 0.5f, 1.2f);

            } else {
                // 劳动力召回
                ILaborProvider provider = LaborProviderRegistry.getProvider(payload.providerId()).orElse(null);
                if (provider != null) {
                    provider.recall(payload.laborId(), player);
                }
            }
        });
    }

    private static void playSound(Player player, String soundId, float volume, float pitch) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace(soundId));
        if (sound != null) {
            player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
        }
    }
}