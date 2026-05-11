package com.snowcity.maid_construction_team.api.contract;

import com.snowcity.maid_construction_team.MaidConstructionTeam;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * 自动扫描带 {@link AutoContractEffect} 注解的 {@link IContractEffect} 实现类，
 * 并注册到 {@link ContractRoleRegistry}。
 * 基于 NeoForge 1.21.1 字节码扫描，无需 SPI 文件，无需手动注册。
 */
public class ContractScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContractScanner.class);

    public static void scanAndRegister() {
        ModContainer mod = ModList.get().getModContainerById(MaidConstructionTeam.MOD_ID).orElse(null);
        if (mod == null) {
            LOGGER.warn("Cannot find mod container for {}", MaidConstructionTeam.MOD_ID);
            return;
        }

        IModFileInfo modFileInfo = mod.getModInfo().getOwningFile();
        IModFile modFile = modFileInfo.getFile();
        ModFileScanData scanData = modFile.getScanResult();

        // 1.21.1 中 getAnnotations() 返回 Set<AnnotationData>
        Set<ModFileScanData.AnnotationData> annotations = scanData.getAnnotations();
        Type targetAnnotation = Type.getType(AutoContractEffect.class);

        for (ModFileScanData.AnnotationData data : annotations) {
            // 只处理 @AutoContractEffect 注解
            if (!data.annotationType().equals(targetAnnotation)) continue;

            // data.clazz() 返回的是 Type，需转换为全限定类名字符串
            String className = data.clazz().getClassName();

            try {
                Class<?> clazz = Class.forName(className);
                // 必须实现 IContractEffect 且非抽象
                if (!IContractEffect.class.isAssignableFrom(clazz) || Modifier.isAbstract(clazz.getModifiers())) {
                    continue;
                }

                AutoContractEffect def = clazz.getAnnotation(AutoContractEffect.class);
                if (def == null) continue;

                IContractEffect effect = (IContractEffect) clazz.getDeclaredConstructor().newInstance();
                ContractRoleRegistry.registerEffect(effect);

                for (String rawType : def.assignTo()) {
                    ResourceLocation rl = ResourceLocation.parse(rawType);
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
                    if (type != null) {
                        ContractRoleRegistry.assignRole(type, effect.getRoleId());
                        ContractRoleRegistry.setMaxActive(type, def.maxActive());
                    } else {
                        LOGGER.warn("Unknown entity type: {} for effect {}", rawType, className);
                    }
                }

                LOGGER.info("Auto-registered contract effect: {} ({})", effect.getRoleId(), className);
            } catch (Exception e) {
                LOGGER.error("Failed to auto-register contract effect from class: {}", className, e);
            }
        }
    }
}