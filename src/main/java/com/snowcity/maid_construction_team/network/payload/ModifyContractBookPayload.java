package com.snowcity.maid_construction_team.network.payload;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import com.snowcity.maid_construction_team.component.ContractBookData;
import com.snowcity.maid_construction_team.component.ServantContractData;
import com.snowcity.maid_construction_team.core.init.ModDataComponents;
import com.snowcity.maid_construction_team.item.MaidConstructionTeamItems;
import com.snowcity.maid_construction_team.item.custom.ContractBookItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record ModifyContractBookPayload(
        byte operation,          // 0=存入全部, 1=取出全部, 2=取出单个, 3=重命名
        UUID contractId,         // 仅 operation 2,3 有效
        Optional<String> newName,
        InteractionHand hand
) implements CustomPacketPayload {

    public static final Type<ModifyContractBookPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaidConstructionTeam.MOD_ID, "modify_contract_book"));

    // 手动编解码
    public static final StreamCodec<RegistryFriendlyByteBuf, ModifyContractBookPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.operation);
                        buf.writeUUID(p.contractId);
                        // Optional<String>
                        buf.writeBoolean(p.newName.isPresent());
                        p.newName.ifPresent(buf::writeUtf);
                        // InteractionHand 用 ordinal
                        buf.writeEnum(p.hand);
                    },
                    buf -> {
                        byte op = buf.readByte();
                        UUID id = buf.readUUID();
                        String name = buf.readBoolean() ? buf.readUtf() : null;
                        InteractionHand h = buf.readEnum(InteractionHand.class);
                        return new ModifyContractBookPayload(op, id, Optional.ofNullable(name), h);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // 服务端处理
    public static void handle(ModifyContractBookPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack bookStack = player.getItemInHand(payload.hand());
            if (!(bookStack.getItem() instanceof ContractBookItem)) return;

            ContractBookData data = ContractBookItem.getData(bookStack);
            switch (payload.operation()) {
                case 0 -> storeAll(player, data);
                case 1 -> takeAll(player, data);
                case 2 -> takeSingle(player, data, payload.contractId());
                case 3 -> {
                    if (payload.newName().isPresent()) {
                        data = renameEntry(data, payload.contractId(), payload.newName().get());
                        saveAndSync(player, data, payload.hand());
                    }
                }
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        });
    }

    private static void storeAll(ServerPlayer player, ContractBookData data) {
        List<ContractBookData.ContractEntry> entries = new ArrayList<>(data.entries());
        int added = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.isEmpty()) continue;
            ServantContractData cData = stack.get(ModDataComponents.SERVANT_CONTRACT_DATA.get());
            if (cData == null) continue;
            if (entries.size() >= ContractBookItem.MAX_CAPACITY) break;

            entries.add(new ContractBookData.ContractEntry(
                    cData.contractId(), cData.entityType(), cData.servantName(),
                    cData.modelVariant(), cData.dispatchedSessionId()
            ));
            stack.shrink(1);
            added++;
        }
        data = new ContractBookData(entries);
        saveAndSync(player, data, InteractionHand.MAIN_HAND);
        player.sendSystemMessage(Component.literal("已存入 " + added + " 张契约"));
    }

    private static void takeAll(ServerPlayer player, ContractBookData data) {
        List<ContractBookData.ContractEntry> remaining = new ArrayList<>();
        int taken = 0;
        for (ContractBookData.ContractEntry entry : data.entries()) {
            if (entry.dispatchedSessionId().isPresent()) {
                remaining.add(entry);
                continue;
            }
            ItemStack contractStack = new ItemStack(MaidConstructionTeamItems.SERVANT_CONTRACT.get());
            ServantContractData cData = new ServantContractData(
                    entry.contractId(), entry.entityType(), entry.servantName(),
                    entry.modelVariant(), entry.dispatchedSessionId()
            );
            contractStack.set(ModDataComponents.SERVANT_CONTRACT_DATA.get(), cData);
            if (!player.getInventory().add(contractStack)) {
                player.drop(contractStack, false);
            }
            taken++;
        }
        data = new ContractBookData(remaining);
        saveAndSync(player, data, InteractionHand.MAIN_HAND);
        player.sendSystemMessage(Component.literal("已取出 " + taken + " 张契约"));
    }

    private static void takeSingle(ServerPlayer player, ContractBookData data, UUID contractId) {
        List<ContractBookData.ContractEntry> entries = new ArrayList<>(data.entries());
        Optional<ContractBookData.ContractEntry> opt = entries.stream()
                .filter(e -> e.contractId().equals(contractId)).findFirst();
        if (opt.isEmpty()) return;

        ContractBookData.ContractEntry entry = opt.get();
        entries.remove(entry);
        ItemStack contractStack = new ItemStack(MaidConstructionTeamItems.SERVANT_CONTRACT.get());
        ServantContractData cData = new ServantContractData(
                entry.contractId(), entry.entityType(), entry.servantName(),
                entry.modelVariant(), entry.dispatchedSessionId()
        );
        contractStack.set(ModDataComponents.SERVANT_CONTRACT_DATA.get(), cData);
        if (!player.getInventory().add(contractStack)) {
            player.drop(contractStack, false);
        }
        data = new ContractBookData(entries);
        saveAndSync(player, data, InteractionHand.MAIN_HAND);
        player.sendSystemMessage(Component.literal("已取出 " + entry.servantName()));
    }

    private static ContractBookData renameEntry(ContractBookData data, UUID contractId, String newName) {
        List<ContractBookData.ContractEntry> entries = new ArrayList<>(data.entries());
        for (int i = 0; i < entries.size(); i++) {
            ContractBookData.ContractEntry e = entries.get(i);
            if (e.contractId().equals(contractId)) {
                entries.set(i, new ContractBookData.ContractEntry(
                        e.contractId(), e.entityType(), newName, e.modelVariant(), e.dispatchedSessionId()
                ));
                break;
            }
        }
        return new ContractBookData(entries);
    }

    private static void saveAndSync(ServerPlayer player, ContractBookData newData, InteractionHand hand) {
        ItemStack bookStack = player.getItemInHand(hand);
        ContractBookItem.setData(bookStack, newData);
        player.setItemInHand(hand, bookStack);
        // 同步到客户端（会触发 GUI 刷新）
        CompoundTag tag = (CompoundTag) bookStack.save(player.registryAccess(), new CompoundTag());
        PacketDistributor.sendToPlayer(player, new SyncHandItemPayload(hand, tag));
    }
}