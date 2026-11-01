package com.volleyball.game.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.volleyball.game.entities.Ball;
import com.volleyball.game.entities.VolleyballPlayer;
import com.volleyball.game.world.CourtTheme;
import com.volleyball.game.world.LightingPreset;
import com.volleyball.game.world.Venue;
import com.volleyball.game.world.VolleyballCourt;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the static 3D venue (floor, net, stands, crowd, screens, props) and its light rig, and
 * renders a frame: the shadow map from the dominant light, the floor reflection, then the scene.
 * Dynamic objects (players, ball) are added to each pass through {@link Dynamic}.
 */
public final class ArenaScene implements Disposable {
    /** Draws the moving objects (players, ball) into a pass; {@code replay} re-draws the last poses (reflection, shadow map). */
    public interface Dynamic {
        void draw(ModelBatch batch, Environment environment, boolean replay);
    }

    /** Home and away fan colours (blue and red, as the team kits). */
    /** The two teams of the match as the venue shows them: the LED boards' "NWO vs CSH" and the fans' colours. */
    public record Matchup(String home, String away, Color homeFans, Color awayFans) {
        public static final Matchup DEFAULT = new Matchup("NWO", "CSH", new Color(0.12f, 0.32f, 0.78f, 1f),
                new Color(0.82f, 0.14f, 0.16f, 1f));

        public static Matchup of(com.volleyball.game.entities.Team home, com.volleyball.game.entities.Team away) {
            return new Matchup(home.shortName, away.shortName, home.shirtColor, away.shirtColor);
        }
    }

    private static final Color SAND = new Color(0.86f, 0.75f, 0.55f, 1f);
    private static final Color SAND_OUTSIDE = new Color(0.83f, 0.72f, 0.52f, 1f);
    private static final Color BEACH_LINES = new Color(0.1f, 0.3f, 0.82f, 1f);
    /** Scroll speed of the LED boards, in texture widths per second. */
    private static final float LED_SPEED = 0.035f;
    /** The venue shadow map (stands, crowd) is refreshed every this many frames. */
    private static final int VENUE_SHADOW_INTERVAL = 3;
    /** Half size of the court shadow map's view (court, free zone, benches). */
    private static final float COURT_SHADOW_EXTENT = 20f;

    private final VolleyballCourt court;
    private final Venue venue;
    private final ArenaModelFactory factory = new ArenaModelFactory();
    private final ArenaShader.Frame frame = new ArenaShader.Frame();
    private ModelBatch batch = new ModelBatch();
    private ModelBatch depthBatch;
    private GraphicsQuality quality = GraphicsQuality.LOW;
    /** Shadow map size chosen in the settings (0: the preset's). */
    private int shadowOverride;
    private FrameBuffer reflectionBuffer;
    private FrameBuffer shadowBuffer;
    private FrameBuffer courtShadowBuffer;
    /** Frames since the venue shadow map was rendered. */
    private int venueShadowAge;
    private final PerspectiveCamera mirror = new PerspectiveCamera();
    private final OrthographicCamera shadowCamera = new OrthographicCamera();
    private final Environment environment = new Environment();
    private final DirectionalLight keyLight = new DirectionalLight();
    private final DirectionalLight fillLight = new DirectionalLight();
    private final ScoreboardDisplay scoreboard = new ScoreboardDisplay();
    private final LedBoardTexture ledTexture;
    private final CrowdMood crowdMood = new CrowdMood();
    private LightRig rig;

    private final List<Model> models = new ArrayList<>();
    private Model netModel;

    private final ModelInstance floor;
    private final ModelInstance structure;
    private final ModelInstance props;
    private final ModelInstance crowd;
    private final ModelInstance fixtures;
    private final ModelInstance screens;
    private final ModelInstance ledBoards;
    private final TextureAttribute ledScroll;
    private final ModelInstance sky;
    private final ModelInstance spidercam;
    private final ModelInstance[] cables;
    private ModelInstance net;

    private final Vector3 podPosition = new Vector3();
    private final Vector3 cableAnchor = new Vector3();
    private final Vector3 cableDirection = new Vector3();
    private float time;

    private CourtTheme theme;
    private LightingPreset lighting;

    public ArenaScene(VolleyballCourt court, CourtTheme theme, LightingPreset lighting) {
        this(court, Venue.CHAMPIONSHIP, theme, lighting, GraphicsQuality.LOW);
    }

