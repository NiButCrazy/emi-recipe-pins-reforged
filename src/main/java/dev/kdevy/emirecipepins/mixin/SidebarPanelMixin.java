package dev.kdevy.emirecipepins.mixin;

import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class SidebarPanelMixin {
    @Shadow
    public EmiScreenManager.ScreenSpace space;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void emiRecipePins$invalidatePageLayout(EmiDrawContext context, int mouseX, int mouseY, float delta,
            CallbackInfo ci) {
        PinnedRecipeRenderer.invalidateOnLayoutChange(space);
    }

    /** 第一页按实际渲染条目数、后续页按 EMI 原始容量计算总页数（totalPages 位于槽位 5） */
    @ModifyVariable(method = "render", at = @At("STORE"), index = 5, remap = false)
    private int emiRecipePins$pageCount(int totalPages) {
        return PinnedRecipeRenderer.getPageCount(space, totalPages);
    }

    /** 把 EMI 按 {@code pageSize * page} 算出的 startIndex 映射到第一页实测后的分页 */
    @ModifyArg(method = "render",
            at = @At(value = "INVOKE",
                    target = "Ldev/emi/emi/screen/EmiScreenManager$ScreenSpace;render(Ldev/emi/emi/runtime/EmiDrawContext;IIFI)V",
                    ordinal = 0),
            index = 4, remap = false)
    private int emiRecipePins$pageStart(int startIndex) {
        return PinnedRecipeRenderer.getPageStart(space, startIndex);
    }

    @ModifyVariable(method = "scroll", at = @At("STORE"), index = 3, remap = false)
    private int emiRecipePins$scrollPageCount(int totalPages) {
        return PinnedRecipeRenderer.getPageCount(space, totalPages);
    }

    @ModifyVariable(method = "wrapPage", at = @At("STORE"), index = 1, remap = false)
    private int emiRecipePins$wrapPageCount(int totalPages) {
        return PinnedRecipeRenderer.getPageCount(space, totalPages);
    }

    @Inject(method = "hasMultiplePages", at = @At("RETURN"), cancellable = true, remap = false)
    private void emiRecipePins$hasMultiplePages(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(PinnedRecipeRenderer.hasMultiplePages(space));
    }
}
