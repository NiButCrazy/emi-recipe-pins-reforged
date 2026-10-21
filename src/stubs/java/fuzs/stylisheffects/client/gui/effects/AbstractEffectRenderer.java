package fuzs.stylisheffects.client.gui.effects;

import net.minecraft.world.effect.MobEffectInstance;

import java.util.Optional;

/**
 * 仅用于编译期：让 Mixin 注解处理器能找到 StylishEffects 的目标类和方法。
 * 这个 source set 不会打包进 mod，运行时由 EmiRecipePinsMixinPlugin 判断模组是否存在。
 */
public class AbstractEffectRenderer {
    public Optional<MobEffectInstance> getHoveredEffect(int mouseX, int mouseY) {
        return Optional.empty();
    }
}
