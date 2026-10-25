package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.BogLurker;
import com.panda.fulldraw.registry.ModEntities;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Bog Lurker: a broad, flat, mossy frog-thing with two bulging eyes on top of its head that glow yellow in any
 * light, so at the surface of dark water they are what a player sees. Its throat swells before it spits; fleeing, its
 * back legs kick. Its spit is drawn as the slimeball it is.
 */
public final class BogLurkerRenderer extends MobRenderer<BogLurker, BogLurkerRenderer.State, BogLurkerRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(FullDrawMod.id("bog_lurker"), "main");
    private static final Identifier TEXTURE = FullDrawMod.id("textures/entity/bog_lurker/bog_lurker.png");
    private static final RenderType EYES = RenderTypes.eyes(FullDrawMod.id("textures/entity/bog_lurker/bog_lurker_eyes.png"));

    public BogLurkerRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.5F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    public static void registerLayers(ClientSinks.Layers layers) {
        layers.register(LAYER, Model::createLayer);
    }

    public static void registerRenderers(ClientSinks.Renderers renderers) {
        renderers.register(ModEntities.BOG_LURKER.get(), BogLurkerRenderer::new);
        renderers.register(ModEntities.BOG_SPIT.get(), ThrownItemRenderer::new);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BogLurker entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.phase = entity.phase();
        state.throat = entity.swollen(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURE;
    }

    public static final class State extends LivingEntityRenderState {
        public BogLurker.Phase phase = BogLurker.Phase.LURK;
        /** How far its throat is swollen for a spit, 0 to 1. */
        public float throat;
    }

    /**
     * Model pixels, facing -Z, feet at y = 24: a flat body 12 by 12 and 4 deep, its top at 18 (under the surface while it
     * lurks); two eyes 3 across on mounds standing 4 above it at the front (their tops at 14, near the top of its box),
     * so only the eyes clear the water; a throat pouch under the front; short front legs and long back legs splayed out
     * behind.
     */
    public static final class Model extends EntityModel<State> {
        private final ModelPart body, throat, leftEye, rightEye, leftBack, rightBack, leftFront, rightFront;

        Model(ModelPart root) {
            super(root);
            body = root.getChild("body");
            throat = body.getChild("throat");
            leftEye = body.getChild("left_eye");
            rightEye = body.getChild("right_eye");
            leftBack = body.getChild("left_back_leg");
            rightBack = body.getChild("right_back_leg");
            leftFront = body.getChild("left_front_leg");
            rightFront = body.getChild("right_front_leg");
        }

        static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot().addOrReplaceChild("body",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -2.0F, -6.0F, 12.0F, 4.0F, 12.0F), PartPose.offset(0.0F, 20.0F, 0.0F));
            body.addOrReplaceChild("throat", CubeListBuilder.create().texOffs(0, 18).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 2.0F, 4.0F),
                    PartPose.offset(0.0F, 2.0F, -3.0F));
            body.addOrReplaceChild("left_eye", CubeListBuilder.create().texOffs(20, 18).addBox(-1.5F, -4.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                    PartPose.offset(3.5F, -2.0F, -3.5F));
            body.addOrReplaceChild("right_eye", CubeListBuilder.create().texOffs(20, 18).mirror().addBox(-1.5F, -4.0F, -1.5F, 3.0F, 4.0F, 3.0F),
                    PartPose.offset(-3.5F, -2.0F, -3.5F));
            body.addOrReplaceChild("left_back_leg", CubeListBuilder.create().texOffs(0, 26).addBox(0.0F, -1.0F, -1.0F, 5.0F, 2.0F, 7.0F),
                    PartPose.offsetAndRotation(5.0F, 1.0F, 1.0F, 0.0F, -0.35F, 0.0F));
            body.addOrReplaceChild("right_back_leg", CubeListBuilder.create().texOffs(0, 26).mirror().addBox(-5.0F, -1.0F, -1.0F, 5.0F, 2.0F, 7.0F),
                    PartPose.offsetAndRotation(-5.0F, 1.0F, 1.0F, 0.0F, 0.35F, 0.0F));
            body.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(24, 26).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offset(5.0F, 1.0F, -4.0F));
            body.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(24, 26).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offset(-5.0F, 1.0F, -4.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            float swell = s.throat;
            // breathing at the surface: a slow rise and fall
            body.y += Mth.sin(t * 0.08F) * 0.3F;
            // the throat swells before a spit, and the head lifts with it: the eyes rise high out of the water, so the
            // tell shows from the bank as well as sounds
            leftEye.y -= 2.0F * swell;
            rightEye.y -= 2.0F * swell;
            throat.yScale = 1.0F + 1.2F * swell;
            throat.xScale = 1.0F + 0.35F * swell;
            throat.zScale = 1.0F + 0.35F * swell;
            body.xRot = -0.12F * swell;
            if (s.phase == BogLurker.Phase.FLEE) {
                // swimming away: nose down, the back legs kicking together, the front ones tucked
                body.xRot = 0.15F;
                float kick = Mth.sin(t * 0.9F);
                leftBack.yRot = -0.35F + kick * 0.6F;
                rightBack.yRot = 0.35F - kick * 0.6F;
                leftFront.xRot = 1.2F;
                rightFront.xRot = 1.2F;
            } else if (!s.isInWater) {
                // on land it crawls, the legs in diagonal pairs
                float walk = s.walkAnimationPos * 0.9F, amount = Math.min(1.0F, s.walkAnimationSpeed * 2.0F);
                leftBack.yRot = -0.35F + Mth.sin(walk) * 0.5F * amount;
                rightBack.yRot = 0.35F + Mth.sin(walk) * 0.5F * amount;
                leftFront.xRot = Mth.cos(walk) * 0.8F * amount;
                rightFront.xRot = -Mth.cos(walk) * 0.8F * amount;
            } else {
                // treading water: a slow paddle
                float paddle = Mth.sin(t * 0.12F) * 0.2F;
                leftBack.yRot = -0.35F + paddle;
                rightBack.yRot = 0.35F - paddle;
                leftFront.xRot = paddle;
                rightFront.xRot = -paddle;
            }
        }
    }
}
