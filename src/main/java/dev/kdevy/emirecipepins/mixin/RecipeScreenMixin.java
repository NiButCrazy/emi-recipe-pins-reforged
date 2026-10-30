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
import dev.emi.emi.screen.WidgetGroup;
import dev.kdevy.emirecipepins.RecipePinsConfig;
import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import dev.kdevy.emirecipepins.pin.RecipeCardFavorite;
import net.minecraft.client.gui.screens.Screen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Mixin(value = RecipeScreen.class, remap = false)
public abstract class RecipeScreenMixin {
    @Shadow
    private List<WidgetGroup> currentPage;

    @Shadow
    int y;

    @Shadow
    private Map<EmiRecipeCategory, List<EmiRecipe>> recipes;

    @Unique
    private EmiRecipe emiRecipePins$pressedRecipe;

    @Unique
    private Bounds emiRecipePins$pressedRecipeBounds;

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

    /** 只有配置里列出的配方分类才利用 verticalMargin 扩容 */
    @Redirect(method = "init",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 0),
            remap = true)
    private int emiRecipePins$expandForCategory(int maximumHeight, int availableHeight) {
        if (!emiRecipePins$matchesConfiguredCategory()) {
            return Math.min(maximumHeight, availableHeight);
        }
        return Math.min(maximumHeight, ((Screen) (Object) this).height - 52);
    }

    /** 扩容时用原来的高度重算顶部位置，避免界面整体上移 */
    @Inject(method = "init",
            at = @At(value = "FIELD", target = "Ldev/emi/emi/screen/RecipeScreen;y:I",
                    opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER),
            remap = true)
    private void emiRecipePins$keepTopStable(CallbackInfo ci) {
        if (!emiRecipePins$matchesConfiguredCategory()) {
            return;
        }
        int screenHeight = ((Screen) (Object) this).height;
        int base = Math.min(EmiConfig.maximumRecipeScreenHeight,
                screenHeight - 52 - EmiConfig.verticalMargin);
        this.y = (screenHeight - base) / 2 + 1;
    }

    private boolean emiRecipePins$matchesConfiguredCategory() {
        if (recipes == null) {
            return false;
        }
        for (EmiRecipeCategory category : recipes.keySet()) {
            if (category != null && category.id != null
                    && RecipePinsConfig.hasRecipeScreenHeightCategory(category.id.toString())) {
                return true;
            }
        }
        return false;
    }
}
