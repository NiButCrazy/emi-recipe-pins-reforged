package dev.kdevy.emirecipepins.pin;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;
import dev.emi.emi.runtime.EmiPersistentData;
import dev.emi.emi.screen.EmiScreenManager;

import java.util.ArrayList;
import java.util.List;

public final class FavoriteOrganizer {
    private FavoriteOrganizer() {
    }

    public static boolean isRecipeFavorite(EmiIngredient ingredient) {
        // 合成模式下的 synthetic 条目由 EMI 自己生成，不是用户 pin 的配方
        if (ingredient instanceof EmiFavorite.Synthetic) {
            return false;
        }
        return ingredient instanceof EmiFavorite favorite && favorite.getRecipe() != null;
    }

    public static boolean isSameRecipeFavorite(EmiIngredient first, EmiIngredient second) {
        if (!(first instanceof EmiFavorite a) || !(second instanceof EmiFavorite b)) {
            return false;
        }
        return a.getRecipe() == b.getRecipe() && a.strictEquals(b);
    }

    public static int getRecipeCount() {
        int count = 0;
        for (EmiFavorite favorite : EmiFavorites.favorites) {
            if (isRecipeFavorite(favorite)) {
                count++;
            }
        }
        return count;
    }

    public static void normalize() {
        if (normalizeInMemory()) {
            EmiPersistentData.save();
            EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
        }
    }

    public static void normalizeLoadedFavorites() {
        normalizeInMemory();
    }

    public static void moveRecipeFavoriteTo(EmiIngredient ingredient, int recipeIndex) {
        if (!(ingredient instanceof EmiFavorite favorite) || !isRecipeFavorite(favorite)) {
            return;
        }

        EmiFavorite inserted = new EmiFavorite(favorite.getStack(), favorite.getRecipe());
        int original = indexOfRecipeFavorite(favorite);
        if (original != -1) {
            EmiFavorites.favorites.remove(original);
        }

        int recipeCount = getRecipeCount();
        int target = Math.max(0, Math.min(recipeIndex, recipeCount));
        EmiFavorites.favorites.add(target, inserted);
        normalizeInMemory();
        EmiPersistentData.save();
        EmiScreenManager.repopulatePanels(SidebarType.FAVORITES);
    }

    private static boolean normalizeInMemory() {
        List<EmiFavorite> recipes = new ArrayList<>();
        List<EmiFavorite> others = new ArrayList<>();
        for (EmiFavorite favorite : EmiFavorites.favorites) {
            if (isRecipeFavorite(favorite)) {
                recipes.add(favorite);
            } else {
                others.add(favorite);
            }
        }

        List<EmiFavorite> ordered = new ArrayList<>(recipes.size() + others.size());
        ordered.addAll(recipes);
        ordered.addAll(others);
        if (ordered.equals(EmiFavorites.favorites)) {
            return false;
        }

        EmiFavorites.favorites.clear();
        EmiFavorites.favorites.addAll(ordered);
        return true;
    }

    private static int indexOfRecipeFavorite(EmiFavorite needle) {
        for (int i = 0; i < EmiFavorites.favorites.size(); i++) {
            EmiFavorite favorite = EmiFavorites.favorites.get(i);
            if (favorite.getRecipe() == needle.getRecipe() && favorite.strictEquals(needle)) {
                return i;
            }
        }
        return -1;
    }
}