    public ArenaScene(VolleyballCourt court, Venue venue, CourtTheme theme, LightingPreset lighting, GraphicsQuality quality) {
        this(court, venue, theme, lighting, quality, Matchup.DEFAULT);
    }

    public ArenaScene(VolleyballCourt court, Venue venue, CourtTheme theme, LightingPreset lighting, GraphicsQuality quality,
                      Matchup matchup) {
        this.court = court;
        this.venue = venue;
        ledTexture = new LedBoardTexture(venue, matchup.home() + "  vs  " + matchup.away());
        floor = instance(factory.buildFloor());
        structure = instance(factory.buildStructure(venue));
        props = instance(factory.buildVenue(venue));
        crowd = instance(factory.buildCrowd(venue, matchup.homeFans(), matchup.awayFans()));
        fixtures = instance(factory.buildFixtures(venue));
        screens = instance(factory.buildScreens(venue, scoreboard.texture()));
        ledBoards = instance(factory.buildLedBoards(venue, ledTexture.texture()));
        ledScroll = (TextureAttribute) ledBoards.getMaterial("led").get(TextureAttribute.Diffuse);
        sky = venue.outdoor || venue == Venue.SPORTS_PALACE ? instance(factory.buildSky(venue)) : null;
        if (venue != Venue.CHAMPIONSHIP) {
            spidercam = null;
            cables = null;
        } else {
            spidercam = instance(factory.buildSpidercamPod());
            Model cable = factory.buildCable();
            models.add(cable);
            cables = new ModelInstance[4];
            for (int i = 0; i < cables.length; i++) cables[i] = new ModelInstance(cable);
            placeSpidercam();
        }
        rebuildNet();

        environment.add(keyLight);
        environment.add(fillLight);
        applyTheme(theme);
        applyLighting(lighting);
        setQuality(quality);
    }

    private ModelInstance instance(Model model) {
        models.add(model);
        return new ModelInstance(model);
    }

    public Venue venue() {
        return venue;
    }

    public GraphicsQuality quality() {
        return quality;
    }

    /** The crowd's mood; register it as a match listener so the spectators react to the game. */
    public CrowdMood crowdMood() {
        return crowdMood;
    }

    /**
     * Switches the graphics preset. If the arena shader cannot be compiled on this GPU the scene
     * falls back to the standard look (and says so in the log).
     */
    public void setQuality(GraphicsQuality newQuality) {
        ModelBatch next;
        ModelBatch nextDepth = null;
        GraphicsQuality applied = newQuality;
        if (newQuality.arenaShader) {
            try {
                next = new ModelBatch(new ArenaShader.Provider(frame, false));
                if (shadowSize(newQuality) > 0) nextDepth = new ModelBatch(new ArenaShader.Provider(frame, true));
                // Compile now, so a broken driver is detected here and not mid-frame.
                next.begin(mirror);
                next.render(floor, environment);
                next.render(crowd, environment);
                next.end();
                if (nextDepth != null) {
                    nextDepth.begin(mirror);
                    nextDepth.render(crowd);
                    nextDepth.end();
                }
            } catch (RuntimeException e) {
                Gdx.app.error("Graphics", "Arena shader unavailable, using standard lighting: " + e.getMessage());
                if (nextDepth != null) nextDepth.dispose();
                nextDepth = null;
                next = new ModelBatch();
                applied = GraphicsQuality.LOW;
            }
        } else {
            next = new ModelBatch();
        }
        batch.dispose();
        batch = next;
        if (depthBatch != null) depthBatch.dispose();
        depthBatch = nextDepth;
        quality = applied;
        if (reflectionBuffer != null && applied.reflectionScale <= 0f) {
            reflectionBuffer.dispose();
            reflectionBuffer = null;
        }
        if (shadowBuffer != null && shadowBuffer.getWidth() != shadowSize(applied)) disposeShadowMaps();
    }

    /**
     * Shadow map size from the settings: {@code size} texels (1024, 2048, 4096) for the presets that
     * have shadow maps, or 0 to use the preset's own.
     */
    public void setShadowMapSize(int size) {
        if (size == shadowOverride) return;
        shadowOverride = size;
        setQuality(quality);
    }

    private int shadowSize(GraphicsQuality q) {
        return q.shadowMapSize > 0 && shadowOverride > 0 ? shadowOverride : q.shadowMapSize;
    }

