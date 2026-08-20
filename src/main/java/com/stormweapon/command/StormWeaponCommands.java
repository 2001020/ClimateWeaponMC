package com.stormweapon.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.stormweapon.blockentity.WeatherLauncherBlockEntity;
import com.stormweapon.entity.WeatherMissileEntity;
import com.stormweapon.network.StormNetwork;
import com.stormweapon.storm.MissileKind;
import com.stormweapon.storm.StormPhase;
import com.stormweapon.storm.StormSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Arrays;

public final class StormWeaponCommands {
    private static boolean registered;

    private StormWeaponCommands() {}

    public static synchronized void registerEvents() {
        if (registered) {
            return;
        }
        RegisterCommandsEvent.BUS.addListener(StormWeaponCommands::register);
        registered = true;
    }

    private static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("climateweapon")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("storm")
                    .then(Commands.literal("start")
                        .executes(context -> startAtSource(context.getSource()))
                        .then(Commands.argument("center", Vec2Argument.vec2())
                            .executes(context -> {
                                Vec2 center = Vec2Argument.getVec2(context, "center");
                                return start(context.getSource(), center.x, center.y);
                            })))
                    .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                    .then(Commands.literal("phase")
                        .then(Commands.argument("phase", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(StormPhase.values()).map(phase -> phase.name().toLowerCase()), builder
                            ))
                            .executes(context -> forcePhase(
                                context.getSource(), StringArgumentType.getString(context, "phase")
                            )))))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("launcher")
                    .then(Commands.literal("preset")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, 3))
                            .then(Commands.argument("center", Vec2Argument.vec2())
                                .executes(context -> {
                                    Vec2 center = Vec2Argument.getVec2(context, "center");
                                    return setNearestLauncherPreset(context.getSource(), IntegerArgumentType.getInteger(context, "slot"), (int) center.x, (int) center.y);
                                })))))
                .then(Commands.literal("missile")
                    .then(Commands.literal("launch")
                        .then(Commands.argument("center", Vec2Argument.vec2())
                            .executes(context -> {
                                Vec2 center = Vec2Argument.getVec2(context, "center");
                                return debugLaunch(context.getSource(), (int) center.x, (int) center.y, false);
                            })))
                    .then(Commands.literal("launchfog")
                        .then(Commands.argument("center", Vec2Argument.vec2())
                            .executes(context -> {
                                Vec2 center = Vec2Argument.getVec2(context, "center");
                                return debugLaunch(context.getSource(), (int) center.x, (int) center.y, true);
                            })))
                    .then(Commands.literal("launchmeteor")
                        .then(Commands.argument("center", Vec2Argument.vec2())
                            .executes(context -> {
                                Vec2 center = Vec2Argument.getVec2(context, "center");
                                return debugLaunch(context.getSource(), (int) center.x, (int) center.y, MissileKind.METEOR);
                            })))
                    .then(Commands.literal("launchblizzard")
                        .then(Commands.argument("center", Vec2Argument.vec2())
                            .executes(context -> {
                                Vec2 center = Vec2Argument.getVec2(context, "center");
                                return debugLaunch(context.getSource(), (int) center.x, (int) center.y, MissileKind.BLIZZARD);
                            })))
                    .then(Commands.literal("launchcherry")
                        .then(Commands.argument("center", Vec2Argument.vec2())
                            .executes(context -> {
                                Vec2 center = Vec2Argument.getVec2(context, "center");
                                return debugLaunch(context.getSource(), (int) center.x, (int) center.y, MissileKind.CHERRY);
                            }))))
                .then(Commands.literal("debug").executes(context -> toggleDebug(context.getSource())))
        );
    }

    private static int startAtSource(CommandSourceStack source) {
        return start(source, source.getPosition().x, source.getPosition().z);
    }

    private static int start(CommandSourceStack source, double x, double z) {
        ServerLevel level = source.getLevel();
        StormSavedData data = StormSavedData.get(level);
        data.start(level, x, z);
        StormNetwork.syncLevel(level);
        source.sendSuccess(() -> Component.translatable("command.stormweapon.started", (int)x, (int)z), true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        StormSavedData.get(level).stop(level);
        StormNetwork.syncLevel(level);
        source.sendSuccess(() -> Component.translatable("command.stormweapon.stopped"), true);
        return 1;
    }

    private static int forcePhase(CommandSourceStack source, String phaseName) {
        final StormPhase phase;
        try {
            phase = StormPhase.parse(phaseName);
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.translatable("command.stormweapon.invalid_phase", phaseName));
            return 0;
        }
        ServerLevel level = source.getLevel();
        StormSavedData.get(level).forcePhase(level, phase);
        StormNetwork.syncLevel(level);
        source.sendSuccess(() -> Component.translatable("command.stormweapon.phase", phase.name()), true);
        return 1;
    }

    private static int toggleDebug(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        boolean enabled = StormSavedData.get(level).toggleDebug();
        StormNetwork.syncLevel(level);
        source.sendSuccess(() -> Component.translatable(enabled ? "command.stormweapon.debug_on" : "command.stormweapon.debug_off"), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        StormSavedData.StormStatus status = StormSavedData.get(level).status(level);
        if (!status.snapshot().active()) {
            source.sendSuccess(() -> Component.translatable("command.stormweapon.status.clear"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable(
            "command.stormweapon.status.active",
            status.snapshot().phase().name(),
            Math.round(status.snapshot().centerX()), Math.round(status.snapshot().centerZ()),
            String.format("%.1f", status.phaseRemainingTicks() / 20.0D),
            String.format("%.0f%%", status.snapshot().waveProgress() * 100.0F)
        ), false);
        return 1;
    }

    private static int setNearestLauncherPreset(CommandSourceStack source, int slot, int x, int z) {
        WeatherLauncherBlockEntity launcher = findNearestLauncher(source.getLevel(), source.getPosition());
        if (launcher == null) {
            source.sendFailure(Component.translatable("command.stormweapon.launcher_not_found"));
            return 0;
        }
        launcher.setPreset(slot, x, z);
        source.sendSuccess(() -> Component.translatable("command.stormweapon.preset_set", slot, x, z), true);
        return 1;
    }

    private static int debugLaunch(CommandSourceStack source, int x, int z, boolean fog) {
        return debugLaunch(source, x, z, fog ? MissileKind.FOG : MissileKind.THUNDER);
    }

    private static int debugLaunch(CommandSourceStack source, int x, int z, MissileKind kind) {
        Vec3 position = source.getPosition().add(0.0D, 1.0D, 0.0D);
        WeatherMissileEntity.launch(source.getLevel(), position, source.getRotation().y, x, z, source.getLevel().getRandom().nextLong(), kind);
        source.sendSuccess(() -> Component.translatable(switch (kind) {
            case FOG -> "command.stormweapon.fog_missile_launched";
            case METEOR -> "command.stormweapon.meteor_missile_launched";
            case BLIZZARD -> "command.stormweapon.blizzard_missile_launched";
            case CHERRY -> "command.stormweapon.cherry_missile_launched";
            case THUNDER -> "command.stormweapon.missile_launched";
        }, x, z), true);
        return 1;
    }

    /** Command-only bounded scan; normal ticking never iterates a storm-sized area. */
    private static WeatherLauncherBlockEntity findNearestLauncher(ServerLevel level, Vec3 source) {
        BlockPos origin = BlockPos.containing(source);
        WeatherLauncherBlockEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -16; dz <= 16; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (level.getBlockEntity(candidate) instanceof WeatherLauncherBlockEntity launcher) {
                        double distance = candidate.distToCenterSqr(source.x, source.y, source.z);
                        if (distance < nearestDistance) {
                            nearest = launcher;
                            nearestDistance = distance;
                        }
                    }
                }
            }
        }
        return nearest;
    }
}
