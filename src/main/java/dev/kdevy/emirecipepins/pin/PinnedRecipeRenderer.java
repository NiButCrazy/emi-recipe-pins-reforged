package dev.kdevy.emirecipepins.pin;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.ButtonWidget;
import dev.emi.emi.api.widget.RecipeFillButtonWidget;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.screen.EmiScreenManager;
import dev.kdevy.emirecipepins.RecipePinsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PinnedRecipeRenderer {
    private static final int ENTRY_SIZE = 18;
    private static final int RECIPE_PADDING = 4;
    private static final int RECIPE_GAP = 5;
    private static final float DRAG_ANIMATION_SPEED = 0.36f;
    private static final float SNAP_ANIMATION_SPEED = 0.42f;
    private static final Map<EmiScreenManager.ScreenSpace, List<Hit>> HITS = new IdentityHashMap<>();
    private static EmiRecipe animatedDragRecipe;
    private static DragAnimation dragAnimation;

    private PinnedRecipeRenderer() {
    }

    public static void beginFrame() {
        HITS.clear();
        if (!FavoriteOrganizer.isRecipeFavorite(EmiScreenManager.draggedStack)) {
            resetDragAnimation();
        }
    }

    public static boolean renderFavorites(EmiScreenManager.ScreenSpace space, EmiDrawContext context, int mouseX,
            int mouseY, float delta, int startIndex) {
        if (space.getType() != SidebarType.FAVORITES || !hasRecipeFavorites(space.getStacks())) {
            HITS.remove(space);
            return false;
        }

        List<Hit> hits = new ArrayList<>();
        List<? extends EmiIngredient> stacks = space.getStacks();
        int maxWidth = Math.max(ENTRY_SIZE, space.tw * ENTRY_SIZE);
        int bottom = space.ty + space.th * ENTRY_SIZE;
        int x = space.tx;
        int y = space.ty;
        int iconColumn = 0;
        int recipeIndex = 0;
        int maxVisibleRecipes = getMaxVisibleRecipes();

        context.enableDepthTest();
        context.resetColor();

        for (int i = Math.max(0, startIndex); i < stacks.size() && y < bottom; i++) {
            EmiIngredient ingredient = stacks.get(i);
            if (FavoriteOrganizer.isSameRecipeFavorite(ingredient, EmiScreenManager.draggedStack)) {
                continue;
            }
            EmiRecipe recipe = getFavoriteRecipe(ingredient);
            if (recipe != null) {
                if (recipeIndex < maxVisibleRecipes) {
                    if (iconColumn != 0) {
                        y += ENTRY_SIZE;
                        iconColumn = 0;
                    }

                    float scale = getRecipeScale(recipe, maxWidth);
                    int cardHeight = getRecipeCardHeight(recipe, scale);
                    if (y + cardHeight > bottom) {
                        break;
                    }

                    renderRecipeCard(space, ingredient, recipe, context, x, y, maxWidth, cardHeight, scale, mouseX, mouseY, delta, hits, recipeIndex);
                    y += cardHeight + RECIPE_GAP;
                } else {
                    if (iconColumn >= Math.max(1, space.tw)) {
                        y += ENTRY_SIZE;
                        iconColumn = 0;
                    }
                    if (y + ENTRY_SIZE > bottom) {
                        break;
                    }
                    int iconX = space.rtl ? x + (space.tw - 1 - iconColumn) * ENTRY_SIZE : x + iconColumn * ENTRY_SIZE;
                    Bounds bounds = new Bounds(iconX, y, ENTRY_SIZE, ENTRY_SIZE);
                    if (bounds.contains(mouseX, mouseY) && EmiConfig.showHoverOverlay) {
                        EmiRenderHelper.drawSlotHightlight(context, iconX, y, ENTRY_SIZE, ENTRY_SIZE, 0);
                    }
                    ingredient.render(context.raw(), iconX + 1, y + 1, delta);
                    hits.add(new RecipeIconHit(space, bounds, ingredient, recipe, recipeIndex));
                    iconColumn++;
                }
                recipeIndex++;
            } else {
                if (iconColumn >= Math.max(1, space.tw)) {
                    y += ENTRY_SIZE;
                    iconColumn = 0;
                }
                if (y + ENTRY_SIZE > bottom) {
                    break;
                }
                int iconX = space.rtl ? x + (space.tw - 1 - iconColumn) * ENTRY_SIZE : x + iconColumn * ENTRY_SIZE;
                Bounds bounds = new Bounds(iconX, y, ENTRY_SIZE, ENTRY_SIZE);
                if (bounds.contains(mouseX, mouseY) && EmiConfig.showHoverOverlay) {
                    EmiRenderHelper.drawSlotHightlight(context, iconX, y, ENTRY_SIZE, ENTRY_SIZE, 0);
                }
                ingredient.render(context.raw(), iconX + 1, y + 1, delta);
                hits.add(new IngredientHit(space, bounds, ingredient, getFavoriteRecipe(ingredient)));
                iconColumn++;
            }
        }

        HITS.put(space, hits);
        return true;
    }

    public static EmiStackInteraction getHoveredStack(int mouseX, int mouseY) {
        Hit hit = getHit(mouseX, mouseY);
        if (hit == null) {
            return EmiStackInteraction.EMPTY;
        }
        return hit.interaction();
    }

    public static boolean mouseClicked(int mouseX, int mouseY, int button) {
        Hit hit = getHit(mouseX, mouseY);
        if (hit instanceof WidgetHit widgetHit && !(widgetHit.widget instanceof SlotWidget)) {
            return widgetHit.widget.mouseClicked(widgetHit.localX(mouseX), widgetHit.localY(mouseY), button);
        }
        return false;
    }

    public static void wrapDraggedRecipe() {
        if (EmiScreenManager.draggedStack instanceof EmiFavorite favorite
                && !(favorite instanceof RecipeCardFavorite)
                && FavoriteOrganizer.isRecipeFavorite(favorite)) {
            EmiScreenManager.draggedStack = new RecipeCardFavorite(favorite);
        }
    }

    public static boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button != 0 || !FavoriteOrganizer.isRecipeFavorite(EmiScreenManager.draggedStack)) {
            return false;
        }
        EmiScreenManager.ScreenSpace space = EmiScreenManager.getHoveredSpace(mouseX, mouseY);
        if (space == null || space.getType() != SidebarType.FAVORITES) {
            return false;
        }

        FavoriteOrganizer.moveRecipeFavoriteTo(EmiScreenManager.draggedStack, getRecipeDropIndex(space, mouseX, mouseY));
        EmiScreenManager.pressedStack = EmiStack.EMPTY;
        EmiScreenManager.draggedStack = EmiStack.EMPTY;
        resetDragAnimation();
        return true;
    }

    public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Hit hit = getHit(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY);
        if (hit instanceof WidgetHit widgetHit && !(widgetHit.widget instanceof SlotWidget)) {
            return widgetHit.widget.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public static void renderDraggedRecipe(EmiDrawContext context, EmiFavorite favorite, int cursorX, int cursorY, float delta) {
        EmiRecipe recipe = favorite.getRecipe();
        if (recipe == null) {
            resetDragAnimation();
            return;
        }

        DragPreview target = getDragPreview(recipe, cursorX, cursorY);
        DragAnimation preview = updateDragAnimation(recipe, target);
        int x = Math.round(preview.x);
        int y = Math.round(preview.y);
        int width = Math.max(ENTRY_SIZE, Math.round(preview.width));
        int height = Math.max(ENTRY_SIZE, Math.round(preview.height));
        context.push();
        context.matrices().translate(0, 0, 500);
        renderRecipeCardVisual(recipe, context, x, y, width, height, preview.scale,
                -1000, -1000, delta, null, null, -1);
        int snapAlpha = Math.max(0, Math.min(255, Math.round(preview.snap * 255)));
        if (snapAlpha > 4) {
            int snapColor = (snapAlpha << 24) | 0x00FFFF;
            context.fill(x, y - 2, width, 2, snapColor);
            context.fill(x, y + height, width, 2, snapColor);
        }
        context.pop();
    }

    public static void startDragAnimation(EmiRecipe recipe, Bounds sourceBounds) {
        if (recipe == null || sourceBounds == null) {
            resetDragAnimation();
            return;
        }

        int width = Math.max(ENTRY_SIZE, sourceBounds.width());
        float scale = getRecipeScale(recipe, width);
        int height = Math.max(ENTRY_SIZE, sourceBounds.height());
        animatedDragRecipe = recipe;
        dragAnimation = new DragAnimation(new DragPreview(sourceBounds.x(), sourceBounds.y(), width, height, scale, false));
    }

    public static void renderTooltip(EmiDrawContext context, int mouseX, int mouseY) {
        Hit hit = getHit(mouseX, mouseY);
        if (!(hit instanceof WidgetHit widgetHit) || widgetHit.widget instanceof SlotWidget) {
            return;
        }

        List<ClientTooltipComponent> tooltip = widgetHit.tooltip(mouseX, mouseY);
        if (tooltip.isEmpty()) {
            return;
        }

        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }
        if (hit.space.rtl) {
            EmiRenderHelper.drawLeftTooltip(screen, context, tooltip, mouseX, mouseY);
        } else {
            EmiRenderHelper.drawTooltip(screen, context, tooltip, mouseX, mouseY);
        }
    }

    private static boolean hasRecipeFavorites(List<? extends EmiIngredient> stacks) {
        for (EmiIngredient stack : stacks) {
            if (getFavoriteRecipe(stack) != null) {
                return true;
            }
        }
        return false;
    }

    private static EmiRecipe getFavoriteRecipe(EmiIngredient ingredient) {
        // 合成模式的 synthetic 条目（合成步骤 / 材料统计）必须按 EMI 原版样式渲染，
        // 它们不是用户 pin 的配方，也不该占用卡片名额
        if (ingredient instanceof EmiFavorite.Synthetic) {
            return null;
        }
        if (ingredient instanceof EmiFavorite favorite) {
            return favorite.getRecipe();
        }
        return null;
    }

    /**
     * EMI 合成模式（Crafting mode）会把合成步骤与材料统计作为 synthetic favorites 追加到
     * 收藏栏末尾，它们排在 pin 卡片之后，卡片展开时会把这些统计挤出可视区。
     * 因此只要存在 synthetic 条目，就把卡片全部收成图标，让统计保持 EMI 原生渲染。
     */
    private static int getMaxVisibleRecipes() {
        if (!EmiFavorites.syntheticFavorites.isEmpty()) {
            return 0;
        }
        return RecipePinsConfig.getMaxVisibleRecipes();
    }

    private static float getRecipeScale(EmiRecipe recipe, int maxWidth) {
        int contentWidth = Math.max(1, maxWidth - RECIPE_PADDING * 2);
        if (recipe.getDisplayWidth() <= contentWidth) {
            return 1.0f;
        }
        return Math.max(0.25f, contentWidth / (float) recipe.getDisplayWidth());
    }

    private static int getRecipeCardHeight(EmiRecipe recipe, float scale) {
        return Math.max(ENTRY_SIZE, Math.round(recipe.getDisplayHeight() * scale) + RECIPE_PADDING * 2);
    }

    private static void renderRecipeCard(EmiScreenManager.ScreenSpace space, EmiIngredient favorite, EmiRecipe recipe,
            EmiDrawContext context, int x, int y, int cardWidth, int cardHeight, float scale, int mouseX, int mouseY,
            float delta, List<Hit> hits, int recipeIndex) {
        List<Widget> widgets = Lists.newArrayList();
        int contentX = renderRecipeCardVisual(recipe, context, x, y, cardWidth, cardHeight, scale, mouseX, mouseY, delta,
                widgets, hits, recipeIndex);
        if (contentX != Integer.MIN_VALUE) {
            Bounds cardBounds = new Bounds(x, y, cardWidth, cardHeight);
            hits.add(new RecipeHit(space, cardBounds, favorite, recipe, recipeIndex));
            for (Widget widget : widgets) {
                if (!isInteractiveWidget(widget)) {
                    continue;
                }
                Bounds bounds = widget.getBounds();
                Bounds scaledBounds = new Bounds(
                        x + contentX + Math.round(bounds.x() * scale),
                        y + Math.round((RECIPE_PADDING + bounds.y()) * scale),
                        Math.max(1, Math.round(bounds.width() * scale)),
                        Math.max(1, Math.round(bounds.height() * scale)));
                hits.add(new WidgetHit(space, scaledBounds, widget, recipe, x, y, contentX, scale));
            }
        }
    }

    private static int renderRecipeCardVisual(EmiRecipe recipe, EmiDrawContext context, int x, int y, int cardWidth,
            int cardHeight, float scale, int mouseX, int mouseY, float delta, List<Widget> widgetsOut, List<Hit> hits,
            int recipeIndex) {
        List<Widget> widgets = Lists.newArrayList();
        WidgetHolder holder = new WidgetHolder() {
            @Override
            public int getWidth() {
                return recipe.getDisplayWidth();
            }

            @Override
            public int getHeight() {
                return recipe.getDisplayHeight();
            }

            @Override
            public <T extends Widget> T add(T widget) {
                widgets.add(widget);
                return widget;
            }
        };

        boolean pushed = false;
        try {
            recipe.addWidgets(holder);
            if (widgetsOut != null) {
                widgetsOut.addAll(widgets);
            }
            context.push();
            pushed = true;
            context.matrices().translate(x, y, 0);
            EmiRenderHelper.drawNinePatch(context, EmiRenderHelper.BACKGROUND, 0, 0, cardWidth, cardHeight, 27, 0, 4, 1);
            int contentX = Math.round((cardWidth - recipe.getDisplayWidth() * scale) / 2.0f);
            context.matrices().translate(contentX, RECIPE_PADDING, 0);
            context.matrices().scale(scale, scale, 1.0f);
            int localMouseX = (int) ((mouseX - x - contentX) / scale);
            int localMouseY = (int) ((mouseY - y - RECIPE_PADDING) / scale);
            for (Widget widget : widgets) {
                widget.render(context.raw(), localMouseX, localMouseY, delta);
            }
            context.pop();
            pushed = false;
            return contentX;
        } catch (Throwable ignored) {
            if (pushed) {
                context.pop();
            }
            return Integer.MIN_VALUE;
        }
    }

    private static Hit getHit(int mouseX, int mouseY) {
        Hit found = null;
        for (List<Hit> hits : HITS.values()) {
            for (Hit hit : hits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    found = hit;
                }
            }
        }
        return found;
    }

    private static int getRecipeDropIndex(EmiScreenManager.ScreenSpace space, int mouseX, int mouseY) {
        int index = 0;
        List<Hit> hits = HITS.getOrDefault(space, List.of());
        for (Hit hit : hits) {
            if (hit instanceof RecipeOrderHit orderHit) {
                Bounds bounds = hit.bounds;
                if (mouseY < bounds.y() + bounds.height() / 2
                        || (mouseY < bounds.bottom() && mouseX < bounds.x() + bounds.width() / 2)) {
                    return orderHit.recipeIndex();
                }
                index = Math.max(index, orderHit.recipeIndex() + 1);
            }
        }
        return index;
    }

    private static DragPreview getDragPreview(EmiRecipe recipe, int cursorX, int cursorY) {
        EmiScreenManager.ScreenSpace space = EmiScreenManager.getHoveredSpace(EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY);
        int originalWidth = getNaturalDragWidth(recipe);
        if (space != null && space.getType() == SidebarType.FAVORITES) {
            int width = Math.max(originalWidth, Math.max(ENTRY_SIZE, space.tw * ENTRY_SIZE));
            float scale = getRecipeScale(recipe, width);
            int height = getRecipeCardHeight(recipe, scale);
            int index = getRecipeDropIndex(space, EmiScreenManager.lastMouseX, EmiScreenManager.lastMouseY);
            int y = getRecipeSnapY(space, index);
            return new DragPreview(space.tx, y, width, height, scale, true);
        }

        int width = originalWidth;
        float scale = getRecipeScale(recipe, width);
        int height = getRecipeCardHeight(recipe, scale);
        return new DragPreview(cursorX - width / 2, cursorY - 12, width, height, scale, false);
    }

    private static int getNaturalDragWidth(EmiRecipe recipe) {
        return Math.max(ENTRY_SIZE * 4, recipe.getDisplayWidth() + RECIPE_PADDING * 2);
    }

    private static DragAnimation updateDragAnimation(EmiRecipe recipe, DragPreview target) {
        if (dragAnimation == null || animatedDragRecipe != recipe) {
            animatedDragRecipe = recipe;
            dragAnimation = new DragAnimation(target);
        } else {
            dragAnimation.update(target);
        }
        return dragAnimation;
    }

    private static void resetDragAnimation() {
        animatedDragRecipe = null;
        dragAnimation = null;
    }

    private static float approach(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private static int getRecipeSnapY(EmiScreenManager.ScreenSpace space, int index) {
        int y = space.ty;
        for (Hit hit : HITS.getOrDefault(space, List.of())) {
            if (hit instanceof RecipeOrderHit orderHit) {
                if (orderHit.recipeIndex() >= index) {
                    return hit.bounds.y();
                }
                y = hit.bounds.bottom() + RECIPE_GAP;
            }
        }
        return y;
    }

    private static boolean isInteractiveWidget(Widget widget) {
        return widget instanceof SlotWidget || widget instanceof ButtonWidget || widget instanceof RecipeFillButtonWidget;
    }

    private abstract static class Hit {
        final EmiScreenManager.ScreenSpace space;
        final Bounds bounds;

        Hit(EmiScreenManager.ScreenSpace space, Bounds bounds) {
            this.space = space;
            this.bounds = bounds;
        }

        abstract EmiStackInteraction interaction();

        List<ClientTooltipComponent> tooltip(int mouseX, int mouseY) {
            return List.of();
        }
    }

    private static class IngredientHit extends Hit {
        private final EmiIngredient ingredient;
        private final EmiRecipe recipe;

        IngredientHit(EmiScreenManager.ScreenSpace space, Bounds bounds, EmiIngredient ingredient, EmiRecipe recipe) {
            super(space, bounds);
            this.ingredient = ingredient;
            this.recipe = recipe;
        }

        @Override
        EmiStackInteraction interaction() {
            return new EmiScreenManager.SidebarEmiStackInteraction(ingredient, space, recipe, true);
        }

        @Override
        List<ClientTooltipComponent> tooltip(int mouseX, int mouseY) {
            return ingredient.getTooltip();
        }
    }

    private static final class RecipeHit extends Hit implements RecipeOrderHit {
        private final EmiIngredient favorite;
        private final EmiRecipe recipe;
        private final int recipeIndex;

        RecipeHit(EmiScreenManager.ScreenSpace space, Bounds bounds, EmiIngredient favorite, EmiRecipe recipe, int recipeIndex) {
            super(space, bounds);
            this.favorite = favorite;
            this.recipe = recipe;
            this.recipeIndex = recipeIndex;
        }

        @Override
        EmiStackInteraction interaction() {
            return new EmiScreenManager.SidebarEmiStackInteraction(favorite, space, recipe, true);
        }

        @Override
        public int recipeIndex() {
            return recipeIndex;
        }
    }

    private static final class RecipeIconHit extends IngredientHit implements RecipeOrderHit {
        private final int recipeIndex;

        RecipeIconHit(EmiScreenManager.ScreenSpace space, Bounds bounds, EmiIngredient ingredient, EmiRecipe recipe, int recipeIndex) {
            super(space, bounds, ingredient, recipe);
            this.recipeIndex = recipeIndex;
        }

        @Override
        public int recipeIndex() {
            return recipeIndex;
        }
    }

    private static final class WidgetHit extends Hit {
        private final Widget widget;
        private final EmiRecipe recipe;
        private final int cardX;
        private final int cardY;
        private final int contentX;
        private final float scale;

        WidgetHit(EmiScreenManager.ScreenSpace space, Bounds bounds, Widget widget, EmiRecipe recipe, int cardX,
                int cardY, int contentX, float scale) {
            super(space, bounds);
            this.widget = widget;
            this.recipe = recipe;
            this.cardX = cardX;
            this.cardY = cardY;
            this.contentX = contentX;
            this.scale = scale;
        }

        @Override
        EmiStackInteraction interaction() {
            if (widget instanceof SlotWidget slot) {
                EmiRecipe context = slot.getRecipe();
                EmiIngredient ingredient = context == null ? slot.getStack() : new EmiFavorite(slot.getStack(), context);
                return new EmiScreenManager.SidebarEmiStackInteraction(ingredient, space, context, true);
            }
            EmiIngredient output = recipe.getOutputs().isEmpty() ? EmiStackInteraction.EMPTY.getStack() : recipe.getOutputs().get(0);
            return new EmiScreenManager.SidebarEmiStackInteraction(output, space, recipe, true);
        }

        @Override
        List<ClientTooltipComponent> tooltip(int mouseX, int mouseY) {
            return widget.getTooltip(localX(mouseX), localY(mouseY));
        }

        int localX(int mouseX) {
            return (int) ((mouseX - cardX - contentX) / scale);
        }

        int localY(int mouseY) {
            return (int) ((mouseY - cardY - RECIPE_PADDING) / scale);
        }
    }

    private interface RecipeOrderHit {
        int recipeIndex();
    }

    private static final class DragAnimation {
        private float x;
        private float y;
        private float width;
        private float height;
        private float scale;
        private float snap;

        private DragAnimation(DragPreview target) {
            x = target.x;
            y = target.y;
            width = target.width;
            height = target.height;
            scale = target.scale;
            snap = target.snapped ? 1.0f : 0.0f;
        }

        private void update(DragPreview target) {
            x = approach(x, target.x, DRAG_ANIMATION_SPEED);
            y = approach(y, target.y, DRAG_ANIMATION_SPEED);
            width = approach(width, target.width, DRAG_ANIMATION_SPEED);
            height = approach(height, target.height, DRAG_ANIMATION_SPEED);
            scale = approach(scale, target.scale, DRAG_ANIMATION_SPEED);
            snap = approach(snap, target.snapped ? 1.0f : 0.0f, SNAP_ANIMATION_SPEED);
        }
    }

    private record DragPreview(int x, int y, int width, int height, float scale, boolean snapped) {
    }
}
