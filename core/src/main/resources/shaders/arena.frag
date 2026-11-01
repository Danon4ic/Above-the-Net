// Arena shader (fragment): physically motivated lighting for the venue, the floor and the players.
//
//  - Real light sources (LightRig): spotlights with an aim, a beam cone and inverse-square
//    falloff (floodlight banks, stand wash) or the sun; Blinn-Phong highlights by material.
//  - Ambient light as a hemisphere: sky / roof above, the floor's bounce below.
//  - Shadows: a shadow map from the dominant light (the rig or the sun), filtered (PCF), so the
//    net, posts, stands, spectators, players and ball all cast shadows; without a shadow map,
//    ray-traced soft shadows against the players' capsules. Capsule ambient occlusion always.
//  - Planar reflection of the venue in the floor, Fresnel and a roughness blur.
//  - Procedural floor detail (sports floor, wood, sand), distance haze, filmic tone curve.
#ifdef GL_ES
precision highp float;
#endif

// Spectators (see crowd.glsl): many tiny triangles, so a cheaper path (one bilinear shadow
// lookup, no capsules, no highlights).
#if defined(texCoord0Flag) && !defined(diffuseTextureFlag)
#define crowdFlag
#endif

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec4 v_color;
varying vec2 v_uv;

#ifdef diffuseColorFlag
uniform vec4 u_diffuseColor;
#endif
#ifdef diffuseTextureFlag
uniform sampler2D u_diffuseTexture;
#endif
#ifdef blendedFlag
uniform float u_opacity;
#ifdef alphaTestFlag
uniform float u_alphaTest;
#endif
#endif

uniform vec4 u_cameraPosition;

// Lights (see LightRig).
const int MAX_LIGHTS = 8;
uniform vec4 u_lightPos[MAX_LIGHTS];
uniform vec4 u_lightDir[MAX_LIGHTS];
uniform vec4 u_lightColor[MAX_LIGHTS];
uniform float u_lightCount;
uniform vec3 u_sky;
uniform vec3 u_ground;
// Distance haze: rgb colour, a = density (0 = none).
uniform vec4 u_fog;

// Shadow maps from the dominant light: the venue (stands, crowd, props) and, finer, the court
// (players, ball, net, cable camera). Both have the same size.
uniform sampler2D u_shadowMap;
uniform mat4 u_shadowMatrix;
uniform sampler2D u_courtShadowMap;
uniform mat4 u_courtShadowMatrix;
uniform float u_shadowOn;
uniform float u_shadowTexel;

// Occluders: capsule i from u_capA[i].xyz to u_capB[i].xyz with radius u_capA[i].w.
const int MAX_CAPSULES = 24;
uniform vec4 u_capA[MAX_CAPSULES];
uniform vec4 u_capB[MAX_CAPSULES];
uniform float u_capCount;
uniform vec3 u_shadowDir;

// x reflectivity, y shininess, z specular strength, w rim strength.
uniform vec4 u_surface;
// x floor pattern (0 none, 1 sports floor, 2 wood, 3 sand), y roughness of the reflection.
uniform vec2 u_surfaceDetail;
uniform sampler2D u_reflection;
uniform vec2 u_screenSize;
uniform float u_reflectionOn;
// x, y unused, z exposure, w occlusion strength (0 = off).
uniform vec4 u_arena;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

vec3 closestOnSegment(vec3 p, vec3 a, vec3 b) {
    vec3 ab = b - a;
    float t = clamp(dot(p - a, ab) / max(dot(ab, ab), 1e-5), 0.0, 1.0);
    return a + ab * t;
}

// Soft shadow of a sphere along the ray ro + t * rd (Inigo Quilez).
float sphereSoftShadow(vec3 ro, vec3 rd, vec3 c, float r, float k) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float cc = dot(oc, oc) - r * r;
    float h = b * b - cc;
    float d = sqrt(max(0.0, r * r - h)) - r;
    float t = -b - sqrt(max(h, 0.0));
    return t < 0.0 ? 1.0 : smoothstep(0.0, 1.0, 2.5 * k * d / t);
}

float sphereOcclusion(vec3 p, vec3 n, vec3 c, float r) {
    vec3 di = c - p;
    float l = length(di);
    float h = l / r;
    return max(0.0, dot(n, di / l)) / (h * h);
}

// x: soft shadow, y: ambient occlusion from the capsules.
vec2 capsuleShading(vec3 p, vec3 n, bool shadows) {
    float shadow = 1.0;
    float occlusion = 0.0;
    // Players and the ball stay around the court: nothing to do in the stands.
    if (abs(p.x) > 17.0 || abs(p.z) > 14.0 || p.y > 7.0) return vec2(1.0);
    for (int i = 0; i < MAX_CAPSULES; i++) {
        if (float(i) >= u_capCount) break;
        vec3 a = u_capA[i].xyz;
        vec3 b = u_capB[i].xyz;
        float r = u_capA[i].w;
        vec3 axisPoint = closestOnSegment(p, a, b);
        float dist = length(p - axisPoint);
        if (dist < r * 1.15 || dist > 6.0) continue;
        occlusion += sphereOcclusion(p, n, axisPoint, r);
        if (shadows) {
            vec3 along = closestOnSegment(p + u_shadowDir * dot(axisPoint - p, u_shadowDir), a, b);
            shadow *= sphereSoftShadow(p + n * 0.01, u_shadowDir, along, r, 5.0);
        }
    }
    return vec2(shadow, clamp(1.0 - occlusion * 0.9, 0.0, 1.0));
}

