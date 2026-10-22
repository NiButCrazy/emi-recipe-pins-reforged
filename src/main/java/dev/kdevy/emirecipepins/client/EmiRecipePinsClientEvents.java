package dev.kdevy.emirecipepins.client;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.kdevy.emirecipepins.EmiRecipePins;
import dev.kdevy.emirecipepins.RecipePinsConfig;
import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EmiRecipePins.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmiRecipePinsClientEvents {
    private EmiRecipePinsClientEvents() {
    }

    /**
     * 用 ScreenEvent 而不是 InputEvent.Key：原版只会在屏幕没有消费按键时触发 InputEvent，
     * 而配方界面往往会吃掉按键，导致快捷键收不到。
     */
    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (EmiRecipePinsKeybinds.ADD_FAVORITE.matches(event.getKeyCode(), event.getScanCode())) {
            addPlainFavorite(event.getScreen());
        }
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Post event) {
        if (EmiRecipePinsKeybinds.ADD_FAVORITE.matchesMouse(event.getButton())) {
            addPlainFavorite(event.getScreen());
        }
    }

    /**
     * 卡片只是画在屏幕上层，下层原版 GUI（药水效果、物品槽位等）的 hover 判断仍然成立，
     * 会把它们的 tooltip 一起画出来。原版和大多数模组的 tooltip 都走这个事件，而 EMI 的
     * tooltip 是直接绘制、不经过这里，所以在卡片区域取消事件即可让下层不再“穿透”。
     */
    @SubscribeEvent
    public static void onRenderTooltip(RenderTooltipEvent.Pre event) {
        if (RecipePinsConfig.shouldBlockMousePassthrough()
                && !PinnedRecipeRenderer.isRenderingOwnTooltip()
                && PinnedRecipeRenderer.hasHitAt(event.getX(), event.getY())) {
            event.setCanceled(true);
        }
    }

    /** 在 EMI 的配方界面里把鼠标下方的物品收藏为普通收藏（不带配方上下文） */
    private static void addPlainFavorite(Screen screen) {
        if (!(screen instanceof RecipeScreen recipeScreen) || hasFocusedTextField(screen)) {
            return;
        }
        // EMI 没有给 RecipeScreen 注册 stack provider，EmiScreenManager#getHoveredStack 拿不到
        // 配方槽位；RecipeScreen 自己记录了鼠标下的 SlotWidget，优先用它，侧边栏再回退
        EmiIngredient hovered = recipeScreen.getHoveredStack();
        if (hovered.isEmpty()) {
            hovered = EmiScreenManager.getHoveredStack(
                    EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY, true).getStack();
        }
        if (hovered.isEmpty()) {
            return;
        }
        EmiFavorites.addFavorite(hovered);
        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
    }

    /** 文本输入获得焦点时不应该把按键当成收藏快捷键 */
    private static boolean hasFocusedTextField(Screen screen) {
        return hasFocusedTextField(screen, 10);
    }

    private static boolean hasFocusedTextField(ContainerEventHandler parent, int depthBail) {
        if (depthBail <= 0) {
            return false;
        }
        for (GuiEventListener element : parent.children()) {
            if (element instanceof EditBox box && box.isFocused()) {
                return true;
            } else if (element instanceof ContainerEventHandler child && hasFocusedTextField(child, depthBail - 1)) {
                return true;
            }
        }
        return false;
    }
}
