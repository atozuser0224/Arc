package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class ItemEntitySettings extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".item-entity-settings";
    }

    public static int defaultPickupDelay = 10;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "Settings for dropped item entity behavior.",
            "掉落物实体行为设置.");

        defaultPickupDelay = config.getInt(getBasePath() + ".default-pickup-delay", defaultPickupDelay,
            config.pickStringRegionBased(
                "Default delay (in ticks) before a newly dropped item can be picked up. " +
                "Set to 0 for instant pickup. Vanilla: 10. " +
                "Note: items thrown by players use a fixed 40-tick delay regardless of this setting.",
                "新掉落物品可被拾取前的默认延迟(刻). " +
                "设为0可立即拾取. 原版: 10. " +
                "注意: 玩家扔出的物品无论此设置如何均使用固定的40刻延迟."
            ));
    }
}
