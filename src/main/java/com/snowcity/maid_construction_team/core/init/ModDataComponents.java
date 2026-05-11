package com.snowcity.maid_construction_team.core.init;

import com.mojang.serialization.Codec;
import com.snowcity.maid_construction_team.api.labor.PetList;
import com.snowcity.maid_construction_team.component.BlueprintData;
import com.snowcity.maid_construction_team.component.ContractBookData;
import com.snowcity.maid_construction_team.component.ServantContractData;
import com.snowcity.maid_construction_team.component.MaterialChecklistData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 注册数据组件
 */
public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "maid_construction_team");

    public static final Supplier<DataComponentType<BlueprintData>> BLUEPRINT_DATA =
            REGISTER.register("blueprint_data", () ->
                    DataComponentType.<BlueprintData>builder()
                            .persistent(BlueprintData.CODEC)
                            .networkSynchronized(BlueprintData.STREAM_CODEC)
                            .cacheEncoding()
                            .build()
            );

    // 物资登记表数据组件
    public static final Supplier<DataComponentType<MaterialChecklistData>> MATERIAL_CHECKLIST =
            REGISTER.register("material_checklist", () ->
                    DataComponentType.<MaterialChecklistData>builder()
                            .persistent(MaterialChecklistData.CODEC)
                            .networkSynchronized(MaterialChecklistData.STREAM_CODEC)
                            .cacheEncoding()
                            .build()
            );

    public static final Supplier<DataComponentType<Optional<UUID>>> PREVIEW_ID =
            REGISTER.register("preview_id", () ->
                    DataComponentType.<Optional<UUID>>builder()
                            .persistent(Codec.STRING.xmap(
                                    s -> s.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(s)),
                                    opt -> opt.map(UUID::toString).orElse("")
                            ))
                            .networkSynchronized(StreamCodec.of(
                                    (buf, opt) -> {
                                        buf.writeBoolean(opt.isPresent());
                                        opt.ifPresent(buf::writeUUID);
                                    },
                                    buf -> buf.readBoolean() ? Optional.of(buf.readUUID()) : Optional.empty()
                            ))
                            .cacheEncoding()
                            .build()
            );

    // 存储仆从契约数据
    public static final Supplier<DataComponentType<ServantContractData>> SERVANT_CONTRACT_DATA =
            REGISTER.register("contract_data", () ->
                    DataComponentType.<ServantContractData>builder()
                            .persistent(ServantContractData.CODEC)
                            .networkSynchronized(ServantContractData.STREAM_CODEC)
                            .cacheEncoding()
                            .build()
            );

    // 存储契约之书数据
    public static final Supplier<DataComponentType<ContractBookData>> CONTRACT_BOOK_DATA =
            REGISTER.register("contract_book_data", () ->
                    DataComponentType.<ContractBookData>builder()
                            .persistent(ContractBookData.CODEC)
                            .networkSynchronized(ContractBookData.STREAM_CODEC)
                            .cacheEncoding()
                            .build()
            );

    //存储玩家宠物 UUID 列表
    public static final Supplier<DataComponentType<PetList>> PET_LIST =
            REGISTER.register("pet_list", () ->
                    DataComponentType.<PetList>builder()
                            .persistent(PetList.CODEC)
                            .networkSynchronized(PetList.STREAM_CODEC)
                            .cacheEncoding()
                            .build()
            );
}