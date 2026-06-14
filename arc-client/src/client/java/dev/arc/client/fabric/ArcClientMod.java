package dev.arc.client.fabric;

import dev.arc.client.ArcClientRuntime;
import dev.arc.client.ClientCreativeEntry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ArcClientMod implements ClientModInitializer {
    private static final Identifier CONTENT_GROUP_ID = Identifier.of("arc", "content");
    private static ArcClientRuntime runtime;

    @Override
    public void onInitializeClient() {
        runtime = new ArcClientRuntime(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("arc")
        );
        Registry.register(
            Registries.ITEM_GROUP,
            CONTENT_GROUP_ID,
            FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.NETHER_STAR))
                .displayName(Text.translatable("itemGroup.arc.content"))
                .entries((context, entries) -> appendEntries(entries))
                .build()
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.disconnect());
    }

    public static ArcClientRuntime runtime() {
        if (runtime == null) {
            throw new IllegalStateException("Arc client has not initialized");
        }
        return runtime;
    }

    private static void appendEntries(ItemGroup.Entries entries) {
        if (runtime == null || runtime.getCatalog() == null) {
            return;
        }
        for (ClientCreativeEntry entry : runtime.getCatalog().getEntries()) {
            Identifier fallback = Identifier.tryParse(entry.getFallback());
            if (fallback == null) {
                continue;
            }
            Item item = Registries.ITEM.get(fallback);
            if (item != Items.AIR) {
                entries.add(new ItemStack(item));
            }
        }
    }
}
