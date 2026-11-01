package com.volleyball.game.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.shaders.BaseShader;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * libGDX's {@link DefaultShader} with the arena's own GLSL ({@code shaders/arena.*}): the
 * default shader keeps setting matrices and materials; this class adds the venue's light rig,
 * the shadow map, the capsule occluders, the floor reflection, the crowd animation state and the
 * surface parameters. The same class with the depth GLSL renders the shadow map.
 */
public final class ArenaShader extends DefaultShader {
    /** Frame state shared by all arena shaders (set by {@link ArenaScene} every frame). */
    public static final class Frame {
        public static final int MAX_CAPSULES = 24;
        final float[] capA = new float[MAX_CAPSULES * 4];
        final float[] capB = new float[MAX_CAPSULES * 4];
        int capsules;
        final Vector3 shadowDir = new Vector3(0.25f, 1f, 0.15f).nor();
        Texture reflection;
        boolean mirrorPass;
        float exposure = 1.1f;
        float occlusion = 1f;
        LightRig rig = new LightRig();
        Texture shadowMap;
        final Matrix4 shadowMatrix = new Matrix4();
        Texture courtShadowMap;
        final Matrix4 courtShadowMatrix = new Matrix4();
        float shadowTexel = 1f / 2048f;
        float time;
        final float[] crowd = {0.2f, 0f, 0f, 0f};
        final float[] fog = {0f, 0f, 0f, 0f};

        void clearCapsules() {
            capsules = 0;
        }

        void addCapsule(float ax, float ay, float az, float bx, float by, float bz, float radius) {
            if (capsules >= MAX_CAPSULES) return;
            int i = capsules * 4;
            capA[i] = ax;
            capA[i + 1] = ay;
            capA[i + 2] = az;
            capA[i + 3] = radius;
            capB[i] = bx;
            capB[i + 1] = by;
            capB[i + 2] = bz;
            capB[i + 3] = radius;
            capsules++;
        }
    }

    private static final ShadingAttribute NONE = ShadingAttribute.matte();

    private final boolean hasShading;

    public ArenaShader(Renderable renderable, Config config, Frame frame) {
        super(renderable, config, createPrefix(renderable, config));
        hasShading = renderable.material.has(ShadingAttribute.TYPE);
        global("u_capA", (s, id) -> s.program.setUniform4fv(s.loc(id), frame.capA, 0, frame.capA.length));
        global("u_capB", (s, id) -> s.program.setUniform4fv(s.loc(id), frame.capB, 0, frame.capB.length));
        global("u_capCount", (s, id) -> s.set(id, (float) frame.capsules));
        global("u_shadowDir", (s, id) -> s.set(id, frame.shadowDir));
        global("u_screenSize", (s, id) -> s.set(id, (float) Gdx.graphics.getBackBufferWidth(), (float) Gdx.graphics.getBackBufferHeight()));
        global("u_reflectionOn", (s, id) -> s.set(id, frame.reflection != null && !frame.mirrorPass ? 1f : 0f));
        global("u_reflection", (s, id) -> {
            if (frame.reflection != null && !frame.mirrorPass) s.set(id, s.context.textureBinder.bind(frame.reflection));
        });
        global("u_arena", (s, id) -> s.set(id, 9f, 4.5f, frame.exposure, frame.occlusion));
        global("u_lightPos", (s, id) -> s.program.setUniform4fv(s.loc(id), frame.rig.pos, 0, frame.rig.pos.length));
        global("u_lightDir", (s, id) -> s.program.setUniform4fv(s.loc(id), frame.rig.dir, 0, frame.rig.dir.length));
        global("u_lightColor", (s, id) -> s.program.setUniform4fv(s.loc(id), frame.rig.color, 0, frame.rig.color.length));
        global("u_lightCount", (s, id) -> s.set(id, (float) frame.rig.count));
        global("u_sky", (s, id) -> s.set(id, frame.rig.sky.r, frame.rig.sky.g, frame.rig.sky.b));
        global("u_ground", (s, id) -> s.set(id, frame.rig.ground.r, frame.rig.ground.g, frame.rig.ground.b));
        global("u_fog", (s, id) -> s.set(id, frame.fog[0], frame.fog[1], frame.fog[2], frame.fog[3]));
        global("u_shadowOn", (s, id) -> s.set(id, frame.shadowMap != null ? 1f : 0f));
        global("u_shadowMap", (s, id) -> {
            if (frame.shadowMap != null) s.set(id, s.context.textureBinder.bind(frame.shadowMap));
        });
        global("u_shadowMatrix", (s, id) -> s.set(id, frame.shadowMatrix));
        global("u_courtShadowMap", (s, id) -> {
            if (frame.courtShadowMap != null) s.set(id, s.context.textureBinder.bind(frame.courtShadowMap));
        });
        global("u_courtShadowMatrix", (s, id) -> s.set(id, frame.courtShadowMatrix));
        global("u_shadowTexel", (s, id) -> s.set(id, frame.shadowTexel));
        global("u_time", (s, id) -> s.set(id, frame.time));
        global("u_crowd", (s, id) -> s.set(id, frame.crowd[0], frame.crowd[1], frame.crowd[2], frame.crowd[3]));
        register(new Uniform("u_surface"), new LocalSetter() {
            @Override
            public void set(BaseShader shader, int inputID, Renderable r, Attributes a) {
                ShadingAttribute s = surface(r);
                shader.set(inputID, s.reflectivity, s.shininess, s.specular, s.rim);
            }
        });
        register(new Uniform("u_surfaceDetail"), new LocalSetter() {
            @Override
            public void set(BaseShader shader, int inputID, Renderable r, Attributes a) {
                ShadingAttribute s = surface(r);
                shader.set(inputID, (float) s.pattern, s.roughness);
            }
        });
    }

    private interface FrameSetter {
        void set(BaseShader shader, int inputID);
    }

    private void global(String name, FrameSetter setter) {
        register(new Uniform(name), new GlobalSetter() {
            @Override
            public void set(BaseShader shader, int inputID, Renderable r, Attributes a) {
                setter.set(shader, inputID);
            }
        });
    }

    private static ShadingAttribute surface(Renderable r) {
        ShadingAttribute s = (ShadingAttribute) r.material.get(ShadingAttribute.TYPE);
        return s == null ? NONE : s;
    }

    @Override
    public boolean canRender(Renderable renderable) {
        return super.canRender(renderable) && renderable.material.has(ShadingAttribute.TYPE) == hasShading;
    }

    /** Creates arena shaders (colour pass), or with {@code depth} the shadow-map shaders. */
    public static final class Provider extends BaseShaderProvider {
        private final Config config;
        private final Frame frame;

        public Provider(Frame frame, boolean depth) {
            this.frame = frame;
            String crowd = Gdx.files.classpath("shaders/crowd.glsl").readString();
            config = depth
                    ? new Config(crowd + Gdx.files.classpath("shaders/arena_depth.vert").readString(),
                    Gdx.files.classpath("shaders/arena_depth.frag").readString())
                    : new Config(crowd + Gdx.files.classpath("shaders/arena.vert").readString(),
                    Gdx.files.classpath("shaders/arena.frag").readString());
            config.numDirectionalLights = 0;
            config.numPointLights = 0;
            config.numSpotLights = 0;
            config.numBones = 0;
        }

        @Override
        protected Shader createShader(Renderable renderable) {
            return new ArenaShader(renderable, config, frame);
        }
    }
}
