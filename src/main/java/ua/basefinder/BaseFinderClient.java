
package ua.basefinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class BaseFinderClient implements ClientModInitializer {
    private static boolean enabled = false;
    private static int tickCounter = 0;

    private static final int RADIUS = 12;
    private static final int VERTICAL_RADIUS = 6;

    private static final List<BlockPos> CHESTS = new ArrayList<>();

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.basefinder.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.basefinder"
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                enabled = !enabled;
                CHESTS.clear();

                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal(
                            "BaseFinder: " +
                            (enabled ? "ON" : "OFF")
                        ),
                        true
                    );
                }
            }

            if (!enabled || client.world == null ||
                client.player == null) {
                CHESTS.clear();
                return;
            }

            if (++tickCounter >= 10) {
                tickCounter = 0;
                scanNearby(client);
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!enabled || CHESTS.isEmpty()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrixStack();
            if (matrices == null || context.consumers() == null) return;

            Vec3d camera = context.camera().getPos();
            VertexConsumer lines = context.consumers()
                .getBuffer(RenderLayer.getLines());

            for (BlockPos pos : CHESTS) {
                if (pos.getSquaredDistance(
                    client.player.getBlockPos()
                ) > RADIUS * RADIUS * 3) {
                    continue;
                }

                Box box = new Box(pos).offset(
                    -camera.x, -camera.y, -camera.z
                );

                VertexRendering.drawBox(
                    matrices, lines, box,
                    0.15f, 1.0f, 0.25f, 1.0f
                );
            }
        });
    }

    private static void scanNearby(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        BlockPos center = client.player.getBlockPos();
        List<BlockPos> found = new ArrayList<>();

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -VERTICAL_RADIUS;
                 y <= VERTICAL_RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    mutable.set(
                        center.getX() + x,
                        center.getY() + y,
                        center.getZ() + z
                    );

                    if (!client.world.isChunkLoaded(mutable)) {
                        continue;
                    }

                    var state = client.world.getBlockState(mutable);

                    if (state.isOf(Blocks.CHEST) ||
                        state.isOf(Blocks.TRAPPED_CHEST)) {
                        found.add(mutable.toImmutable());
                    }
                }
            }
        }

        CHESTS.clear();
        CHESTS.addAll(found);
    }
}
