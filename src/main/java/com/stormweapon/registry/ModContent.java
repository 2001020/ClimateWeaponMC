package com.stormweapon.registry;

import com.stormweapon.StormWeaponMod;
import com.stormweapon.block.WeatherLauncherBlock;
import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.entity.MeteorEntity;
import com.stormweapon.entity.WeatherMissileEntity;
import com.stormweapon.item.BlizzardMissileItem;
import com.stormweapon.item.CherryMissileItem;
import com.stormweapon.item.FogMissileItem;
import com.stormweapon.item.MeteorMissileItem;
import com.stormweapon.item.SignalConnectorItem;
import com.stormweapon.item.StormControllerItem;
import com.stormweapon.item.WeatherMissileItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

/** All common game content. Kept in one registry owner so the 26.2 registration order is explicit. */
public final class ModContent {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StormWeaponMod.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StormWeaponMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StormWeaponMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StormWeaponMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StormWeaponMod.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, StormWeaponMod.MOD_ID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, StormWeaponMod.MOD_ID);

    /**
     * Vanilla-style status effects that make the blizzard/fog debuffs and the cherry blossom buff
     * show up in the player's effect HUD and inventory screen exactly like a potion effect, instead
     * of being an invisible attribute change. The amplifier directly encodes the manager's own 1-99
     * percent/heart level (amplifier = level - 1), so {@code MobEffect}'s built-in
     * {@code amount * (amplifier + 1)} scaling reproduces the same magnitude the managers already
     * compute.
     */
    public static final RegistryObject<MobEffect> BLIZZARD_CHILL = MOB_EFFECTS.register(
        "blizzard_chill", () -> new MobEffect(MobEffectCategory.HARMFUL, 0xAFD8E8) {}
            .addAttributeModifier(Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "effect.blizzard_chill"),
                -0.01D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
    );
    public static final RegistryObject<MobEffect> BLIZZARD_FRAILTY = MOB_EFFECTS.register(
        "blizzard_frailty", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6E93B5) {}
            .addAttributeModifier(Attributes.MAX_HEALTH,
                Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "effect.blizzard_frailty"),
                -2.0D, AttributeModifier.Operation.ADD_VALUE)
    );
    public static final RegistryObject<MobEffect> FOG_CHILL = MOB_EFFECTS.register(
        "fog_chill", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x8C8C8C) {}
            .addAttributeModifier(Attributes.MOVEMENT_SPEED,
                Identifier.fromNamespaceAndPath(StormWeaponMod.MOD_ID, "effect.fog_chill"),
                -0.20D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
    );
    /** Purely a HUD marker: the cherry deployment's heal/cleanse is applied directly by CherryBlossomManager. */
    public static final RegistryObject<MobEffect> CHERRY_BLOSSOM_BLESSING = MOB_EFFECTS.register(
        "cherry_blossom_blessing", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xF4A8CE) {}
    );

    /**
     * First endpoint a player picked with the signal connector, held on the connector's own
     * ItemStack while the pairing is half-finished. Kept as an item component rather than a
     * server-side per-player map so it both persists (the half-made selection survives a relog)
     * and reaches the client automatically, which is what lets the client highlight the pending
     * endpoint without any extra packet of its own.
     */
    public static final RegistryObject<DataComponentType<BlockPos>> LINK_ANCHOR = DATA_COMPONENTS.register(
        "link_anchor", () -> DataComponentType.<BlockPos>builder()
            .persistent(BlockPos.CODEC)
            .networkSynchronized(BlockPos.STREAM_CODEC)
            .build()
    );

    public static final RegistryObject<WeatherLauncherBlock> WEATHER_MISSILE_LAUNCHER = BLOCKS.register(
        "weather_missile_launcher", () -> new WeatherLauncherBlock(BlockBehaviour.Properties.of()
            .strength(4.0F, 12.0F).noOcclusion().setId(BLOCKS.key("weather_missile_launcher")))
    );
    public static final RegistryObject<Item> WEATHER_MISSILE_LAUNCHER_ITEM = ITEMS.register(
        "weather_missile_launcher", () -> new BlockItem(WEATHER_MISSILE_LAUNCHER.get(), new Item.Properties()
            .setId(ITEMS.key("weather_missile_launcher")))
    );
    public static final RegistryObject<Item> WEATHER_MISSILE = ITEMS.register(
        "weather_missile", () -> new WeatherMissileItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("weather_missile")))
    );
    public static final RegistryObject<Item> STORM_CONTROLLER = ITEMS.register(
        "storm_controller", () -> new StormControllerItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("storm_controller")))
    );
    public static final RegistryObject<Item> FOG_MISSILE = ITEMS.register(
        "fog_missile", () -> new FogMissileItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("fog_missile")))
    );
    public static final RegistryObject<Item> METEOR_MISSILE = ITEMS.register(
        "meteor_missile", () -> new MeteorMissileItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("meteor_missile")))
    );
    public static final RegistryObject<Item> BLIZZARD_MISSILE = ITEMS.register(
        "blizzard_missile", () -> new BlizzardMissileItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("blizzard_missile")))
    );
    public static final RegistryObject<Item> CHERRY_MISSILE = ITEMS.register(
        "cherry_missile", () -> new CherryMissileItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("cherry_missile")))
    );
    public static final RegistryObject<Item> SIGNAL_CONNECTOR = ITEMS.register(
        "signal_connector", () -> new SignalConnectorItem(new Item.Properties().stacksTo(1)
            .setId(ITEMS.key("signal_connector")))
    );

    public static final RegistryObject<EntityType<WeatherMissileEntity>> WEATHER_MISSILE_ENTITY = ENTITY_TYPES.register(
        "weather_missile", () -> EntityType.Builder.of(WeatherMissileEntity::new, MobCategory.MISC)
            .sized(0.9F, 6.8F)
            // A launch can target coordinates well beyond the previous 16 chunks (256 blocks): once
            // the missile passed that distance from the viewing player, the server stopped syncing
            // it and it vanished mid-flight, exhaust and all. 48 (768 blocks) comfortably covers the
            // missile's full climb-and-cruise envelope.
            .clientTrackingRange(48)
            .updateInterval(1)
            .build(ENTITY_TYPES.key("weather_missile"))
    );
    public static final RegistryObject<EntityType<MeteorEntity>> METEOR_ENTITY = ENTITY_TYPES.register(
        "meteor", () -> EntityType.Builder.of(MeteorEntity::new, MobCategory.MISC)
            .sized(1.7F, 1.7F)
            // Meteors spawn ~90 blocks up and are meant to be watched all the way down, so the
            // tracking range has to cover that descent plus the horizontal scatter around it.
            .clientTrackingRange(12)
            .updateInterval(1)
            .build(ENTITY_TYPES.key("meteor"))
    );
    public static final RegistryObject<BlockEntityType<WeatherLauncherBlockEntity>> WEATHER_LAUNCHER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
        "weather_missile_launcher", () -> new BlockEntityType<>(WeatherLauncherBlockEntity::new, Set.of(WEATHER_MISSILE_LAUNCHER.get()))
    );

    /** A single dedicated tab for every Storm Weapon item, instead of scattering them across vanilla tabs. */
    public static final RegistryObject<CreativeModeTab> STORM_WEAPON_TAB = CREATIVE_MODE_TABS.register(
        "main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.stormweapon.main"))
            .icon(() -> new ItemStack(WEATHER_MISSILE_LAUNCHER_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(WEATHER_MISSILE_LAUNCHER_ITEM.get());
                output.accept(WEATHER_MISSILE.get());
                output.accept(FOG_MISSILE.get());
                output.accept(METEOR_MISSILE.get());
                output.accept(BLIZZARD_MISSILE.get());
                output.accept(CHERRY_MISSILE.get());
                output.accept(SIGNAL_CONNECTOR.get());
                output.accept(STORM_CONTROLLER.get());
            })
            .build()
    );

    private ModContent() {}

    public static void register(net.minecraftforge.eventbus.api.bus.BusGroup bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITY_TYPES.register(bus);
        BLOCK_ENTITY_TYPES.register(bus);
        CREATIVE_MODE_TABS.register(bus);
        DATA_COMPONENTS.register(bus);
        MOB_EFFECTS.register(bus);
    }
}
