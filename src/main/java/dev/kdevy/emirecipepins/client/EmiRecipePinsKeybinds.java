package dev.kdevy.emirecipepins.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kdevy.emirecipepins.EmiRecipePins;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = EmiRecipePins.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EmiRecipePinsKeybinds {
    public static final String KEY_CATEGORY = "key.categories.emi_recipe_pins";

    /**
     * 配方界面里的普通收藏键：只收藏物品本身，不生成固定配方卡片。
     * EMI 自己的收藏键在配方界面固定的是带配方的卡片，这个按键提供互补的行为。
     */
    public static final KeyMapping ADD_FAVORITE = new KeyMapping(
            "key.emi_recipe_pins.add_favorite",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            KEY_CATEGORY);

    private EmiRecipePinsKeybinds() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ADD_FAVORITE);
    }
}
