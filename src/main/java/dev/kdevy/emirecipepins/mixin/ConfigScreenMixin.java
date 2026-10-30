package dev.kdevy.emirecipepins.mixin;

import dev.emi.emi.EmiPort;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.screen.ConfigScreen;
import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import dev.emi.emi.screen.widget.config.ConfigSearch;
import dev.emi.emi.screen.widget.config.GroupNameWidget;
import dev.emi.emi.screen.widget.config.IntWidget;
import dev.emi.emi.screen.widget.config.ListWidget;
import dev.kdevy.emirecipepins.RecipePinsConfig;
import dev.kdevy.emirecipepins.client.RecipeScreenHeightInfoWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ConfigScreen.class, remap = false)
public abstract class ConfigScreenMixin {
    @Shadow
    public ListWidget list;

    @Shadow
    private ConfigSearch search;

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void emiRecipePins$addEmiConfigEntry(CallbackInfo ci) {
        ConfigScreen screen = (ConfigScreen) (Object) this;
        GroupNameWidget group = new GroupNameWidget("recipe-pins", EmiPort.translatable("config.emi.group.recipe_pins"));
        ConfigEntryWidget entry = new IntWidget(
                EmiPort.translatable("config.emi.recipe_pins.max_visible_recipes"),
                List.of(EmiTooltipComponents.of(EmiPort.translatable("config.emi.tooltip.recipe_pins.max_visible_recipes"))),
                () -> search.getSearch(),
                screen.new Mutator<Integer>() {
                    @Override
                    protected Integer getValue() {
                        return RecipePinsConfig.getMaxVisibleRecipes();
                    }

                    @Override
                    protected void setValue(Integer value) {
                        RecipePinsConfig.setMaxVisibleRecipes(value);
                    }
                });
        group.children.add(entry);
        entry.parentGroups.add(group);
        list.addEntry(group);
        list.addEntry(entry);

        Component infoName = EmiPort.translatable("config.emi.ui.recipe_screen_height_categories");
        List<ClientTooltipComponent> infoTooltip = List.of(
                EmiTooltipComponents.of(
                        EmiPort.translatable("config.emi.tooltip.ui.recipe_screen_height_categories")),
                EmiTooltipComponents.of(
                        EmiPort.literal(RecipePinsConfig.getRecipeScreenHeightCategories())));
        list.addEntry(new RecipeScreenHeightInfoWidget(infoName, infoTooltip, () -> search.getSearch()));
    }
}
