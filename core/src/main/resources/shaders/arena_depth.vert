// Shadow map pass (vertex): positions as seen from the shadow-casting light, with the same crowd
// animation as the colour pass (crowd.glsl is prepended).
#ifdef GL_ES
precision highp float;
#endif

attribute vec3 a_position;
uniform mat4 u_worldTrans;
uniform mat4 u_projViewTrans;

#ifdef crowdFlag
attribute vec2 a_texCoord0;
#endif

void main() {
    vec4 world = u_worldTrans * vec4(a_position, 1.0);
#ifdef crowdFlag
    world.xyz = animateCrowd(world.xyz, a_texCoord0);
#endif
    gl_Position = u_projViewTrans * world;
}
