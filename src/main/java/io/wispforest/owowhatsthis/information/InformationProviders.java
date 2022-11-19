package io.wispforest.owowhatsthis.information;

import io.wispforest.owo.network.serialization.PacketBufSerializer;
import io.wispforest.owo.ops.TextOps;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.core.*;
import io.wispforest.owo.util.RegistryAccess;
import io.wispforest.owowhatsthis.FluidToVariant;
import io.wispforest.owowhatsthis.OwoWhatsThis;
import io.wispforest.owowhatsthis.client.component.ColoringComponent;
import io.wispforest.owowhatsthis.client.component.HeartSpriteComponent;
import io.wispforest.owowhatsthis.client.component.ProgressBarComponent;
import io.wispforest.owowhatsthis.mixin.ClientPlayerInteractionManagerAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registries;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class InformationProviders {

    public static final InformationProvider<BlockPos, Text> BLOCK_HARDNESS = new InformationProvider<>(
            TargetType.BLOCK,
            (player, world, blockPos) -> Text.translatable("text.owo-whats-this.tooltip.blockHardness", world.getBlockState(blockPos).getHardness(world, blockPos)),
            Text.class, false, true, 0
    );

    public static final InformationProvider<BlockPos, Text> BLOCK_HARVESTABILITY = new InformationProvider<>(
            TargetType.BLOCK,
            (player, world, target) -> {
                var state = world.getBlockState(target);
                var harvestable = !state.isToolRequired() || player.getMainHandStack().isSuitableFor(state);

                var effectiveTools = RegistryAccess.getEntry(Registries.BLOCK, state.getBlock()).streamTags()
                        .filter(blockTagKey -> OwoWhatsThis.effectiveToolTags().containsKey(blockTagKey.id()))
                        .map(blockTagKey -> OwoWhatsThis.effectiveToolTags().get(blockTagKey.id()))
                        .reduce((mutableText, text) -> TextOps.concat(mutableText, Text.of(", ")).append(text));

                return effectiveTools.map(tools -> Text.translatable("text.owo-whats-this.tooltip.tools", tools)).orElse(Text.translatable("text.owo-whats-this.tooltip.noTools"))
                        .append("\n")
                        .append(Text.translatable(harvestable ? "text.owo-whats-this.tooltip.harvestable" : "text.owo-whats-this.tooltip.not_harvestable"));
            },
            Text.class, false, true, 0
    );

    public static final InformationProvider<BlockPos, Float> BLOCK_BREAKING_PROGRESS = new InformationProvider<>(
            TargetType.BLOCK,
            (player, world, target) -> {
                float progress = ((ClientPlayerInteractionManagerAccessor) MinecraftClient.getInstance().interactionManager).whatsthis$getCurrentBreakingProgress();
                return progress > 0 ? progress : null;
            },
            Float.class, false, true, -6900
    );

    @SuppressWarnings("unchecked")
    public static final InformationProvider<BlockPos, List<ItemStack>> BLOCK_INVENTORY = new InformationProvider<>(
            TargetType.BLOCK,
            (player, world, blockPos) -> {
                var storage = ItemStorage.SIDED.find(world, blockPos, null);
                if (storage == null) return null;

                var items = new ArrayList<ItemStack>();
                storage.forEach(variant -> {
                    var stack = variant.getResource().toStack((int) variant.getAmount());
                    if (stack.isEmpty()) return;
                    items.add(stack);
                });

                return items.isEmpty() ? null : items;
            },
            (PacketBufSerializer<List<ItemStack>>) (Object) PacketBufSerializer.createCollectionSerializer(List.class, ItemStack.class),
            true, false, 0
    );

    @SuppressWarnings("unchecked")
    public static final InformationProvider<BlockPos, List<NbtCompound>> BLOCK_FLUID_STORAGE = new InformationProvider<>(
            TargetType.BLOCK,
            (player, world, blockPos) -> {
                var storage = FluidStorage.SIDED.find(world, blockPos, null);
                if (storage == null) return null;

                var fluidData = new ArrayList<NbtCompound>();
                for (var entry : storage) {
                    if (entry.isResourceBlank()) continue;

                    var nbt = entry.getResource().toNbt();
                    nbt.putLong("owo-whats-this:amount", entry.getAmount());
                    nbt.putLong("owo-whats-this:capacity", entry.getCapacity());
                    fluidData.add(nbt);
                }

                return fluidData.isEmpty() ? null : fluidData;
            },
            (PacketBufSerializer<List<NbtCompound>>) (Object) PacketBufSerializer.createCollectionSerializer(List.class, NbtCompound.class),
            true, false, 0
    );

    public static final InformationProvider<BlockPos, Text> FLUID_VISCOSITY = new InformationProvider<>(
            TargetType.FLUID,
            (player, world, target) -> {
                return Text.translatable("text.owo-whats-this.tooltip.fluidViscosity", FluidVariantAttributes.getViscosity(FluidToVariant.apply(world.getFluidState(target).getFluid()), world));
            },
            Text.class, false, true, 0
    );

    public static final InformationProvider<Entity, Float> ENTITY_HEALTH = new InformationProvider<>(
            TargetType.ENTITY,
            (player, world, entity) -> (entity instanceof LivingEntity living)
                    ? living.getHealth()
                    : null,
            Float.class, true, false, 0
    );

    public static final InformationProvider<Entity, Text> ENTITY_STATUS_EFFECTS = new InformationProvider<>(
            TargetType.ENTITY,
            (player, world, entity) -> {
                if (!(entity instanceof LivingEntity living)) return null;

                var effects = living.getStatusEffects();
                if (effects.isEmpty()) return null;

                var effectTexts = new ArrayList<Text>();
                for (var effect : effects) {
                    effectTexts.add(Text.translatable("text.owo-whats-this.tooltip.status_effect", Text.translatable(effect.getTranslationKey()), StatusEffectUtil.durationToString(effect, 1)));
                }

                var display = Text.empty();
                for (int i = 0; i < effectTexts.size(); i++) {
                    display.append(effectTexts.get(i));
                    if (i < effectTexts.size() - 1) display.append("\n");
                }
                return display;
            },
            Text.class, true, false, 0
    );

    public static final InformationProvider<Entity, Integer> PLAYER_PING = new InformationProvider<>(
            TargetType.ENTITY,
            (player, world, target) -> {
                if (!(target instanceof OtherClientPlayerEntity otherPlayer)) return null;

                var entry = MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(otherPlayer.getUuid());
                if (entry == null) return null;

                return entry.getLatency();
            },
            Integer.class, true, true, 0
    );

    @Environment(EnvType.CLIENT)
    public static class DisplayAdapters {

        public static final InformationProvider.DisplayAdapter<Text> TEXT = data -> {
            return Components.label(data).shadow(true);
        };

        public static final InformationProvider.DisplayAdapter<Float> ENTITY_HEALTH = data -> {
            if (data < 30) {
                return Containers.horizontalFlow(Sizing.content(), Sizing.content()).<FlowLayout>configure(flowLayout -> {
                    flowLayout.gap(-1);
                    for (int i = 0; i < Math.floor(data / 2); i++) {
                        flowLayout.child(new HeartSpriteComponent(1f));
                    }

                    if (data % 2f > 0.05) {
                        flowLayout.child(new HeartSpriteComponent(data * .5f % 1f));
                    }
                });
            } else {
                return Containers.horizontalFlow(Sizing.content(), Sizing.content()).<FlowLayout>configure(flowLayout -> {
                    flowLayout.gap(2);
                    flowLayout.child(
                            Components.label(Text.literal(Math.round(data / 2f) + "x"))
                    ).child(
                            new HeartSpriteComponent(1)
                    );
                });
            }
        };

        public static final InformationProvider.DisplayAdapter<Integer> PLAYER_PING = data -> {
            int pingStep;

            if (data < 0) {
                pingStep = 5;
            } else if (data < 150) {
                pingStep = 0;
            } else if (data < 300) {
                pingStep = 1;
            } else if (data < 600) {
                pingStep = 2;
            } else if (data < 1000) {
                pingStep = 3;
            } else {
                pingStep = 4;
            }

            return Components.texture(
                    InGameHud.GUI_ICONS_TEXTURE,
                    0, 176 + pingStep * 8, 10, 8
            );
        };

        public static final InformationProvider.DisplayAdapter<List<NbtCompound>> FLUID_STORAGE_LIST = data -> {
            return Containers.verticalFlow(Sizing.content(), Sizing.content()).<FlowLayout>configure(layout -> {
                layout.gap(2);
                for (var fluidNbt : data) {
                    var variant = FluidVariant.fromNbt(fluidNbt);

                    var sprite = FluidVariantRendering.getSprite(variant);
                    int color = FluidVariantRendering.getColor(variant);

                    long amount = fluidNbt.getLong("owo-whats-this:amount");
                    long capacity = fluidNbt.getLong("owo-whats-this:capacity");

                    layout.child(
                            Containers.horizontalFlow(Sizing.fixed(110), Sizing.fixed(12)).<FlowLayout>configure(spriteContainer -> {
                                spriteContainer.padding(Insets.of(1)).surface(Surface.outline(0xA7000000));

                                int width = Math.round(110 * (amount / (float) capacity));
                                while (width > 0) {
                                    spriteContainer.child(
                                            new ColoringComponent<>(
                                                    Color.ofRgb(color),
                                                    Components.sprite(sprite).horizontalSizing(Sizing.fixed(Math.min(sprite.getContents().getWidth(), width)))
                                            )
                                    );
                                    width -= sprite.getContents().getWidth();
                                }

                                spriteContainer.child(
                                        Components.label(
                                                Text.translatable("text.owo-whats-this.tooltip.blockFluidAmount", FluidVariantAttributes.getName(variant), amount / 81, capacity / 81)
                                        ).positioning(Positioning.relative(0, 50)).margins(Insets.left(5))
                                );
                            })
                    );
                }
            });
        };

        public static final InformationProvider.DisplayAdapter<Float> BREAKING_PROGRESS = data -> {
            return new ProgressBarComponent(Sizing.fixed(110), Sizing.fixed(2)).progress(data);
        };

        public static final InformationProvider.DisplayAdapter<List<ItemStack>> ITEM_STACK_LIST = data -> {
            int rows = MathHelper.ceilDiv(data.size(), 9);
            return Containers.grid(Sizing.content(), Sizing.content(), rows, Math.min(data.size(), 9)).<GridLayout>configure(layout -> {
                for (int i = 0; i < data.size(); i++) {
                    layout.child(
                            Components.item(data.get(i)).showOverlay(true),
                            i / 9, i % 9
                    );
                }
            });
        };

    }

}
