package dev.kdevy.emirecipepins;

import java.awt.Desktop;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class RecipePinsConfig {
    private static final Path FILE = Path.of("config", "emi_recipe_pins.properties");
    private static final int DEFAULT_MAX_VISIBLE_RECIPES = 3;
    private static final int MIN_VISIBLE_RECIPES = 0;
    private static final int MAX_VISIBLE_RECIPES = 64;
    private static final boolean DEFAULT_BLOCK_MOUSE_PASSTHROUGH = false;
    private static final String DEFAULT_RECIPE_SCREEN_HEIGHT_CATEGORIES = "";

    private static int maxVisibleRecipes = DEFAULT_MAX_VISIBLE_RECIPES;
    private static boolean blockMousePassthrough = DEFAULT_BLOCK_MOUSE_PASSTHROUGH;
    private static String recipeScreenHeightCategories = DEFAULT_RECIPE_SCREEN_HEIGHT_CATEGORIES;
    private static Set<String> recipeScreenHeightCategorySet = Set.of();

    private RecipePinsConfig() {
    }

    public static void load() {
        Properties properties = new Properties();
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                properties.load(reader);
                maxVisibleRecipes = clamp(Integer.parseInt(properties.getProperty("maxVisibleRecipes",
                        Integer.toString(DEFAULT_MAX_VISIBLE_RECIPES))));
            } catch (Exception ignored) {
                maxVisibleRecipes = DEFAULT_MAX_VISIBLE_RECIPES;
            }
        }
        blockMousePassthrough = Boolean.parseBoolean(properties.getProperty("blockMousePassthrough",
                Boolean.toString(DEFAULT_BLOCK_MOUSE_PASSTHROUGH)));
        setRecipeScreenHeightCategories(properties.getProperty("recipeScreenHeightCategories",
                DEFAULT_RECIPE_SCREEN_HEIGHT_CATEGORIES));
        save();
    }

    public static int getMaxVisibleRecipes() {
        return maxVisibleRecipes;
    }

    /** 是否阻止鼠标悬停穿透到卡片下方：默认关闭，底层界面照常响应 */
    public static boolean shouldBlockMousePassthrough() {
        return blockMousePassthrough;
    }

    /** 逗号分隔的配方分类 id，只有这些分类的配方界面才会利用上下间距扩容 */
    public static String getRecipeScreenHeightCategories() {
        return recipeScreenHeightCategories;
    }

    public static boolean hasRecipeScreenHeightCategory(String categoryId) {
        return recipeScreenHeightCategorySet.contains(categoryId);
    }

    public static String getFilePath() {
        return FILE.toAbsolutePath().toString();
    }

    /** 用系统默认程序打开配置文件 */
    public static boolean openFile() {
        save();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(FILE.toAbsolutePath().toFile());
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void setMaxVisibleRecipes(int value) {
        maxVisibleRecipes = clamp(value);
        save();
    }

    private static void setRecipeScreenHeightCategories(String value) {
        recipeScreenHeightCategories = value == null ? DEFAULT_RECIPE_SCREEN_HEIGHT_CATEGORIES : value.trim();
        recipeScreenHeightCategorySet = Arrays.stream(recipeScreenHeightCategories.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static int clamp(int value) {
        return Math.max(MIN_VISIBLE_RECIPES, Math.min(MAX_VISIBLE_RECIPES, value));
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties properties = new Properties();
            properties.setProperty("maxVisibleRecipes", Integer.toString(maxVisibleRecipes));
            properties.setProperty("blockMousePassthrough", Boolean.toString(blockMousePassthrough));
            properties.setProperty("recipeScreenHeightCategories", recipeScreenHeightCategories);
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                properties.store(writer, "EMI Recipe Pins client settings");
            }
        } catch (IOException ignored) {
        }
    }
}
