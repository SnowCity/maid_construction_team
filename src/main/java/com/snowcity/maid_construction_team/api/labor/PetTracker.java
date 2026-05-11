package com.snowcity.maid_construction_team.api.labor;

import com.snowcity.maid_construction_team.core.init.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

/**
 * 宠物追踪器，维护玩家的宠物 UUID 列表。
 * <p>
 * 首次打开花名册时执行同步扫描（服务端），并监听死亡、离开世界等事件动态更新列表。
 * 驯服事件暂时通过扫描更新，后续可添加对应事件监听。
 */
@EventBusSubscriber(modid = "maid_construction_team")
public class PetTracker {

    /** 标记一个玩家是否已经执行过首次扫描 */
    private static final Set<UUID> scannedPlayers = new HashSet<>();

    /**
     * 执行首次扫描：遍历所有已加载实体，将当前玩家的驯服宠物 UUID 记录到 DataAttachment。
     * 仅在服务端调用，且对一个玩家只会执行一次。
     */
    public static void scanIfNeeded(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (scannedPlayers.contains(player.getUUID())) return;
        scannedPlayers.add(player.getUUID());

        List<UUID> petList = new ArrayList<>();
        for (Entity entity : serverLevel.getEntities().getAll()) {
            if (entity instanceof TamableAnimal tamable && player.getUUID().equals(tamable.getOwnerUUID())) {
                petList.add(entity.getUUID());
            }
        }

        // 存储到 DataAttachment
        player.setData(ModAttachments.PET_LIST.get(), new PetList(petList));
    }

    // 在 PetTracker.java 中添加
    public static void forceRefresh(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        List<UUID> petList = new ArrayList<>();
        for (Entity entity : serverLevel.getEntities().getAll()) {
            if (entity instanceof TamableAnimal tamable && player.getUUID().equals(tamable.getOwnerUUID())) {
                petList.add(entity.getUUID());
            }
        }
        updatePets(player, petList);
        // 确保标记为已扫描，但强制刷新时我们直接覆盖
        scannedPlayers.add(player.getUUID());
    }

    /**
     * 获取玩家宠物列表（从 DataAttachment 读取）。
     */
    public static List<UUID> getPets(Player player) {
        PetList petList = player.getData(ModAttachments.PET_LIST.get());
        return petList != null ? petList.pets() : List.of();
    }

    /**
     * 更新玩家宠物列表（覆盖写入）。
     */
    public static void updatePets(Player player, List<UUID> newList) {
        player.setData(ModAttachments.PET_LIST.get(), new PetList(newList));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        scannedPlayers.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof TamableAnimal tamable && tamable.getOwnerUUID() != null) {
            UUID ownerUUID = tamable.getOwnerUUID();
            Player owner = tamable.level().getPlayerByUUID(ownerUUID);
            if (owner != null) {
                List<UUID> current = new ArrayList<>(getPets(owner));
                if (current.remove(event.getEntity().getUUID())) {
                    updatePets(owner, current);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveWorld(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof TamableAnimal tamable && tamable.getOwnerUUID() != null) {
            UUID ownerUUID = tamable.getOwnerUUID();
            Player owner = tamable.level().getPlayerByUUID(ownerUUID);
            if (owner != null) {
                List<UUID> current = new ArrayList<>(getPets(owner));
                if (current.remove(event.getEntity().getUUID())) {
                    updatePets(owner, current);
                }
            }
        }
    }
}