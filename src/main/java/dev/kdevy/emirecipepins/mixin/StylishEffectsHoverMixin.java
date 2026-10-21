package dev.kdevy.emirecipepins.mixin;

import dev.kdevy.emirecipepins.pin.PinnedRecipeRenderer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * StylishEffects 的效果图标完全按鼠标坐标自行判断 hover，不经过原版槽位或 tooltip 事件，
 * 所以在 pin 卡片覆盖到它们时，hover、tooltip、点击都会穿透。
 * 它的所有判断都汇总在 {@code getHoveredEffect}，卡片区域直接返回空即可。
 * 该 mixin 由 EmiRecipePinsMixinPlugin 控制，仅当 StylishEffects 已加载时应用。
 */
@Mixin(targets = "fuzs.stylisheffects.client.gui.effects.AbstractEffectRenderer", remap = false)
public abstract class StylishEffectsHoverMixin {
    @Inject(method = "getHoveredEffect", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void emiRecipePins$blockHoverThroughCards(int mouseX, int mouseY,
            CallbackInfoReturnable<Optional<MobEffectInstance>> cir) {
        if (PinnedRecipeRenderer.hasHitAt(mouseX, mouseY)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
