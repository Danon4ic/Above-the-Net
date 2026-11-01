// Shadow map pass (fragment): the depth packed into RGBA (works on every GL 2.0 device).
#ifdef GL_ES
precision highp float;
#endif

vec4 packDepth(float depth) {
    const vec4 shift = vec4(1.0, 255.0, 65025.0, 16581375.0);
    const vec4 mask = vec4(1.0 / 255.0, 1.0 / 255.0, 1.0 / 255.0, 0.0);
    vec4 c = fract(depth * shift);
    c -= c.yzww * mask;
    return c;
}

void main() {
    gl_FragColor = packDepth(gl_FragCoord.z);
}
