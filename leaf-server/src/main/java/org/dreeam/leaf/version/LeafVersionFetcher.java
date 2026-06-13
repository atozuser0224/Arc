package org.dreeam.leaf.version;

import org.galemc.gale.version.AbstractPaperVersionFetcher;

public class LeafVersionFetcher extends AbstractPaperVersionFetcher {

    public LeafVersionFetcher() {
        super(
            "https://github.com/atozuser0224/Arc/releases",
            "Arc Project",
            "Arc Bucket",
            "atozuser0224",
            "Arc"
        );
    }
}
