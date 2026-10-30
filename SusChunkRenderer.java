package dev.local.suschunks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * Zeichnet fuer jeden verdaechtigen Chunk eine rote, halbtransparente Platte (durch Waende sichtbar).
 * Aufbau 1:1 nach dem Fabric-Docs-Beispiel "Rendering in the World" fuer 26.2.
 */
public final class SusChunkRenderer {
	// Optik: Hoehe der Platte relativ zu den Fuessen des Spielers, Dicke, Farbe
	private static final float Y_OFFSET = -1.0f;
	private static final float THICKNESS = 0.15f;
	private static final float R = 1f, G = 0f, B = 0f, A = 0.35f;

	private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(SusChunksClient.id("pipeline/sus_chunk_through_walls"))
					.withDepthStencilState(Optional.empty())
					.build());

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final StagedVertexBuffer stagedBuffer =
			new StagedVertexBuffer(() -> "SusChunks Buffer", RenderType.SMALL_BUFFER_SIZE);

	/** Unveraenderlicher Render-State, wird in der Extraction-Phase erzeugt. */
	private record Slab(float x0, float z0, float x1, float z1, float y) { }

	private static volatile List<Slab> slabs = List.of();

	private SusChunkRenderer() { }

	public static void init() {
		LevelExtractionEvents.END_EXTRACTION.register(SusChunkRenderer::extract);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(SusChunkRenderer::renderAndDraw);
	}

	private static void extract(LevelExtractionContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (!SusChunksClient.enabled || mc.player == null) {
			slabs = List.of();
			return;
		}
		float y = (float) mc.player.getY() + Y_OFFSET;
		List<Slab> list = new ArrayList<>();
		for (SusChunkScanner.Hit hit : SusChunkScanner.hits) {
			float x0 = hit.chunkX() * 16f;
			float z0 = hit.chunkZ() * 16f;
			list.add(new Slab(x0, z0, x0 + 16f, z0 + 16f, y));
		}
		slabs = List.copyOf(list);
	}

	private static void renderAndDraw(LevelRenderContext context) {
		List<Slab> current = slabs;
		if (current.isEmpty()) return;

		RenderPipeline pipeline = FILLED_THROUGH_WALLS;
		VertexFormat format = pipeline.getVertexFormatBinding(0);
		assert format != null;

		PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
		StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(format, primitive,
				primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

		PoseStack matrices = context.poseStack();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);

		VertexConsumer builder = stagedBuffer.getVertexBuilder(draw);
		for (Slab s : current) {
			box(matrices.last().pose(), builder, s.x0(), s.y(), s.z0(), s.x1(), s.y() + THICKNESS, s.z1());
		}
		matrices.popPose();

		stagedBuffer.upload();
		StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draw);
		if (info != null) {
			drawBuffer(Minecraft.getInstance(), info, pipeline);
		}
		stagedBuffer.endFrame();
	}

	private static void box(Matrix4fc m, VertexConsumer b, float x0, float y0, float z0, float x1, float y1, float z1) {
		// Front
		b.addVertex(m, x0, y0, z1).setColor(R, G, B, A);
		b.addVertex(m, x1, y0, z1).setColor(R, G, B, A);
		b.addVertex(m, x1, y1, z1).setColor(R, G, B, A);
		b.addVertex(m, x0, y1, z1).setColor(R, G, B, A);
		// Back
		b.addVertex(m, x1, y0, z0).setColor(R, G, B, A);
		b.addVertex(m, x0, y0, z0).setColor(R, G, B, A);
		b.addVertex(m, x0, y1, z0).setColor(R, G, B, A);
		b.addVertex(m, x1, y1, z0).setColor(R, G, B, A);
		// Left
		b.addVertex(m, x0, y0, z0).setColor(R, G, B, A);
		b.addVertex(m, x0, y0, z1).setColor(R, G, B, A);
		b.addVertex(m, x0, y1, z1).setColor(R, G, B, A);
		b.addVertex(m, x0, y1, z0).setColor(R, G, B, A);
		// Right
		b.addVertex(m, x1, y0, z1).setColor(R, G, B, A);
		b.addVertex(m, x1, y0, z0).setColor(R, G, B, A);
		b.addVertex(m, x1, y1, z0).setColor(R, G, B, A);
		b.addVertex(m, x1, y1, z1).setColor(R, G, B, A);
		// Top
		b.addVertex(m, x0, y1, z1).setColor(R, G, B, A);
		b.addVertex(m, x1, y1, z1).setColor(R, G, B, A);
		b.addVertex(m, x1, y1, z0).setColor(R, G, B, A);
		b.addVertex(m, x0, y1, z0).setColor(R, G, B, A);
		// Bottom
		b.addVertex(m, x0, y0, z0).setColor(R, G, B, A);
		b.addVertex(m, x1, y0, z0).setColor(R, G, B, A);
		b.addVertex(m, x1, y0, z1).setColor(R, G, B, A);
		b.addVertex(m, x0, y0, z1).setColor(R, G, B, A);
	}

	private static void drawBuffer(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();
		assert colorTexture != null;

		try (RenderPass pass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "suschunks overlay", colorTexture, Optional.empty(),
						mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", dynamicTransforms);
			pass.setVertexBuffer(0, info.vertexBuffer().slice());
			pass.setIndexBuffer(info.indexBuffer(), info.indexType());
			pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}

	public static void close() {
		stagedBuffer.close();
	}
}
