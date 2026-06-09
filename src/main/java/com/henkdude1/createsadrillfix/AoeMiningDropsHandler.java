package com.henkdude1.createsadrillfix;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Fixes AOE mining enchantments (Digging / Chunk Eater from Create: Stuff 'N Additions)
 * not applying the tool's other enchantments to the extra blocks they break.
 *
 * <p>How it works:
 * <ol>
 * <li>{@link BlockEvent.BreakEvent} fires when a player starts breaking a block. If the
 * held tool has one of the configured trigger enchantments, a short-lived
 * {@link MiningContext} (player, position, tool snapshot) is recorded. This event fires
 * <em>before</em> the AOE enchantment code runs.</li>
 * <li>The enchantment then breaks the surrounding blocks via {@code Level.destroyBlock},
 * which drops loot with an empty tool — that is why Silk Touch/Fortune are lost.
 * Each of those drops fires {@link BlockDropsEvent} with an empty tool.</li>
 * <li>For every tool-less drop close (in space and time) to an active context, the drops
 * are recomputed from the block's loot table using the recorded tool, and the dropped
 * experience is recomputed the same way, so all enchantments apply.</li>
 * </ol>
 *
 * <p>The block the player mined directly is untouched: its {@link BlockDropsEvent}
 * carries the real tool, so vanilla already handles it correctly.
 */
@EventBusSubscriber(modid = CreateSaDrillFix.MOD_ID)
public final class AoeMiningDropsHandler {

    private record MiningContext(ServerLevel level, BlockPos pos, long gameTime, ServerPlayer player, ItemStack tool) {}

    private static final List<MiningContext> ACTIVE_CONTEXTS = new ArrayList<>();

    private AoeMiningDropsHandler() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || !hasTriggerEnchantment(held)) {
            return;
        }
        purgeExpired(level.getGameTime());
        ACTIVE_CONTEXTS.add(new MiningContext(level, event.getPos().immutable(), level.getGameTime(), player, held.copy()));
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (ACTIVE_CONTEXTS.isEmpty()) {
            return;
        }
        // Drops created with a real tool (e.g. the block the player mined directly)
        // already have enchantments applied by vanilla.
        if (!event.getTool().isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel();
        purgeExpired(level.getGameTime());
        MiningContext context = findContext(level, event.getPos(), event.getBreaker());
        if (context == null) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        ItemStack tool = context.tool();

        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, tool)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, event.getBlockEntity())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, context.player());
        List<ItemStack> drops = state.getDrops(params);

        event.getDrops().clear();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            itemEntity.setDefaultPickUpDelay();
            event.getDrops().add(itemEntity);
        }

        int xp = state.getExpDrop(level, pos, event.getBlockEntity(), context.player(), tool);
        event.setDroppedExperience(EnchantmentHelper.processBlockExperience(level, tool, xp));
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!ACTIVE_CONTEXTS.isEmpty()) {
            purgeExpired(event.getServer().overworld().getGameTime());
        }
    }

    private static MiningContext findContext(ServerLevel level, BlockPos pos, net.minecraft.world.entity.Entity breaker) {
        int maxDistance = Config.MAX_BLOCK_DISTANCE.get();
        double maxDistanceSqr = (double) maxDistance * maxDistance;
        MiningContext best = null;
        double bestDistanceSqr = Double.MAX_VALUE;
        for (MiningContext context : ACTIVE_CONTEXTS) {
            if (context.level() != level) {
                continue;
            }
            // Some code paths pass the breaking entity along; if so it must be our player.
            if (breaker != null && breaker != context.player()) {
                continue;
            }
            double distanceSqr = context.pos().distSqr(pos);
            if (distanceSqr <= maxDistanceSqr && distanceSqr < bestDistanceSqr) {
                best = context;
                bestDistanceSqr = distanceSqr;
            }
        }
        return best;
    }

    private static boolean hasTriggerEnchantment(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return false;
        }
        for (String id : Config.TRIGGER_ENCHANTMENTS.get()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                continue;
            }
            for (var entry : enchantments.entrySet()) {
                if (entry.getKey().is(location)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void purgeExpired(long gameTime) {
        int lifetime = Config.CONTEXT_LIFETIME_TICKS.get();
        ACTIVE_CONTEXTS.removeIf(context -> gameTime - context.gameTime() > lifetime
                || context.gameTime() > gameTime
                || context.player().isRemoved());
    }
}
