package io.wispforest.owowhatsthis.client.component;

import io.wispforest.owo.ui.component.EntityComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

public class AligningEntityComponent<E extends Entity> extends EntityComponent<E> {

    public AligningEntityComponent(Sizing sizing, E entity) {
        super(sizing, entity);
    }

    @Override
    public void draw(MatrixStack matrices, int mouseX, int mouseY, float partialTicks, float delta) {
        float prevHeadYaw = 0f;
        float prevPrevHeadYaw = 0f;

        if (this.entity instanceof LivingEntity living) {
            prevHeadYaw = living.headYaw;
            prevPrevHeadYaw = living.prevHeadYaw;

            living.headYaw = living.prevBodyYaw;
            living.prevHeadYaw = living.prevBodyYaw;
        }

        this.mouseRotation = 90 + (entity instanceof LivingEntity living ? living.prevBodyYaw : entity.getBodyYaw());
        super.draw(matrices, mouseX, mouseY, partialTicks, delta);

        if (this.entity instanceof LivingEntity living) {
            living.headYaw = prevHeadYaw;
            living.prevHeadYaw = prevPrevHeadYaw;
        }
    }
}
