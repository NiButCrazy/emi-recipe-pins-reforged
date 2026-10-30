package dev.kdevy.emirecipepins.client;

import dev.emi.emi.screen.widget.config.ConfigEntryWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/** EMI 配置界面里的只读条目：配方界面高度扩容分类在配置文件里编辑 */
public class RecipeScreenHeightInfoWidget extends ConfigEntryWidget {
    public RecipeScreenHeightInfoWidget(Component name, List<ClientTooltipComponent> tooltip,
            Supplier<String> search) {
        super(name, tooltip, search, 20);
    }
}