float unpackDepth(vec4 c) {
    return dot(c, vec4(1.0, 1.0 / 255.0, 1.0 / 65025.0, 1.0 / 16581375.0));
}

// Fraction of light reaching p: 3 x 3 percentage-closer filtering with bilinear weights (the
// depth comparisons of a 4 x 4 texel block blended by the sub-texel position), so shadow edges
// are smooth gradients instead of texel steps.
float shadowMap(sampler2D map, mat4 matrix, vec3 p, vec3 n) {
    vec4 sc = matrix * vec4(p + n * 0.05, 1.0);
    vec3 c = sc.xyz / sc.w * 0.5 + 0.5;
    if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
    // The shadow camera's depth range is under 140 m: 0.0005 is under 7 cm.
    float ref = c.z - 0.0005;
    vec2 texel = c.xy / u_shadowTexel - 0.5;
    vec2 base = floor(texel);
    vec2 f = texel - base;
    float lit = 0.0;
    for (int y = 0; y < 4; y++) {
        float s0 = step(ref, unpackDepth(texture2D(map, (base + vec2(-0.5, float(y) - 0.5)) * u_shadowTexel)));
        float s1 = step(ref, unpackDepth(texture2D(map, (base + vec2(0.5, float(y) - 0.5)) * u_shadowTexel)));
        float s2 = step(ref, unpackDepth(texture2D(map, (base + vec2(1.5, float(y) - 0.5)) * u_shadowTexel)));
        float s3 = step(ref, unpackDepth(texture2D(map, (base + vec2(2.5, float(y) - 0.5)) * u_shadowTexel)));
        // Three bilinear cells along x, weighted by the sub-texel position.
        float r = mix(s0, s1, f.x) + s1 * (1.0 - f.x) + s2 * f.x + mix(s2, s3, f.x);
        float w = (y == 0) ? 1.0 - f.y : (y == 3) ? f.y : 1.0;
        lit += r * w;
    }
    return lit / 9.0;
}

// One bilinear percentage-closer lookup (4 texels): for the spectators.
float shadowMapFast(sampler2D map, mat4 matrix, vec3 p, vec3 n) {
    vec4 sc = matrix * vec4(p + n * 0.05, 1.0);
    vec3 c = sc.xyz / sc.w * 0.5 + 0.5;
    if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
    float ref = c.z - 0.0005;
    vec2 texel = c.xy / u_shadowTexel - 0.5;
    vec2 base = floor(texel);
    vec2 f = texel - base;
    float s0 = step(ref, unpackDepth(texture2D(map, (base + vec2(0.5, 0.5)) * u_shadowTexel)));
    float s1 = step(ref, unpackDepth(texture2D(map, (base + vec2(1.5, 0.5)) * u_shadowTexel)));
    float s2 = step(ref, unpackDepth(texture2D(map, (base + vec2(0.5, 1.5)) * u_shadowTexel)));
    float s3 = step(ref, unpackDepth(texture2D(map, (base + vec2(1.5, 1.5)) * u_shadowTexel)));
    return mix(mix(s0, s1, f.x), mix(s2, s3, f.x), f.y);
}

vec3 filmic(vec3 x) {
    vec3 aces = clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
    return mix(clamp(x, 0.0, 1.0), aces, 0.55);
}

