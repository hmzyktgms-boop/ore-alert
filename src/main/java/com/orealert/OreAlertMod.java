package com.orealert;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class OreAlertMod implements ModInitializer {

    private static final ConcurrentMap<UUID, CopyOnWriteArrayList<Long>> breaks = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60L * 60L * 1000L;

    private static final Set<Block> TRACK_BLOCKS = new HashSet<>(Arrays.asList(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.NETHERITE_BLOCK
    ));

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((World world, ServerPlayerEntity player, BlockPos pos, net.minecraft.block.BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) -> {
            if (world.isClient()) return;

            Block broken = state.getBlock();
            if (!TRACK_BLOCKS.contains(broken)) return;

            UUID id = player.getUuid();
            long now = System.currentTimeMillis();
            breaks.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(now);

            List<Long> recent = breaks.get(id).stream().filter(t -> now - t <= WINDOW_MS).collect(Collectors.toList());
            breaks.put(id, new CopyOnWriteArrayList<>(recent));
            int count = recent.size();

            String blockName = broken.getName().getString();
            int y = pos.getY();

            String msg = "[OreAlert] " + player.getEntityName() + " broke " + blockName + " at Y=" + y + " — " + count + " in last hour";

            MinecraftServer server = player.getServer();
            if (server != null) {
                broadcastToOps(server, Text.of(msg));
                server.sendSystemMessage(Text.of(msg));
            }
        });
    }

    private static void broadcastToOps(MinecraftServer server, Text message) {
        PlayerManager pm = server.getPlayerManager();
        if (pm == null) return;

        for (ServerPlayerEntity p : pm.getPlayerList()) {
            boolean isOp = false;
            try { isOp = p.hasPermissionLevel(3); } catch (Throwable ignored) {}
            if (isOp) p.sendMessage(message, false);
        }
    }
}
