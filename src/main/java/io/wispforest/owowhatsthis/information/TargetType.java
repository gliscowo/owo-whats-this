package io.wispforest.owowhatsthis.information;

import io.wispforest.owo.network.ServerAccess;
import io.wispforest.owo.ops.TextOps;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owowhatsthis.OwoWhatsThis;
import io.wispforest.owowhatsthis.client.component.ColoringComponent;
import io.wispforest.owowhatsthis.client.component.OwoWhatsThisEntityComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public record TargetType<T>(BiFunction<World, HitResult, @Nullable T> transformer, BiConsumer<T, PacketByteBuf> serializer,
                            BiFunction<ServerAccess, PacketByteBuf, @Nullable T> deserializer, int priority, @Nullable TargetType<? super T> parent) {

    public TargetType {
        if (parent != null && parent.priority >= priority) {
            throw new IllegalArgumentException("Parent priority must be lower than own priority");
        }
    }

    public static final TargetType<BlockStateWithPosition> BLOCK = new TargetType<>(
            (world, hitResult) -> hitResult instanceof BlockHitResult blockHit
                    && blockHit.getType() == HitResult.Type.BLOCK
                    && !world.getBlockState(blockHit.getBlockPos()).isAir()
                    ? new BlockStateWithPosition(blockHit.getBlockPos(), world.getBlockState(blockHit.getBlockPos()))
                    : null,
            BlockStateWithPosition::write,
            BlockStateWithPosition::read,
            0, null
    );

    public static final TargetType<FluidStateWithPosition> FLUID = new TargetType<>(
            (world, hitResult) -> hitResult instanceof BlockHitResult blockHit
                    && blockHit.getType() == HitResult.Type.BLOCK
                    && world.getBlockState(blockHit.getBlockPos()).getBlock() instanceof FluidBlock
                    && !world.getFluidState(blockHit.getBlockPos()).isEmpty()
                    ? new FluidStateWithPosition(blockHit.getBlockPos(), world.getFluidState(blockHit.getBlockPos()))
                    : null,
            FluidStateWithPosition::write,
            FluidStateWithPosition::read,
            10, null
    );

    public static final TargetType<Entity> ENTITY = new TargetType<>(
            (world, hitResult) -> hitResult instanceof EntityHitResult entityHit ? fixEnderDragon(entityHit.getEntity()) : null,
            (entity, buf) -> buf.writeVarInt(entity.getId()),
            (access, buf) -> fixEnderDragon(access.player().getWorld().getEntityById(buf.readVarInt())),
            20, null
    );

    public static final TargetType<PlayerEntity> PLAYER = new TargetType<>(
            (world, hitResult) -> hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof PlayerEntity player ? player : null,
            (player, buf) -> buf.writeVarInt(player.getId()),
            (access, buf) -> (PlayerEntity) access.player().getWorld().getEntityById(buf.readVarInt()),
            30, ENTITY
    );

    private static Entity fixEnderDragon(Entity entity) {
        if (!(entity instanceof EnderDragonPart part)) return entity;
        return part.owner;
    }

    @Environment(EnvType.CLIENT)
    public interface DisplayAdapter<T> {

        DisplayAdapter<BlockStateWithPosition> BLOCK = target -> {
            var targetState = target.state();
            var previewItem = targetState.getBlock().getPickStack(MinecraftClient.getInstance().world, target.pos(), targetState);

            return new PreviewData(
                    Components.label(
                            TextOps.concat(
                                    targetState.getBlock().getName(),
                                    Text.literal("\n").append(
                                            TextOps.withFormatting(OwoWhatsThis.modNameOf(Registries.BLOCK.getId(targetState.getBlock())), Formatting.BLUE)
                                    )
                            )
                    ).shadow(true),
                    previewItem.isEmpty()
                            ? Containers.verticalFlow(Sizing.fixed(0), Sizing.fixed(0))
                            : Components.item(previewItem)
            );
        };

        @SuppressWarnings("UnstableApiUsage")
        DisplayAdapter<FluidStateWithPosition> FLUID = target -> {
            var fluidVariant = FluidVariant.of(target.state().getFluid());

            return new PreviewData(
                    Components.label(
                            TextOps.concat(
                                    FluidVariantAttributes.getName(fluidVariant),
                                    Text.literal("\n").append(
                                            TextOps.withFormatting(OwoWhatsThis.modNameOf(Registries.FLUID.getId(fluidVariant.getFluid())), Formatting.BLUE)
                                    )
                            )
                    ).shadow(true),
                    new ColoringComponent<>(
                            Color.ofArgb(FluidVariantRendering.getColor(fluidVariant)),
                            Components.sprite(FluidVariantRendering.getSprite(fluidVariant))
                    )
            );
        };

        DisplayAdapter<Entity> ENTITY = entity -> new PreviewData(
                Components.label(
                        TextOps.concat(
                                entity.getDisplayName(),
                                Text.literal("\n").append(
                                        TextOps.withFormatting(OwoWhatsThis.modNameOf(Registries.ENTITY_TYPE.getId(entity.getType())), Formatting.BLUE)
                                )
                        )
                ).shadow(true),
                new OwoWhatsThisEntityComponent<>(Sizing.fixed(24), entity).scaleToFit(true)
        );

        DisplayAdapter<PlayerEntity> PLAYER = player -> {
            int pingStatus = 1;

            var playerListEntry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (playerListEntry != null) {
                int latency = playerListEntry.getLatency();

                if (latency < 0) pingStatus = 0;
                else if (latency < 150) pingStatus = 5;
                else if (latency < 300) pingStatus = 4;
                else if (latency < 600) pingStatus = 3;
                else if (latency < 1000) pingStatus = 2;
            }

            var pingId = pingStatus == 0
                    ? Identifier.of("icon/ping_unknown")
                    : Identifier.of("icon/ping_" + pingStatus);

            var entityPreview = ENTITY.buildPreview(player);
            return new PreviewData(
                    Containers.horizontalFlow(Sizing.content(), Sizing.content())
                            .child(entityPreview.title())
                            .child(Components.sprite(
                                    MinecraftClient.getInstance().getGuiAtlasManager().getSprite(pingId)
                            )).gap(3),
                    entityPreview.preview()
            );
        };

        PreviewData buildPreview(T value);
    }

    @Environment(EnvType.CLIENT)
    public record PreviewData(Component title, Component preview) {}

}
