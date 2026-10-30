package dev.kdevy.emirecipepins.mixin;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.ButtonWidget;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.RecipeTab;
import dev.emi.emi.screen.WidgetGroup;
import dev.kdevy.emirecipepins.RecipePinsConfig;
import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import dev.kdevy.emirecipepins.pin.RecipeCardFavorite;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin {
    @Shadow
    private List<WidgetGroup> currentPage;

    @Shadow
    private List<RecipeTab> tabs;

    @Shadow
    private int tab;

    @Shadow
    int backgroundHeight;

    @Shadow
    int y;

    @Unique
    private EmiRecipe emiRecipePins$pressedRecipe;

    @Unique
    private Bounds emiRecipePins$pressedRecipeBounds;

    @Unique
    private boolean emiRecipePins$updatingHeight;

    @Inject(method = "mouseClicked", at = @At("TAIL"), remap = true)
    private void emiRecipePins$captureRecipeDrag(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        RecipePress press = button == 0 ? emiRecipePins$getRecipePressAt((int) mouseX, (int) mouseY) : null;
        emiRecipePins$pressedRecipe = press == null ? null : press.recipe();
        emiRecipePins$pressedRecipeBounds = press == null ? null : press.bounds();
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), remap = true)
    private void emiRecipePins$dragRecipe(double mouseX, double mouseY, int button, double deltaX, double deltaY,
            CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && emiRecipePins$pressedRecipe != null && !emiRecipePins$pressedRecipe.getOutputs().isEmpty()) {
            EmiIngredient favorite = new RecipeCardFavorite(emiRecipePins$pressedRecipe.getOutputs().get(0), emiRecipePins$pressedRecipe);
            EmiScreenManager.pressedStack = favorite;
            EmiScreenManager.draggedStack = favorite;
            if (emiRecipePins$pressedRecipeBounds != null) {
                PinnedRecipeRenderer.startDragAnimation(emiRecipePins$pressedRecipe, emiRecipePins$pressedRecipeBounds);
            }
            emiRecipePins$pressedRecipe = null;
            emiRecipePins$pressedRecipeBounds = null;
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"), remap = true)
    private void emiRecipePins$releaseRecipeDrag(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        emiRecipePins$pressedRecipe = null;
        emiRecipePins$pressedRecipeBounds = null;
    }

    @Unique
    private RecipePress emiRecipePins$getRecipePressAt(int mouseX, int mouseY) {
        for (WidgetGroup group : currentPage) {
            Bounds bounds = new Bounds(group.x(), group.y(), group.getWidth(), group.getHeight());
            if (group.recipe == null || !bounds.contains(mouseX, mouseY)) {
                continue;
            }

            int localX = mouseX - group.x();
            int localY = mouseY - group.y();
            for (Widget widget : group.widgets) {
                if (emiRecipePins$blocksRecipeDrag(widget) && widget.getBounds().contains(localX, localY)) {
                    return null;
                }
            }
            return new RecipePress(group.recipe, bounds);
        }
        return null;
    }

    @Unique
    private boolean emiRecipePins$blocksRecipeDrag(Widget widget) {
        return widget instanceof SlotWidget
                || widget instanceof ButtonWidget
                || widget instanceof RecipeFillButtonWidget;
    }

    @Unique
    private record RecipePress(EmiRecipe recipe, Bounds bounds) {
    }

    /**
     * 切换分类时按当前选中的 tab 调整背景高度：只有配置里列出的分类才利用
     * verticalMargin 扩容，其它分类恢复原版高度。顶部位置始终按原版高度计算，
     * 所以高度变化只会向下扩展/收缩。
     */
    @Inject(method = "setPage", at = @At("TAIL"), remap = false)
    private void emiRecipePins$updateHeightForTab(int tabPage, int tab, int page, CallbackInfo ci) {
        if (emiRecipePins$updatingHeight) {
            return;
        }
        int screenHeight = ((Screen) (Object) this).height;
        int base = Math.min(EmiConfig.maximumRecipeScreenHeight,
                screenHeight - 52 - EmiConfig.verticalMargin);
        int target = emiRecipePins$shouldExpand()
                ? Math.min(EmiConfig.maximumRecipeScreenHeight, screenHeight - 52)
                : base;
        if (target == backgroundHeight) {
            return;
        }

        emiRecipePins$updatingHeight = true;
        try {
            backgroundHeight = target;
            y = (screenHeight - base) / 2 + 1;
            // 只重算当前 tab 的分页；其它 tab 在切换过去时会按各自的高度重新计算
            tabs.get(this.tab).bakePages(target);
            ((RecipeScreen) (Object) this).setPage(tabPage, tab, page);
        } finally {
            emiRecipePins$updatingHeight = false;
        }
    }

    @Unique
    private boolean emiRecipePins$shouldExpand() {
        if (tab < 0 || tab >= tabs.size()) {
            return false;
        }
        EmiRecipeCategory category = tabs.get(tab).category;
        return category != null && category.id != null
                && RecipePinsConfig.hasRecipeScreenHeightCategory(category.id.toString());
    }
}
