package dev.arc.api.sync

public object ArcSyncProtocol {
    public const val VERSION: Int = 1
    public const val CHANNEL: String = "arc:sync"
}

public enum class ArcSyncFeature {
    CREATIVE_TABS,
    CUSTOM_ITEMS,
    CUSTOM_BLOCKS,
    FURNITURE,
    DECLARATIVE_UI,
}
