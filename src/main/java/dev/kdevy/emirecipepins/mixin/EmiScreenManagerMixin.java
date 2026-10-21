package dev.kdevy.emirecipepins.mixin;

import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {
    @Inject(method = "drawBackground", at = @At("HEAD"), remap = false)
    private static void emiRecipePins$beginFrame(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PinnedRecipeRenderer.beginFrame();
    }

    @Inject(method = "drawForeground", at = @At("TAIL"), remap = false)
    private static void emiRecipePins$drawPinnedTooltip(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        PinnedRecipeRenderer.renderTooltip(context, mouseX, mouseY);
    }

    /**
     * 卡片区域的 tooltip 由 PinnedRecipeRenderer 负责，它会按「按住显示配方键才预览配方」的
     * 规则重建内容；这里取消 EMI 原生绘制，避免 EmiFavorite 无条件附带配方预览。
     */
    @Inject(method = "renderCurrentTooltip", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emiRecipePins$suppressPinnedTooltip(EmiDrawContext context, int mouseX, int mouseY, float delta,
            EmiScreenBase base, CallbackInfo ci) {
        if (PinnedRecipeRenderer.hasHitAt(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "getHoveredStack(IIZZ)Ldev/emi/emi/api/stack/EmiStackInteraction;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emiRecipePins$getHoveredPinnedStack(int mouseX, int mouseY, boolean notClick,
            boolean ignoreLastHoveredCraftable, CallbackInfoReturnable<EmiStackInteraction> cir) {
        EmiStackInteraction interaction = PinnedRecipeRenderer.getHoveredStack(mouseX, mouseY);
        if (!interaction.isEmpty()) {
            cir.setReturnValue(interaction);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emiRecipePins$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (PinnedRecipeRenderer.mouseClicked((int) mouseX, (int) mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emiRecipePins$mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (PinnedRecipeRenderer.mouseReleased((int) mouseX, (int) mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("TAIL"), remap = false)
    private static void emiRecipePins$mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY,
            CallbackInfoReturnable<Boolean> cir) {
        PinnedRecipeRenderer.wrapDraggedRecipe();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emiRecipePins$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (PinnedRecipeRenderer.keyPressed(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
