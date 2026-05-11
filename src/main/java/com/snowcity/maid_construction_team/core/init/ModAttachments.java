package com.snowcity.maid_construction_team.core.init;

import com.snowcity.maid_construction_team.api.labor.PetList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "maid_construction_team");

    public static final Supplier<AttachmentType<PetList>> PET_LIST =
            ATTACHMENT_TYPES.register("pet_list", () ->
                    AttachmentType.builder(() -> new PetList(List.of()))
                            .serialize(PetList.CODEC)
                            .build()
            );
}