    /**
     * Occluders for the ambient occlusion (and the soft shadows when there is no shadow map): one
     * capsule per player on court (tipped over in dives) and a sphere for the ball.
     */
    public void setOccluders(List<VolleyballPlayer> players, Ball ball) {
        frame.clearCapsules();
        for (VolleyballPlayer p : players) {
            if (!p.isOnCourt()) continue;
            float tilt = p.bodyTilt() * MathUtils.HALF_PI * 0.95f;
            float r = 0.21f;
            float up = MathUtils.cos(tilt);
            float out = MathUtils.sin(tilt);
            float bx = p.position.x + p.diveDirection.x * out * 0.25f;
            float by = p.position.y + r * up + 0.05f;
            float bz = p.position.z + p.diveDirection.z * out * 0.25f;
            float len = p.height - 2f * r - 0.1f;
            frame.addCapsule(bx, by, bz, bx + p.diveDirection.x * out * len, by + up * len, bz + p.diveDirection.z * out * len, r);
        }
        if (ball.isActive() || ball.position.y > 0f) {
            frame.addCapsule(ball.position.x, ball.position.y, ball.position.z, ball.position.x, ball.position.y, ball.position.z,
                    Ball.RADIUS);
        }
    }

    /**
     * The teams changed ends: the hall (stands, benches, scorer's table, screens) turns half a
     * circle around the court, so each team's bench stays beside its half. The court and the net
     * are symmetric and stay.
     */
    public void setSidesSwapped(boolean swapped) {
        if (swapped == sidesSwapped) return;
        sidesSwapped = swapped;
        // The palace's mosaic belongs to the hall (the open-air skies stay with the world).
        ModelInstance hallBackdrop = venue == Venue.SPORTS_PALACE ? sky : null;
        for (ModelInstance m : new ModelInstance[]{structure, props, crowd, fixtures, screens, ledBoards, hallBackdrop}) {
            if (m == null) continue;
            m.transform.idt();
            if (swapped) m.transform.rotate(Vector3.Y, 180f);
        }
        venueShadowAge = VENUE_SHADOW_INTERVAL;
    }

    private boolean sidesSwapped;

    /** Advances the venue's life: the crowd, the LED boards, the cable camera. Call once per frame. */
    public void update(float delta) {
        time += delta;
        crowdMood.update(delta);
        // The gym's banners are painted: they stay put.
        if (venue != Venue.SCHOOL_GYM) ledScroll.offsetU = (ledScroll.offsetU + delta * LED_SPEED) % 1f;
        if (spidercam != null) placeSpidercam();
    }

    /** The cable camera glides slowly over the court on a figure-of-eight; four cables hold it. */
    private void placeSpidercam() {
        podPosition.set(MathUtils.sin(time * 0.11f) * 7f, 10.5f + MathUtils.sin(time * 0.07f) * 1.5f,
                MathUtils.sin(time * 0.22f) * 4f);
        spidercam.transform.setToTranslation(podPosition);
        float ax = ArenaModelFactory.HALL_HALF_X - 1.5f;
        float az = ArenaModelFactory.HALL_HALF_Z - 1.5f;
        float ay = ArenaModelFactory.ROOF_HEIGHT - 1f;
        for (int i = 0; i < cables.length; i++) {
            cableAnchor.set(i % 2 == 0 ? -ax : ax, ay, i < 2 ? -az : az);
            cableDirection.set(cableAnchor).sub(podPosition.x, podPosition.y + 0.4f, podPosition.z);
            float length = cableDirection.len();
            cables[i].transform.setToTranslation(podPosition.x, podPosition.y + 0.4f, podPosition.z)
                    .rotate(Vector3.Y, cableDirection.nor())
                    .scale(1f, length, 1f);
        }
    }

    /**
     * Renders the frame: the shadow map (Medium and up), the floor reflection (High / Ultra) and
     * then the scene. The venue is drawn here; {@code dynamic} adds players and the ball to each pass.
     */
    public void render(Camera camera, Dynamic dynamic) {
        scoreboard.refresh();
        prepareFrame();
        renderShadowMaps(dynamic);
        if (quality.reflectionScale > 0f && camera instanceof PerspectiveCamera perspective) renderReflection(perspective, dynamic);
        ScreenUtils.clear(rig.background, true);
        frame.mirrorPass = false;
        batch.begin(camera);
        batch.render(floor, environment);
        renderAboveFloor(dynamic, false);
        batch.end();
    }

    private void prepareFrame() {
        frame.time = time;
        crowdMood.write(frame.crowd);
        frame.reflection = null;
        frame.shadowMap = null;
        frame.courtShadowMap = null;
    }

