package com.snowcity.maid_construction_team.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = "maid_construction_team", value = Dist.CLIENT)
public class ModKeyMappings {

    /** 工具切换键（按住 + 滚轮切换微调工具） */
    public static final Lazy<KeyMapping> PREVIEW_TOOL_SWITCH = Lazy.of(() -> new KeyMapping(
            "key.maid_construction_team.preview_tool_switch",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.category.maid_construction_team"
    ));

    /** 操作执行键（按住 + 滚轮执行当前工具操作） */
    public static final Lazy<KeyMapping> PREVIEW_EXECUTE = Lazy.of(() -> new KeyMapping(
            "key.maid_construction_team.preview_execute",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.category.maid_construction_team"
    ));

    /** 确认放置 */
    public static final Lazy<KeyMapping> PREVIEW_CONFIRM = Lazy.of(() -> new KeyMapping(
            "key.maid_construction_team.preview_confirm",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            "key.category.maid_construction_team"
    ));

    /** 取消预览（改为 R 键） */
    public static final Lazy<KeyMapping> PREVIEW_CANCEL = Lazy.of(() -> new KeyMapping(
            "key.maid_construction_team.preview_cancel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.category.maid_construction_team"
    ));

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PREVIEW_TOOL_SWITCH.get());
        event.register(PREVIEW_EXECUTE.get());
        event.register(PREVIEW_CONFIRM.get());
        event.register(PREVIEW_CANCEL.get());
    }
}