// Crowd animation, shared by the colour and the shadow (depth) passes so the shadows follow the
// people. Spectator vertices carry (phase, part) in a_texCoord0: part 0 legs, 1 body and head,
// 2 / 3 arms, 4 flag. u_crowd = (excitement, cheer, wave front 0..1, wave strength).
#if defined(texCoord0Flag) && !defined(diffuseTextureFlag)
#define crowdFlag
#endif

uniform float u_time;
uniform vec4 u_crowd;

float crowdHash(float n) {
    return fract(sin(n * 91.3458) * 47453.5453);
}

vec3 animateCrowd(vec3 world, vec2 data) {
    float phase = data.x;
    float part = data.y;
    // How keen this person is (some stay seated even when the others stand).
    float fan = crowdHash(phase * 13.7 + 0.31);
    // The wave runs around the bowl.
    float around = atan(world.z, world.x) / 6.2831853 + 0.5;
    float wave = 0.0;
    if (u_crowd.w > 0.0) {
        float d = abs(fract(around - u_crowd.z + 0.5) - 0.5);
        wave = (1.0 - smoothstep(0.0, 0.05, d)) * u_crowd.w;
    }
    float stand = clamp(max(u_crowd.y * step(0.3, fan), wave), 0.0, 1.0);
    float arms = clamp(max(u_crowd.y * step(0.22, fan), wave), 0.0, 1.0);
    // Breathing and bobbing, livelier when the rally is tense.
    float bob = sin(u_time * (1.4 + fan * 2.2) + phase * 6.2831853) * (0.008 + 0.045 * u_crowd.x * fan);
    vec3 p = world;
    if (part > 0.5) p.y += stand * 0.32 + bob;
    if (part > 1.5 && part < 3.5) {
        // Arms go up over the head; when cheering they pump.
        float pump = 0.5 + 0.5 * sin(u_time * (5.0 + fan * 4.0) + phase * 9.0 + part);
        p.y += arms * (0.45 + 0.1 * pump);
        // Clapping: hands move towards the chest a little, in rhythm.
        float clap = u_crowd.x * (1.0 - arms) * step(0.5, fan);
        p.y += clap * 0.12 * (0.5 + 0.5 * sin(u_time * 9.0 + phase * 3.0));
    }
    if (part > 3.5) {
        // Flags wave.
        p.y += stand * 0.2;
        p.x += sin(u_time * 2.6 + phase * 6.0 + world.y * 1.7) * 0.12 * (0.4 + u_crowd.x);
        p.z += cos(u_time * 2.1 + phase * 5.0) * 0.06 * (0.4 + u_crowd.x);
    }
    return p;
}
