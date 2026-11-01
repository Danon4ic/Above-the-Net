// Arena shader (vertex): passes world position, normal, vertex colour and UVs to the per-pixel
// fragment shader, and animates the crowd (crowd.glsl is prepended by ArenaShader). Uniform and
// attribute names are those of libGDX's DefaultShader, which sets them.
#ifdef GL_ES
precision highp float;
#endif

attribute vec3 a_position;
uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;

#ifdef normalFlag
attribute vec3 a_normal;
uniform mat3 u_normalMatrix;
#endif

#ifdef colorFlag
attribute vec4 a_color;
#endif

#ifdef diffuseTextureFlag
attribute vec2 a_texCoord0;
uniform vec4 u_diffuseUVTransform;
#endif

#ifdef crowdFlag
attribute vec2 a_texCoord0;
#endif

varying vec3 v_worldPos;
varying vec3 v_normal;
varying vec4 v_color;
varying vec2 v_uv;

void main() {
    vec4 world = u_worldTrans * vec4(a_position, 1.0);
#ifdef crowdFlag
    world.xyz = animateCrowd(world.xyz, a_texCoord0);
#endif
    v_worldPos = world.xyz;
#ifdef normalFlag
    v_normal = normalize(u_normalMatrix * a_normal);
#else
    v_normal = vec3(0.0, 1.0, 0.0);
#endif
#ifdef colorFlag
    v_color = a_color;
#else
    v_color = vec4(1.0);
#endif
#ifdef diffuseTextureFlag
    v_uv = u_diffuseUVTransform.xy + a_texCoord0 * u_diffuseUVTransform.zw;
#else
    v_uv = vec2(0.0);
#endif
    gl_Position = u_projViewTrans * world;
}
