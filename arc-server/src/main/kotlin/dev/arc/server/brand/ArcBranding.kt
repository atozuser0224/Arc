package dev.arc.server.brand

/**
 * User-facing Arc Bucket product identity.
 *
 * Compatibility package names and legacy keys deliberately remain separate.
 */
public object ArcBranding {
    public const val NAME: String = "Arc Bucket"
    public const val BRAND_ID: String = "arc:arc-bucket"
    public const val VENDOR: String = "Arc Project"

    public fun startupBanner(version: String, minecraftVersion: String): List<String> = listOf(
        "    ___    ____  ______",
        "   /   |  / __ \\/ ____/",
        "  / /| | / /_/ / /",
        " / ___ |/ _, _/ /___",
        "/_/  |_/_/ |_|\\____/",
        "       A R C   B U C K E T",
        "  $version | Minecraft $minecraftVersion",
    )
}
