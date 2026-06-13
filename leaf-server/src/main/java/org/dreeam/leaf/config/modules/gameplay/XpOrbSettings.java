package org.dreeam.leaf.config.modules.gameplay;

import org.dreeam.leaf.config.ConfigModules;
import org.dreeam.leaf.config.EnumConfigCategory;

public class XpOrbSettings extends ConfigModules {

    public String getBasePath() {
        return EnumConfigCategory.GAMEPLAY.getBaseKeyName() + ".xp-orb-settings";
    }

    public static int lifetime = 6000;
    public static int scanInterval = 20;
    public static double followDistance = 8.0;

    @Override
    public void onLoaded() {
        config.addCommentRegionBased(getBasePath(),
            "Settings for experience orb behavior.",
            "经验球行为设置.");

        lifetime = config.getInt(getBasePath() + ".lifetime", lifetime,
            config.pickStringRegionBased(
                "How many ticks before an experience orb despawns. Vanilla default: 6000 (5 minutes).",
                "经验球消失前的刻数. 原版默认: 6000 (5分钟)."
            ));

        scanInterval = config.getInt(getBasePath() + ".scan-interval", scanInterval,
            config.pickStringRegionBased(
                "How often (in ticks) each xp orb scans for nearby orbs to merge with and players to follow. " +
                "Higher values reduce CPU cost at the cost of slightly delayed merging/following. Vanilla: 20.",
                "每隔多少刻经验球扫描附近可合并的经验球和跟随的玩家. " +
                "较高的值可减少CPU消耗, 代价是合并/跟随稍有延迟. 原版: 20."
            ));

        followDistance = config.getDouble(getBasePath() + ".follow-distance", followDistance,
            config.pickStringRegionBased(
                "Maximum distance (in blocks) at which xp orbs will follow a player. Vanilla: 8.0.",
                "经验球跟随玩家的最大距离(方块). 原版: 8.0."
            ));
    }
}
