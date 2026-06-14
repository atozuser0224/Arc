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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
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
        new ArcClientNetwork(runtime).register();
        Registry.register(
            Registries.ITEM_GROUP,
            CONTENT_GROUP_ID,
            FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.NETHER_STAR))
                .displayName(Text.translatable("itemGroup.arc.content"))
                .entries((context, entries) -> appendEntries(entries))
                .build()
        );
    }

    public static ArcClientRuntime runtime() {
        if (runtime == null) {
            throw new IllegalStateException("Arc client has not initialized");
        }
        return runtime;
    }

    private static void appendEntries(ItemGroup.Entries entries) {
        if (runtime == null || !runtime.active || runtime.getCatalog() == null) {
            return;
        }
        for (ClientCreativeEntry entry : runtime.getCatalog().getEntries()) {
            Identifier fallback = Identifier.tryParse(entry.getFallback());
            if (fallback == null) {
                continue;
            }
            Item item = Registries.ITEM.get(fallback);
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                Identifier contentId = Identifier.tryParse(entry.getId());
                if (contentId == null) {
                    continue;
                }
                stack.set(DataComponentTypes.ITEM_MODEL, contentId);
                stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.getId()));
                NbtCompound identity = new NbtCompound();
                identity.putString("arc:id", entry.getId());
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(identity));
                entries.add(stack);
            }
        }
    }
}
