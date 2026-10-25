package com.panda.fulldraw.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.panda.fulldraw.FullDrawMod;
import com.panda.fulldraw.entity.Marksman;
import com.panda.fulldraw.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractSkeletonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SkeletonClothingLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;

/**
 * The Marksman: vanilla's skeleton in a hooded cloak of forest green (drawn on the stray's outer layer), a quiver of
 * arrows slung across its back. It holds and draws its bow as a skeleton does, and crouches when it
 * ducks behind a parapet.
 */
public final class MarksmanRenderer extends AbstractSkeletonRenderer<Marksman, SkeletonRenderState> {
    public static final ModelLayerLocation QUIVER = new ModelLayerLocation(FullDrawMod.id("marksman"), "quiver");
    private static final Identifier SKIN = Identifier.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    private static final Identifier HOOD = FullDrawMod.id("textures/entity/marksman/marksman_hood.png");
    private static final Identifier QUIVER_TEXTURE = FullDrawMod.id("textures/entity/marksman/marksman_quiver.png");

    public MarksmanRenderer(EntityRendererProvider.Context context) {
        super(context, ModelLayers.SKELETON, ModelLayers.SKELETON_ARMOR);
        addLayer(new SkeletonClothingLayer<>(this, context.getModelSet(), ModelLayers.STRAY_OUTER_LAYER, HOOD));
        addLayer(new QuiverLayer(this, new QuiverModel(context.bakeLayer(QUIVER), getModel().body)));
    }

    public static void registerLayers(ClientSinks.Layers layers) {
        layers.register(QUIVER, QuiverModel::createLayer);
    }

    public static void registerRenderers(ClientSinks.Renderers renderers) {
        renderers.register(ModEntities.MARKSMAN.get(), MarksmanRenderer::new);
    }

    @Override
    public SkeletonRenderState createRenderState() {
        return new SkeletonRenderState();
    }

    @Override
    public Identifier getTextureLocation(SkeletonRenderState state) {
        return SKIN;
    }

    /** Any bow it holds takes the skeleton's bow pose, not only vanilla's (a command may hand it another). */
    @Override
    public void extractRenderState(Marksman entity, SkeletonRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.isHoldingBow = entity.getMainHandItem().getItem() instanceof BowItem;
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(Marksman entity, HumanoidArm arm) {
        if (arm == entity.getMainArm() && entity.isAggressive() && entity.getMainHandItem().getItem() instanceof BowItem)
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        return super.getArmPose(entity, arm);
    }

    /** A leather quiver slung across the back, leaning over the right shoulder, three arrows' fletchings out of its mouth. */
    static final class QuiverModel extends Model<SkeletonRenderState> {
        private final ModelPart body;
        private final ModelPart follow;

        QuiverModel(ModelPart root, ModelPart body) {
            super(root, RenderTypes::entityCutout);
            this.body = body;
            this.follow = root.getChild("body");
        }

        static LayerDefinition createLayer() {
            MeshDefinition mesh = new MeshDefinition();
            var body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
            var quiver = body.addOrReplaceChild("quiver", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F),
                    PartPose.offsetAndRotation(0.5F, 0.5F, 3.6F, 0.0F, 0.0F, -0.6F));
            for (int i = 0; i < 3; i++)
                quiver.addOrReplaceChild("arrow" + i, CubeListBuilder.create().texOffs(12 + 4 * i, 0).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                        PartPose.offsetAndRotation(-0.8F + 0.8F * i, 0.2F, (i == 1 ? 0.5F : -0.4F), 0.0F, 0.0F, (i - 1) * 0.15F));
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public void setupAnim(SkeletonRenderState state) {
            super.setupAnim(state);
            // rides the body: its lean when crouched, its turn
            follow.x = body.x;
            follow.y = body.y;
            follow.z = body.z;
            follow.xRot = body.xRot;
            follow.yRot = body.yRot;
            follow.zRot = body.zRot;
        }
    }

    static final class QuiverLayer extends RenderLayer<SkeletonRenderState, SkeletonModel<SkeletonRenderState>> {
        private final QuiverModel model;

        QuiverLayer(MarksmanRenderer parent, QuiverModel model) {
            super(parent);
            this.model = model;
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, SkeletonRenderState state, float yRot, float xRot) {
            if (state.isInvisible) return;
            collector.submitModel(model, state, poseStack, QUIVER_TEXTURE, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
        }
    }
}