    private void renderAboveFloor(Dynamic dynamic, boolean mirrorPass) {
        batch.render(structure, environment);
        batch.render(props, environment);
        batch.render(crowd, environment);
        batch.render(net, environment);
        if (spidercam != null) {
            batch.render(spidercam, environment);
            for (ModelInstance cable : cables) batch.render(cable, environment);
        }
        // Emissive surfaces: rendered without an environment, i.e. unlit at full brightness.
        batch.render(fixtures);
        batch.render(screens);
        batch.render(ledBoards);
        if (sky != null) batch.render(sky);
        if (dynamic != null) dynamic.draw(batch, environment, mirrorPass);
    }

    /**
     * The shadow maps, depth as seen from the dominant light in orthographic views. The venue map
     * covers the whole venue and holds the static casters and the spectators (stands, props,
     * crowd); it is refreshed every few frames (the crowd moves slowly). The court map covers the
     * court and its surroundings at a finer resolution and holds what moves fast (players, ball,
     * cable camera) and the net; it is rendered every frame. The floor, the light sources, the
     * screens and the sky only receive shadows.
     */
    private void renderShadowMaps(Dynamic dynamic) {
        int size = shadowSize(quality);
        if (depthBatch == null || size <= 0) return;
        if (shadowBuffer == null) {
            shadowBuffer = shadowTarget(size);
            courtShadowBuffer = shadowTarget(size);
            venueShadowAge = VENUE_SHADOW_INTERVAL;
        }
        if (++venueShadowAge >= VENUE_SHADOW_INTERVAL) {
            venueShadowAge = 0;
            aimShadowCamera(rig.shadowExtent);
            frame.shadowMatrix.set(shadowCamera.combined);
            beginShadowTarget(shadowBuffer);
            depthBatch.render(structure);
            depthBatch.render(props);
            depthBatch.render(crowd);
            depthBatch.end();
            shadowBuffer.end();
        }
        aimShadowCamera(COURT_SHADOW_EXTENT);
        frame.courtShadowMatrix.set(shadowCamera.combined);
        beginShadowTarget(courtShadowBuffer);
        depthBatch.render(net);
        if (spidercam != null) depthBatch.render(spidercam);
        if (dynamic != null) dynamic.draw(depthBatch, null, true);
        depthBatch.end();
        courtShadowBuffer.end();
        frame.shadowTexel = 1f / size;
        frame.shadowMap = shadowBuffer.getColorBufferTexture();
        frame.courtShadowMap = courtShadowBuffer.getColorBufferTexture();
    }

