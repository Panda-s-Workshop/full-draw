package com.panda.fulldraw.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.Shieldbearer;
import com.panda.fulldraw.registry.ModEntities;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

/**
 * The Shieldbearer: vanilla's illager model in the pillager's clothes, both arms out holding a tower shield before it,
 * as tall as it is, its sides bent back round the body and a slit to look through. When the shield is knocked down it
 * tips forward and the arms drop with it; cracked by a bolt it sags to one side, its split face still showing. A bash
 * shoves it forward. Celebrating, it drops the shield to raise its arms.
 */
public final class ShieldbearerRenderer extends IllagerRenderer<Shieldbearer, ShieldbearerRenderer.State> {
    public static final ModelLayerLocation BODY = new ModelLayerLocation(FullDrawMod.id("shieldbearer"), "main");
    public static final ModelLayerLocation SHIELD = new ModelLayerLocation(FullDrawMod.id("shieldbearer"), "tower_shield");
    private static final Identifier SKIN = Identifier.withDefaultNamespace("textures/entity/illager/pillager.png");
    private static final Identifier SHIELD_TEXTURE = FullDrawMod.id("textures/entity/shieldbearer/tower_shield.png");
    private static final Identifier CRACKED_TEXTURE = FullDrawMod.id("textures/entity/shieldbearer/tower_shield_cracked.png");

    public ShieldbearerRenderer(EntityRendererProvider.Context context) {
        super(context, new BodyModel(context.bakeLayer(BODY)), 0.5F);
        addLayer(new ShieldLayer(this, new ShieldModel(context.bakeLayer(SHIELD))));
    }

    public static void registerLayers(ClientSinks.Layers layers) {
        layers.register(BODY, IllagerModel::createBodyLayer);
        layers.register(SHIELD, ShieldModel::createLayer);
    }

    public static void registerRenderers(ClientSinks.Renderers renderers) {
        renderers.register(ModEntities.SHIELDBEARER.get(), ShieldbearerRenderer::new);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Shieldbearer entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.lowered = entity.isCelebrating() ? 1.0F : entity.lowered(partialTicks);
        state.cracked = entity.isCracked();
        state.bash = entity.getSwingAnimation(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return SKIN;
    }

    public static final class State extends IllagerRenderState {
        /** How far the shield is down, 0 (raised) to 1. */
        public float lowered;
        /** Whether it's down from a bolt's crack. */
        public boolean cracked;
        /** The bash's swing, 0 to 1. */
        public float bash;
    }

    /** How far forward a bash shoves the shield, in model pixels, at the swing's height. */
    private static float shove(State state) {
        return Mth.sin(state.bash * Mth.PI) * 3.0F;
    }

    /** Vanilla's illager, its arms out in front gripping the shield's back, dropping as the shield goes down. */
    static final class BodyModel extends IllagerModel<State> {
        private final ModelPart rightArm, leftArm;

        BodyModel(ModelPart root) {
            super(root);
            rightArm = root.getChild("right_arm");
            leftArm = root.getChild("left_arm");
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            if (state.armPose == AbstractIllager.IllagerArmPose.CELEBRATING) return;
            float hold = 1.0F - state.lowered;
            float shove = shove(state) * 0.1F;
            rightArm.xRot = Mth.lerp(hold, rightArm.xRot * 0.3F - 0.2F, -1.25F - shove);
            leftArm.xRot = Mth.lerp(hold, leftArm.xRot * 0.3F - 0.2F, -1.25F - shove);
            // the hands meet the shield's grips, either side of the middle
            rightArm.yRot = hold * -0.3F;
            leftArm.yRot = hold * 0.3F;
        }
    }

    /**
     * The tower shield: 14 by 32 model pixels, 2 thick, standing on its bottom edge before the bearer's shins with its top
     * at the crown of the head, and a wing 4 wide down each side, bent back round the body. Knocked down, it tips forward
     * about that edge; cracked, it tips less and leans to one side.
     */
    static final class ShieldModel extends Model<State> {
        /** How far the wings bend back, in radians. */
        private static final float WING_BEND = 0.8F;
        private final ModelPart shield;

        ShieldModel(ModelPart root) {
            super(root, RenderTypes::entityCutout);
            shield = root.getChild("shield");
        }

        static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            var shield = mesh.getRoot().addOrReplaceChild("shield", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -32.0F, -1.0F, 14.0F, 32.0F, 2.0F),
                    PartPose.offset(0.0F, 22.0F, -10.0F));
            shield.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(32, 0).addBox(0.0F, -32.0F, -1.0F, 4.0F, 32.0F, 2.0F),
                    PartPose.offsetAndRotation(7.0F, 0.0F, 0.0F, 0.0F, -WING_BEND, 0.0F));
            shield.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(44, 0).addBox(-4.0F, -32.0F, -1.0F, 4.0F, 32.0F, 2.0F),
                    PartPose.offsetAndRotation(-7.0F, 0.0F, 0.0F, 0.0F, WING_BEND, 0.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            shield.xRot = state.lowered * (state.cracked ? 0.6F : 1.1F);
            shield.zRot = state.cracked ? state.lowered * 0.45F : 0.0F;
            shield.y = 22.0F + state.lowered;
            shield.z = -10.0F - shove(state);
        }
    }

    static final class ShieldLayer extends RenderLayer<State, IllagerModel<State>> {
        private final ShieldModel model;

        ShieldLayer(ShieldbearerRenderer parent, ShieldModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
            if (state.isInvisible) return;
            collector.submitModel(model, state, poseStack, state.cracked ? CRACKED_TEXTURE : SHIELD_TEXTURE, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
        }
    }
}
