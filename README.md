# EMI Recipe Pins Reforged

*Clanker-assisted backport to Forge 1.20.1, reforged with crafting-tree and Favorites pagination fixes.*

Client addon for EMI that renders recipe favorites as REI-style pinned recipe cards.


## Features

- Recipe favorites are kept above regular favorites.
- EMI's Favorites sidebar renders the first configured recipe favorites as full-width recipe cards.
- Extra recipe favorites stay above regular favorites as compact icons.
- Pinned recipes can be reordered by dragging them inside the Favorites panel.
- Recipes can be *~~dragged from EMI's recipe screen into the Favorites panel~~* **pinned by using your EMI fav button** (default: `Q`).
- The visible recipe-card limit is configurable from EMI's config *~~screen~~* **file** (located in `minecraft/config/emi_recipe_pins.properties`).  

*The mod is configured to display a maximum of `3` recipes by default, technically supports up to 50 (?).*   
*Supports any EMI recipe (including mods).*   
*Recipes favorited beyond this cap will render as a compact version (default EMI behaviour), until they're shuffled around or space has been freed.*  


## Build

```powershell
.\gradlew.bat --no-daemon build
```

The built jar is written to `build/libs/emi_recipe_pins-1.0.0-1.20.1.jar`.
