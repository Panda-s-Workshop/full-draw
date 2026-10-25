package com.panda.fulldraw.client;

import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.GaleHarrier;
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
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Gale Harrier: a pale-grey raptor with storm-blue wingtips, a hooked yellow beak and talons. It pitches with its
 * flight (nose down in a dive), soars on spread wings with a slow beat, beats hard as it climbs, folds its wings back
 * along its body for the tell and the dive with its talons thrown forward, and lies splayed when it is grounded.
 */
public final class GaleHarrierRenderer extends MobRenderer<GaleHarrier, GaleHarrierRenderer.State, GaleHarrierRenderer.Model> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(FullDrawMod.id("gale_harrier"), "main");
    private static final Identifier TEXTURE = FullDrawMod.id("textures/entity/gale_harrier/gale_harrier.png");

    public GaleHarrierRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.5F);
    }

    public static void registerLayers(ClientSinks.Layers layers) {
        layers.register(LAYER, Model::createLayer);
    }

    public static void registerRenderers(ClientSinks.Renderers renderers) {
        renderers.register(ModEntities.GALE_HARRIER.get(), GaleHarrierRenderer::new);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GaleHarrier entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.phase = entity.phase();
        state.fold = entity.folded(partialTicks);
        state.pitch = entity.getXRot(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return TEXTURE;
    }

    public static final class State extends LivingEntityRenderState {
        public GaleHarrier.Phase phase = GaleHarrier.Phase.CIRCLE;
        /** How far the wings are folded, 0 (spread) to 1. */
        public float fold;
        /** Its pitch, degrees below level. */
        public float pitch;
    }

    /**
     * Model pixels, facing -Z, feet at y = 24: a body 6 wide and 11 long, a head with a hooked beak, a fanned tail, two
     * wings of an inner and an outer half (each 10 long, the right one mirrors the left's texture) and two legs with
     * talons. The whole bird pitches about the middle of its body.
     */
    public static final class Model extends EntityModel<State> {
        private final ModelPart body, head, tail, leftWing, leftTip, rightWing, rightTip, leftLeg, rightLeg;

        Model(ModelPart root) {
            super(root);
            body = root.getChild("body");
            head = body.getChild("head");
            tail = body.getChild("tail");
            leftWing = body.getChild("left_wing");
            leftTip = leftWing.getChild("left_tip");
            rightWing = body.getChild("right_wing");
            rightTip = rightWing.getChild("right_tip");
            leftLeg = body.getChild("left_leg");
            rightLeg = body.getChild("right_leg");
        }

        static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition body = mesh.getRoot().addOrReplaceChild("body",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.5F, -5.0F, 6.0F, 5.0F, 11.0F), PartPose.offset(0.0F, 18.0F, 0.0F));
            PartDefinition head = body.addOrReplaceChild("head",
                    CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -2.5F, -4.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, -1.0F, -5.0F));
            head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 2.0F)
                    .texOffs(24, 16).addBox(-0.5F, 1.0F, -2.0F, 1.0F, 1.0F, 1.0F), PartPose.offset(0.0F, -0.5F, -4.0F));
            body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, -0.5F, 0.0F, 7.0F, 1.0F, 7.0F),
                    PartPose.offset(0.0F, -1.0F, 6.0F));
            PartDefinition left = body.addOrReplaceChild("left_wing",
                    CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, -0.5F, -3.0F, 10.0F, 1.0F, 8.0F), PartPose.offset(3.0F, -2.0F, -1.0F));
            left.addOrReplaceChild("left_tip",
                    CubeListBuilder.create().texOffs(0, 41).addBox(0.0F, -0.5F, -2.0F, 10.0F, 1.0F, 7.0F), PartPose.offset(10.0F, 0.0F, 0.0F));
            PartDefinition right = body.addOrReplaceChild("right_wing",
                    CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-10.0F, -0.5F, -3.0F, 10.0F, 1.0F, 8.0F), PartPose.offset(-3.0F, -2.0F, -1.0F));
            right.addOrReplaceChild("right_tip",
                    CubeListBuilder.create().texOffs(0, 41).mirror().addBox(-10.0F, -0.5F, -2.0F, 10.0F, 1.0F, 7.0F), PartPose.offset(-10.0F, 0.0F, 0.0F));
            CubeListBuilder leg = CubeListBuilder.create().texOffs(36, 16).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                    .texOffs(40, 16).addBox(-1.0F, 3.0F, -1.5F, 2.0F, 1.0F, 2.0F);
            body.addOrReplaceChild("left_leg", leg, PartPose.offset(1.5F, 2.0F, 1.0F));
            body.addOrReplaceChild("right_leg", leg, PartPose.offset(-1.5F, 2.0F, 1.0F));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float t = s.ageInTicks;
            body.xRot = Mth.clamp(s.pitch, -60.0F, 80.0F) * Mth.DEG_TO_RAD;
            float beat, tipBeat, droop;
            switch (s.phase) {
                case CLIMB -> {
                    // hard beats, the upstroke higher than the downstroke, the outer half trailing
                    beat = 0.25F + Mth.sin(t * 1.1F) * 0.6F;
                    tipBeat = 0.1F + Mth.sin(t * 1.1F - 0.6F) * 0.4F;
                    droop = 0.0F;
                }
                case GROUNDED -> {
                    beat = 0.0F;
                    tipBeat = 0.0F;
                    droop = 0.45F;
                }
                default -> {
                    // soaring: a slight upward set to the wings and a slow beat now and then
                    float burst = Math.max(0.0F, Mth.sin(t * 0.05F));
                    beat = 0.12F + Mth.sin(t * 0.6F) * 0.35F * burst * burst;
                    tipBeat = beat * 0.5F;
                    droop = 0.0F;
                }
            }
            // beat > 0 raises the wings: the +X wing by a negative Z turn (model Y points down), the -X wing mirrors it
            leftWing.zRot = -beat + droop;
            rightWing.zRot = beat - droop;
            leftTip.zRot = -tipBeat + droop * 0.6F;
            rightTip.zRot = tipBeat - droop * 0.6F;
            // folded: each wing swings back along the body, the outer half further, held a little above the back
            float f = s.fold;
            leftWing.yRot = -1.25F * f;
            rightWing.yRot = 1.25F * f;
            leftTip.yRot = -0.35F * f;
            rightTip.yRot = 0.35F * f;
            leftWing.zRot -= 0.2F * f;
            rightWing.zRot += 0.2F * f;
            // talons tucked back in flight, thrown forward at the end of a dive, hanging when it is down
            float legs = s.phase == GaleHarrier.Phase.GROUNDED ? 0.0F : Mth.lerp(f, 1.2F, -0.9F);
            leftLeg.xRot = legs;
            rightLeg.xRot = legs;
            tail.xRot = s.phase == GaleHarrier.Phase.GROUNDED ? 0.1F : -0.08F + Mth.sin(t * 0.1F) * 0.04F;
            head.xRot = -body.xRot * 0.35F;
            head.zRot = s.phase == GaleHarrier.Phase.GROUNDED ? Mth.sin(t * 0.3F) * 0.3F : 0.0F;
        }
    }
}
