package io.wispforest.owowhatsthis;

import io.wispforest.owo.registration.reflect.FieldRegistrationHandler;
import io.wispforest.owo.text.CustomTextRegistry;
import io.wispforest.owo.util.OwoFreezer;
import io.wispforest.owowhatsthis.compat.OwoWhatsThisPlugin;
import io.wispforest.owowhatsthis.information.InformationProvider;
import io.wispforest.owowhatsthis.information.InformationProviders;
import io.wispforest.owowhatsthis.information.TargetType;
import io.wispforest.owowhatsthis.network.OwoWhatsThisNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class OwoWhatsThis implements ModInitializer {

    public static final String MOD_ID = "owo-whats-this";
    public static final io.wispforest.owowhatsthis.OwoWhatsThisConfig CONFIG = io.wispforest.owowhatsthis.OwoWhatsThisConfig.createAndLoad();

    public static final RegistryKey<Registry<TargetType<?>>> TARGET_TYPE_KEY = RegistryKey.ofRegistry(id("target_types"));
    public static final Registry<TargetType<?>> TARGET_TYPE =
            FabricRegistryBuilder.createSimple(TARGET_TYPE_KEY)
                    .attribute(RegistryAttribute.SYNCED)
                    .buildAndRegister();

    public static final RegistryKey<Registry<InformationProvider<?, ?>>> INFORMATION_PROVIDER_KEY = RegistryKey.ofRegistry(id("information_providers"));
    public static final Registry<InformationProvider<?, ?>> INFORMATION_PROVIDER =
            FabricRegistryBuilder.createSimple(INFORMATION_PROVIDER_KEY)
                    .attribute(RegistryAttribute.SYNCED)
                    .buildAndRegister();

    private static final Map<Identifier, Text> EFFECTIVE_TOOL_TAGS = new HashMap<>();
    private static final Map<Identifier, Text> EFFECTIVE_TOOL_TAGS_VIEW = Collections.unmodifiableMap(EFFECTIVE_TOOL_TAGS);

    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    @Override
    public void onInitialize() {
        Registry.register(TARGET_TYPE, id("block"), TargetType.BLOCK);
        Registry.register(TARGET_TYPE, id("entity"), TargetType.ENTITY);
        Registry.register(TARGET_TYPE, id("player"), TargetType.PLAYER);
        Registry.register(TARGET_TYPE, id("fluid"), TargetType.FLUID);

        FieldRegistrationHandler.register(InformationProviders.class, MOD_ID, false);

        for (var entrypoint : FabricLoader.getInstance().getEntrypoints("owo-whats-this-plugin", OwoWhatsThisPlugin.class)) {
            if (!entrypoint.shouldLoad()) continue;
            entrypoint.loadServer();
        }

        OwoWhatsThisNetworking.initialize();
        OwoFreezer.registerFreezeCallback(TooltipObjectManager::updateAndSort);
        CONFIG.subscribeToDisabledProviders(strings -> TooltipObjectManager.updateAndSort());

        cacheEffectiveToolTags();
        CONFIG.subscribeToEffectiveToolTags(strings -> cacheEffectiveToolTags());

        CustomTextRegistry.register(QuantityTextContent.TYPE, "quantity");
    }

    @ApiStatus.Internal
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static String modNameOf(Identifier id) {
        return FabricLoader.getInstance()
                .getModContainer(id.getNamespace())
                .map(ModContainer::getMetadata)
                .map(ModMetadata::getName)
                .orElse(id.getNamespace());
    }

    public static Map<Identifier, Text> effectiveToolTags() {
        return EFFECTIVE_TOOL_TAGS_VIEW;
    }

    private static void cacheEffectiveToolTags() {
        EFFECTIVE_TOOL_TAGS.clear();
        CONFIG.effectiveToolTags().forEach(s -> {
            var splitName = s.split("/");
            EFFECTIVE_TOOL_TAGS.put(
                    Identifier.of(s),
                    Text.translatable("text.owo-whats-this.toolType." + splitName[splitName.length - 1])
            );
        });
    }

    public static HitResult raycast(Entity entity, float tickDelta) {
        var blockTarget = entity.raycast(5, tickDelta, OwoWhatsThis.CONFIG.showFluids());

        var maxReach = entity.getRotationVec(tickDelta).multiply(5);
        var entityTarget = ProjectileUtil.raycast(
                entity,
                entity.getEyePos(),
                entity.getEyePos().add(maxReach),
                entity.getBoundingBox().stretch(maxReach),
                candidate -> true,
                5 * 5
        );

        return entityTarget != null && entityTarget.squaredDistanceTo(entity) < blockTarget.squaredDistanceTo(entity)
                ? entityTarget
                : blockTarget;
    }
}

