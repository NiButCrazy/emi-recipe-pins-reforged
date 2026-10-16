package dev.kdevy.emirecipepins.mixin;

import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.kdevy.emirecipepins.PageSizeAccess;
import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiScreenManager.ScreenSpace.class, remap = false)
public abstract class ScreenSpaceMixin implements PageSizeAccess {
    @Mutable
    @Shadow
    @Final
    public int pageSize;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void emiRecipePins$renderFavoritesAsRecipePins(EmiDrawContext context, int mouseX, int mouseY, float delta,
            int startIndex, CallbackInfo ci) {
        EmiScreenManager.ScreenSpace space = (EmiScreenManager.ScreenSpace) (Object) this;
        PinnedRecipeRenderer.refreshPageSize(space);
        if (PinnedRecipeRenderer.renderFavorites(space, context, mouseX, mouseY, delta, startIndex)) {
            ci.cancel();
        }
    }

    @Override
    public void emiRecipePins$setPageSize(int size) {
        this.pageSize = size;
    }
}
