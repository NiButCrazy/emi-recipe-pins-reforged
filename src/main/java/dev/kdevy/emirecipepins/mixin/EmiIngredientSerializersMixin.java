package dev.kdevy.emirecipepins.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.registry.EmiIngredientSerializers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 某些模组的自定义 EmiStack（例如 attributeslib 的属性）序列化结果只有 {"id": "..."}，
 * 没有 EMI 期望的 "type" 字段。EMI 读取 type 时会抛 NPE，虽然会被 catch 成空 stack，
 * 但每次加载持久化数据、或者用收藏键收藏这类条目时都会刷一条错误日志。
 * 这里在缺 type 时按物品兜底解析，解析不到就安静地返回空。
 */
@Mixin(value = EmiIngredientSerializers.class, remap = false)
public abstract class EmiIngredientSerializersMixin {
    @Inject(method = "deserialize", at = @At("HEAD"), cancellable = true, remap = false)
    private static void emiRecipePins$deserializeIdOnly(JsonElement element,
            CallbackInfoReturnable<EmiIngredient> cir) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject json = element.getAsJsonObject();
        if (json.has("type") || !json.has("id")) {
            return;
        }
        EmiIngredientSerializer<?> serializer = EmiIngredientSerializers.BY_TYPE.get("emi:item");
        if (serializer != null) {
            try {
                cir.setReturnValue(serializer.deserialize(element));
                return;
            } catch (Throwable ignored) {
            }
        }
        cir.setReturnValue(EmiStack.EMPTY);
    }
}
