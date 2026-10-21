package dev.kdevy.emirecipepins.pin;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
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
import dev.emi.emi.screen.tooltip.RecipeTooltipComponent;
import dev.kdevy.emirecipepins.RecipePinsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    /**
     * 收藏栏第一页实际渲染了多少个条目。展开的卡片会占掉好几行，所以第一页能消费的条目
     * 通常少于 EMI 按格子数算出的 pageSize；后续页没有卡片，仍然是 pageSize 个图标。
     */
    private static final Map<EmiScreenManager.ScreenSpace, Integer> FIRST_PAGE_ENTRIES = new IdentityHashMap<>();
    /** 上次看到的第一页布局（固定卡片数量 / 是否处于合成模式），用来判断实测容量是否过期 */
    private static final Map<EmiScreenManager.ScreenSpace, Integer> LAST_RECIPE_COUNT = new IdentityHashMap<>();
    private static final Map<EmiScreenManager.ScreenSpace, Boolean> LAST_SYNTHETIC = new IdentityHashMap<>();
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

    /**
     * 每帧渲染收藏栏之前调用（由 SidebarPanelMixin 在计算页码、分页按钮和 startIndex 之前执行）。
     * 只有第一页布局变了（固定卡片增删、进出合成模式）才丢弃实测容量：普通收藏的增删不会
     * 改变布局，继续沿用实测值，否则删除物品时容量会在实测值和 EMI 原始值之间切换一帧，
     * 分页就会闪烁。布局变化时丢弃实测值，也能让取消 pin 后不再残留不存在的分页。
     */
    public static void invalidateOnLayoutChange(EmiScreenManager.ScreenSpace space) {
        if (space == null || space.getType() != SidebarType.FAVORITES) {
            return;
        }
        int recipeCount = FavoriteOrganizer.getRecipeCount();
        boolean synthetic = !EmiFavorites.syntheticFavorites.isEmpty();
        Integer seenRecipes = LAST_RECIPE_COUNT.put(space, recipeCount);
        Boolean seenSynthetic = LAST_SYNTHETIC.put(space, synthetic);
        if (seenRecipes != null && (seenRecipes != recipeCount || seenSynthetic != synthetic)) {
            FIRST_PAGE_ENTRIES.remove(space);
        }
    }

    /**
     * 分页模型：第一页按实际渲染的条目数切分，后续页按 EMI 原始的每页格子数切分。
     * 第一页展开的卡片占掉了若干行，如果强行用同一个 pageSize 做等长分页，要么后续页
     * 只能塞下很少的图标（页数暴涨），要么页与页的条目范围重叠（每页内容不规律）。
     */
    public static int getPageCount(EmiScreenManager.ScreenSpace space, int fallback) {
        if (space == null || space.getType() != SidebarType.FAVORITES) {
            return fallback;
        }
        int base = space.pageSize;
        if (base <= 0) {
            return fallback;
        }
        int size = space.getStacks().size();
        int first = FIRST_PAGE_ENTRIES.getOrDefault(space, base);
        if (size <= first) {
            return 1;
        }
        return 2 + (size - first - 1) / base;
    }

    /** 把 EMI 按 {@code pageSize * page} 算出的 startIndex 映射到不等长分页的起始条目 */
    public static int getPageStart(EmiScreenManager.ScreenSpace space, int startIndex) {
        if (space == null || space.getType() != SidebarType.FAVORITES) {
            return startIndex;
        }
        int base = space.pageSize;
        if (base <= 0) {
            return startIndex;
        }
        int page = startIndex / base;
        if (page <= 0) {
            return 0;
        }
        int first = FIRST_PAGE_ENTRIES.getOrDefault(space, base);
        return first + (page - 1) * base;
    }

    public static boolean hasMultiplePages(EmiScreenManager.ScreenSpace space) {
        if (space == null) {
            return false;
        }
        int base = space.pageSize;
        int size = space.getStacks().size();
        if (space.getType() != SidebarType.FAVORITES || base <= 0) {
            return size > base;
        }
        int first = FIRST_PAGE_ENTRIES.getOrDefault(space, base);
        return size > first;
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

        int start = Math.max(0, startIndex);
        boolean firstPage = start == 0;
        int processed = 0;

        int i = start;
        for (; i < stacks.size() && y < bottom; i++) {
            EmiIngredient ingredient = stacks.get(i);
            if (FavoriteOrganizer.isSameRecipeFavorite(ingredient, EmiScreenManager.draggedStack)) {
                processed = i - start + 1;
                continue;
            }
            EmiRecipe recipe = getFavoriteRecipe(ingredient);
            // 卡片属于收藏列表开头，后续页一律用图标，保证每页的布局长度一致
            int index = firstPage && recipe != null ? recipeIndex++ : -1;
            if (index >= 0 && index < maxVisibleRecipes) {
                if (iconColumn != 0) {
                    y += ENTRY_SIZE;
                    iconColumn = 0;
                }

                float scale = getRecipeScale(recipe, maxWidth);
                int cardHeight = getRecipeCardHeight(recipe, scale);
                if (y + cardHeight <= bottom) {
                    renderRecipeCard(space, ingredient, recipe, context, x, y, maxWidth, cardHeight, scale, mouseX, mouseY, delta, hits, index);
                    y += cardHeight + RECIPE_GAP;
                    processed = i - start + 1;
                    continue;
                }
                // 卡片放不下就退回图标形式：后面的条目还要继续渲染，否则会被分页永久跳过
            }

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
            if (recipe != null) {
                hits.add(new RecipeIconHit(space, bounds, ingredient, recipe, index));
            } else {
                hits.add(new IngredientHit(space, bounds, ingredient, null));
            }
            iconColumn++;
            processed = i - start + 1;
        }

        if (firstPage) {
            int entries = processed;
            if (i >= stacks.size()) {
                // 列表不足以填满第一页：按剩余空间补足第一页的“理论容量”。否则每收藏一个
                // 条目，第一页容量都会跟着 +1，分页会在“出现”和“消失”之间来回闪。
                int columns = Math.max(1, space.tw);
                int rows = (bottom - y) / ENTRY_SIZE;
                if (rows > 0) {
                    entries += Math.max(0, columns - iconColumn) + (rows - 1) * columns;
                }
            }
            FIRST_PAGE_ENTRIES.put(space, Math.max(1, entries));
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

    /** 鼠标下方是否有 pin 卡片渲染出来的交互区域，用于接管 EMI 原生的 tooltip */
    public static boolean hasHitAt(int mouseX, int mouseY) {
        return getHit(mouseX, mouseY) != null;
    }

    /** 卡片自己的 tooltip 正在绘制，此时不能再取消 RenderTooltipEvent，否则会把自己也拦掉 */
    private static boolean renderingOwnTooltip;

    public static boolean isRenderingOwnTooltip() {
        return renderingOwnTooltip;
    }

    /**
     * 卡片区域的 tooltip 由这里统一绘制。EMI 对 {@link EmiFavorite} 的 tooltip 会无条件附加
     * 配方预览，而普通物品只有在按住「显示配方」键（默认 Shift）时才预览，两者表现不一致。
     * 因此 mixin 会取消 EMI 对卡片区域的原生 tooltip，改由这里按同样的规则重建。
     */
    public static void renderTooltip(EmiDrawContext context, int mouseX, int mouseY) {
        Hit hit = getHit(mouseX, mouseY);
        if (hit == null || !EmiScreenManager.draggedStack.isEmpty()) {
            return;
        }

        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }
        if (screen instanceof AbstractContainerScreen<?> container && !container.getMenu().getCarried().isEmpty()) {
            return;
        }

        List<ClientTooltipComponent> tooltip;
        try {
            tooltip = hit.tooltip(mouseX, mouseY);
        } catch (Throwable ignored) {
            return;
        }
        if (tooltip.isEmpty()) {
            return;
        }

        renderingOwnTooltip = true;
        try {
            if (hit.space.rtl) {
                EmiRenderHelper.drawLeftTooltip(screen, context, tooltip, mouseX, mouseY);
            } else {
                EmiRenderHelper.drawTooltip(screen, context, tooltip, mouseX, mouseY);
            }
        } catch (Throwable ignored) {
        } finally {
            renderingOwnTooltip = false;
        }
    }

    /**
     * 收藏条目的 tooltip。未按住「显示配方」键时只显示物品本身，按住时才对普通收藏补上
     * 偏好配方、对固定卡片补上它固定的配方，与 EMI 原生物品的悬停预览规则一致。
     */
    private static List<ClientTooltipComponent> favoriteTooltip(EmiIngredient ingredient) {
        if (!(ingredient instanceof EmiFavorite favorite) || favorite instanceof EmiFavorite.Synthetic) {
            return ingredient.getTooltip();
        }
        List<ClientTooltipComponent> tooltip = new ArrayList<>(favorite.getStack().getTooltip());
        if (EmiConfig.showCraft.isHeld()) {
            EmiRecipe recipe = favorite.getRecipe();
            if (recipe == null) {
                recipe = EmiUtil.getPreferredRecipe(favorite.getStack(), EmiScreenManager.lastPlayerInventory, false);
            }
            if (recipe != null) {
                tooltip.add(new RecipeTooltipComponent(recipe, true));
            }
        }
        return tooltip;
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
            return favoriteTooltip(ingredient);
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
        List<ClientTooltipComponent> tooltip(int mouseX, int mouseY) {
            return favoriteTooltip(favorite);
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
                // 槽位的配方上下文只用于交互（收藏 / 查看配方），stack 保持槽位物品本身，
                // 这样 EMI 的 tooltip 逻辑不会把槽位当成带配方的收藏而无条件预览
                return new EmiScreenManager.SidebarEmiStackInteraction(slot.getStack(), space, slot.getRecipe(), true);
            }
            EmiIngredient output = recipe.getOutputs().isEmpty() ? EmiStackInteraction.EMPTY.getStack() : recipe.getOutputs().get(0);
            return new EmiScreenManager.SidebarEmiStackInteraction(output, space, recipe, true);
        }

        @Override
        List<ClientTooltipComponent> tooltip(int mouseX, int mouseY) {
            List<ClientTooltipComponent> tooltip = new ArrayList<>(widget.getTooltip(localX(mouseX), localY(mouseY)));
            if (EmiConfig.showCraft.isHeld() && widget instanceof SlotWidget slot && slot.getRecipe() != null) {
                tooltip.add(new RecipeTooltipComponent(slot.getRecipe(), true));
            }
            return tooltip;
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