    private static FrameBuffer shadowTarget(int size) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, true);
        buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return buffer;
    }

    /** Orthographic view along the shadow light, {@code extent} metres either side of the court centre. */
    private void aimShadowCamera(float extent) {
        shadowCamera.viewportWidth = extent * 2f;
        shadowCamera.viewportHeight = extent * 2f;
        shadowCamera.near = 0f;
        shadowCamera.far = rig.shadowDistance + 70f;
        shadowCamera.position.set(rig.shadowDirection).scl(rig.shadowDistance);
        shadowCamera.direction.set(rig.shadowDirection).scl(-1f);
        shadowCamera.up.set(Math.abs(rig.shadowDirection.y) > 0.9f ? Vector3.Z : Vector3.Y);
        shadowCamera.update();
    }

    private void beginShadowTarget(FrameBuffer buffer) {
        buffer.begin();
        Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        depthBatch.begin(shadowCamera);
    }

    /**
     * The venue seen in the floor: everything above the floor rendered by a camera mirrored in the
     * floor plane (y = 0) into a texture, which the floor samples at its own screen position.
     */
    private void renderReflection(PerspectiveCamera camera, Dynamic dynamic) {
        int w = Math.max(16, Math.round(Gdx.graphics.getBackBufferWidth() * quality.reflectionScale));
        int h = Math.max(16, Math.round(Gdx.graphics.getBackBufferHeight() * quality.reflectionScale));
        if (reflectionBuffer == null || reflectionBuffer.getWidth() != w || reflectionBuffer.getHeight() != h) {
            if (reflectionBuffer != null) reflectionBuffer.dispose();
            reflectionBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, true);
            reflectionBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        mirror.fieldOfView = camera.fieldOfView;
        mirror.viewportWidth = camera.viewportWidth;
        mirror.viewportHeight = camera.viewportHeight;
        mirror.near = camera.near;
        mirror.far = camera.far;
        mirror.position.set(camera.position.x, -camera.position.y, camera.position.z);
        mirror.direction.set(camera.direction.x, -camera.direction.y, camera.direction.z);
        mirror.up.set(camera.up.x, -camera.up.y, camera.up.z);
        mirror.update();

        frame.mirrorPass = true;
        reflectionBuffer.begin();
        ScreenUtils.clear(rig.background, true);
        // The mirrored camera is an ordinary (right-handed) camera: it renders the reflection
        // flipped left-right, which the floor shader undoes when sampling.
        batch.begin(mirror);
        renderAboveFloor(dynamic, true);
        batch.end();
        reflectionBuffer.end();
        frame.mirrorPass = false;
        frame.reflection = reflectionBuffer.getColorBufferTexture();
    }

    public ModelBatch batch() {
        return batch;
    }

    public Environment environment() {
        return environment;
    }

    public ScoreboardDisplay scoreboard() {
        return scoreboard;
    }

    public CourtTheme theme() {
        return theme;
    }

    public LightingPreset lighting() {
        return lighting;
    }

    /** Must be called after the net category of the court changes. */
    public void rebuildNet() {
        if (netModel != null) netModel.dispose();
        netModel = factory.buildNet(court.net());
        net = new ModelInstance(netModel);
    }

    public void applyTheme(CourtTheme newTheme) {
        theme = newTheme;
        if (venue == Venue.BEACH) {
            // Sand everywhere, blue lines (ropes); wooden bleachers on a steel frame.
            for (String id : new String[]{ArenaModelFactory.MAT_COURT, ArenaModelFactory.MAT_FREE_ZONE, ArenaModelFactory.MAT_HALL_FLOOR}) {
                floor.getMaterial(id).set(ShadingAttribute.sand());
            }
            floor.getMaterial(ArenaModelFactory.MAT_LINES).set(ShadingAttribute.matte());
            setDiffuse(floor, ArenaModelFactory.MAT_HALL_FLOOR, SAND_OUTSIDE);
            setDiffuse(floor, ArenaModelFactory.MAT_FREE_ZONE, SAND);
            setDiffuse(floor, ArenaModelFactory.MAT_COURT, SAND);
            setDiffuse(floor, ArenaModelFactory.MAT_LINES, BEACH_LINES);
            setDiffuse(structure, ArenaModelFactory.MAT_STANDS, new Color(0.62f, 0.5f, 0.36f, 1f));
            setDiffuse(structure, ArenaModelFactory.MAT_WALLS, new Color(0.6f, 0.62f, 0.66f, 1f));
            return;
        }
        // Floors: lacquered wood reflects most, sports floors a softer sheen.
        boolean wood = newTheme == CourtTheme.WOOD_GYM;
        int pattern = wood ? ShadingAttribute.PATTERN_WOOD : ShadingAttribute.PATTERN_SPORTS_FLOOR;
        float court = switch (newTheme) {
            case WOOD_GYM -> 0.55f;
            case NIGHT_GREEN -> 0.45f;
            default -> 0.38f;
        };
        floor.getMaterial(ArenaModelFactory.MAT_COURT).set(ShadingAttribute.floor(court, pattern));
        floor.getMaterial(ArenaModelFactory.MAT_FREE_ZONE).set(ShadingAttribute.floor(court * 0.9f, pattern));
        floor.getMaterial(ArenaModelFactory.MAT_HALL_FLOOR).set(ShadingAttribute.floor(0.22f, ShadingAttribute.PATTERN_SPORTS_FLOOR));
        floor.getMaterial(ArenaModelFactory.MAT_LINES).set(ShadingAttribute.floor(court * 0.7f, ShadingAttribute.PATTERN_NONE));
        setDiffuse(floor, ArenaModelFactory.MAT_HALL_FLOOR, theme.hallFloor);
        setDiffuse(floor, ArenaModelFactory.MAT_FREE_ZONE, theme.freeZone);
        setDiffuse(floor, ArenaModelFactory.MAT_COURT, theme.court);
        setDiffuse(floor, ArenaModelFactory.MAT_LINES, theme.lines);
        setDiffuse(structure, ArenaModelFactory.MAT_STANDS, theme.stands);
        setDiffuse(structure, ArenaModelFactory.MAT_WALLS, theme.walls);
        // The smaller venues keep their own stands and walls (the court follows the theme).
        switch (venue) {
            case SCHOOL_GYM -> {
                setDiffuse(structure, ArenaModelFactory.MAT_STANDS, new Color(0.66f, 0.48f, 0.3f, 1f));
                setDiffuse(structure, ArenaModelFactory.MAT_WALLS, new Color(0.8f, 0.8f, 0.74f, 1f));
            }
            case SPORTS_PALACE -> {
                setDiffuse(structure, ArenaModelFactory.MAT_STANDS, new Color(0.5f, 0.48f, 0.45f, 1f));
                setDiffuse(structure, ArenaModelFactory.MAT_WALLS, new Color(0.72f, 0.64f, 0.52f, 1f));
            }
            case ROOFTOP -> {
                // Rooftop concrete around the court, aluminium stands, a concrete parapet.
                floor.getMaterial(ArenaModelFactory.MAT_HALL_FLOOR).set(ShadingAttribute.floor(0.08f, ShadingAttribute.PATTERN_SPORTS_FLOOR));
                setDiffuse(floor, ArenaModelFactory.MAT_HALL_FLOOR, new Color(0.26f, 0.26f, 0.28f, 1f));
                setDiffuse(structure, ArenaModelFactory.MAT_STANDS, new Color(0.58f, 0.6f, 0.63f, 1f));
                setDiffuse(structure, ArenaModelFactory.MAT_WALLS, new Color(0.45f, 0.45f, 0.47f, 1f));
            }
            default -> {
            }
        }
    }

    public void applyLighting(LightingPreset preset) {
        lighting = preset;
        rig = LightRig.of(venue, preset);
        frame.rig = rig;
        frame.exposure = rig.exposure;
        frame.shadowDir.set(rig.shadowDirection);
        venueShadowAge = VENUE_SHADOW_INTERVAL;
        if (venue == Venue.BEACH) {
            // Open air: distant things fade into the sky.
            frame.fog[0] = rig.background.r;
            frame.fog[1] = rig.background.g;
            frame.fog[2] = rig.background.b;
            frame.fog[3] = 0.0035f;
            // Standard lighting (Low): the sun and the sky.
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight, rig.sky));
            keyLight.set(new Color(rig.color[0], rig.color[1], rig.color[2], 1f).mul(0.8f),
                    -LightRig.SUN_DIRECTION.x, -LightRig.SUN_DIRECTION.y, -LightRig.SUN_DIRECTION.z);
            fillLight.set(rig.ground, 0.3f, 0.5f, -0.4f);
            setDiffuse(fixtures, ArenaModelFactory.MAT_FIXTURES, new Color(1f, 0.97f, 0.86f, 1f));
        } else {
            // The rooftop's city fades into the night; halls have no fog.
            frame.fog[0] = rig.background.r;
            frame.fog[1] = rig.background.g;
            frame.fog[2] = rig.background.b;
            frame.fog[3] = venue == Venue.ROOFTOP ? 0.004f : 0f;
            environment.set(new ColorAttribute(ColorAttribute.AmbientLight, preset.ambient));
            keyLight.set(preset.keyColor, preset.keyDirection);
            fillLight.set(preset.fillColor, preset.fillDirection);
            setDiffuse(fixtures, ArenaModelFactory.MAT_FIXTURES, preset.fixtureColor);
        }
    }

    /** Clears the screen and starts the 3D pass with the static arena already queued (no shadows, no reflection). */
    public void begin(Camera camera) {
        scoreboard.refresh();
        prepareFrame();
        frame.mirrorPass = false;
        ScreenUtils.clear(rig.background, true);
        batch.begin(camera);
        batch.render(floor, environment);
        renderAboveFloor(null, false);
    }

    public void end() {
        batch.end();
    }

    private void disposeShadowMaps() {
        if (shadowBuffer != null) shadowBuffer.dispose();
        if (courtShadowBuffer != null) courtShadowBuffer.dispose();
        shadowBuffer = null;
        courtShadowBuffer = null;
    }

    private static void setDiffuse(ModelInstance instance, String materialId, Color color) {
        instance.getMaterial(materialId).set(ColorAttribute.createDiffuse(color));
    }

    @Override
    public void dispose() {
        batch.dispose();
        if (depthBatch != null) depthBatch.dispose();
        if (reflectionBuffer != null) reflectionBuffer.dispose();
        disposeShadowMaps();
        scoreboard.dispose();
        ledTexture.dispose();
        for (Model model : models) model.dispose();
        netModel.dispose();
    }
}