void main() {
    vec4 albedo = v_color;
#ifdef diffuseColorFlag
    albedo *= u_diffuseColor;
#endif
#ifdef diffuseTextureFlag
    albedo *= texture2D(u_diffuseTexture, v_uv);
#endif
    float alpha = albedo.a;
#ifdef blendedFlag
    alpha *= u_opacity;
#ifdef alphaTestFlag
    if (alpha <= u_alphaTest) discard;
#endif
#endif

#ifdef lightingFlag
    vec3 n = normalize(v_normal);
    if (!gl_FrontFacing) n = -n;
    vec3 toEye = u_cameraPosition.xyz - v_worldPos;
    float eyeDistance = length(toEye);
    vec3 v = toEye / eyeDistance;

    // Procedural floor detail.
    float pattern = u_surfaceDetail.x;
    if (pattern > 0.5 && n.y > 0.9) {
        vec2 q = v_worldPos.xz;
        if (pattern < 1.5) {
            float grain = noise(q * 22.0) * 0.6 + noise(q * 3.0) * 0.4;
            albedo.rgb *= 0.95 + 0.08 * grain;
        } else if (pattern < 2.5) {
            float plank = floor(q.y / 0.12);
            float board = floor((q.x + hash(vec2(plank, 3.0)) * 2.0) / 1.6);
            float tone = hash(vec2(plank, board));
            float f = fract(q.y / 0.12);
            float seam = smoothstep(0.0, 0.05, f) * smoothstep(0.0, 0.05, 1.0 - f);
            float streak = noise(vec2(q.x * 1.3, q.y * 40.0));
            albedo.rgb *= (0.88 + 0.16 * tone) * (0.94 + 0.1 * streak) * mix(0.82, 1.0, seam);
        } else {
            // Sand: fine grains, soft wind ripples and patches.
            float grain = hash(floor(q * 180.0));
            float ripple = sin(q.x * 3.1 + noise(q * 0.7) * 6.0) * 0.5 + 0.5;
            float patches = noise(q * 0.35);
            albedo.rgb *= (0.88 + 0.14 * grain) * (0.94 + 0.06 * ripple) * (0.9 + 0.12 * patches);
        }
    }

    // Ambient: hemisphere light from the sky / roof above and the floor's bounce below.
    vec3 ambient = mix(u_ground, u_sky, n.y * 0.5 + 0.5);

    bool hasShadowMap = u_shadowOn > 0.5;
    vec2 caps = vec2(1.0);
#ifdef crowdFlag
    float shadow = hasShadowMap ? shadowMapFast(u_shadowMap, u_shadowMatrix, v_worldPos, n) : 1.0;
#else
    if (u_arena.w > 0.0) caps = mix(vec2(1.0), capsuleShading(v_worldPos, n, !hasShadowMap), u_arena.w);
    float shadow = caps.x;
    if (hasShadowMap) {
        shadow = shadowMap(u_shadowMap, u_shadowMatrix, v_worldPos, n);
        if (abs(v_worldPos.x) < 24.0 && abs(v_worldPos.z) < 20.0 && v_worldPos.y < 8.0) {
            shadow = min(shadow, shadowMap(u_courtShadowMap, u_courtShadowMatrix, v_worldPos, n));
        }
    }
#endif

    vec3 diffuse = vec3(0.0);
    vec3 specular = vec3(0.0);
    for (int i = 0; i < MAX_LIGHTS; i++) {
        if (float(i) >= u_lightCount) break;
        vec4 lp = u_lightPos[i];
        vec3 l;
        float attenuation = 1.0;
        if (lp.w < 0.5) {
            l = normalize(lp.xyz);
        } else {
            vec3 d = lp.xyz - v_worldPos;
            float dist2 = max(dot(d, d), 0.01);
            l = d * inversesqrt(dist2);
            // Inverse-square falloff, normalised at the light's reference distance.
            float reference = u_lightColor[i].w;
            attenuation = min(reference * reference / dist2, 3.0);
            // Beam cone with a soft edge.
            float cosAngle = dot(-l, u_lightDir[i].xyz);
            float outer = u_lightDir[i].w;
            if (cosAngle < outer) continue;
            attenuation *= smoothstep(outer, outer + (1.0 - outer) * 0.4, cosAngle);
        }
        float ndl = max(dot(n, l), 0.0);
        vec3 light = u_lightColor[i].rgb * attenuation;
        diffuse += light * ndl;
#ifndef crowdFlag
        vec3 h = normalize(l + v);
        specular += light * pow(max(dot(n, h), 0.0), u_surface.y) * u_surface.z * step(1e-4, ndl);
#endif
    }
    vec3 color = albedo.rgb * (ambient * caps.y + diffuse * shadow) + specular * shadow;

    // Rim light: the bright venue behind outlines bodies.
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    color += rim * u_surface.w * (u_sky * 1.6 + 0.12) * caps.y;

    // Reflection of the venue in the floor.
    if (u_reflectionOn > 0.5 && u_surface.x > 0.0 && n.y > 0.9) {
        vec2 uv = gl_FragCoord.xy / u_screenSize;
        uv.x = 1.0 - uv.x;
        vec2 px = u_surfaceDetail.y / u_screenSize;
        vec3 reflection = texture2D(u_reflection, uv).rgb * 0.4
                + texture2D(u_reflection, uv + vec2(px.x, 0.0)).rgb * 0.15
                + texture2D(u_reflection, uv - vec2(px.x, 0.0)).rgb * 0.15
                + texture2D(u_reflection, uv + vec2(0.0, px.y)).rgb * 0.15
                + texture2D(u_reflection, uv - vec2(0.0, px.y)).rgb * 0.15;
        float fresnel = 0.08 + 0.92 * pow(1.0 - max(dot(n, v), 0.0), 5.0);
        float amount = clamp(u_surface.x * fresnel * 1.25, 0.0, 0.42);
        color = mix(color, reflection * 0.85, amount);
    }

    color *= u_arena.z;
    color = filmic(color);
    // Distance haze (open air): far objects fade into the sky colour.
    if (u_fog.a > 0.0) color = mix(color, u_fog.rgb, clamp(1.0 - exp(-eyeDistance * u_fog.a), 0.0, 0.85));
    gl_FragColor = vec4(color, alpha);
#else
    // Unlit (emissive) surfaces: floodlights, screens, LED boards, sky.
    gl_FragColor = vec4(albedo.rgb, alpha);
#endif
}
