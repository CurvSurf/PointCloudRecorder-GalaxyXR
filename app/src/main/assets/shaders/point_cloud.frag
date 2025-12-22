#version 320 es
precision mediump float;

layout(location = 0) flat in float in_confidence;

out vec4 frag_color;

const vec3 RED = vec3(1.0, 0.35, 0.45);
const vec3 ORANGE = vec3(1.0, 0.68, 0.0);
const vec3 YELLOW = vec3(0.99, 1.0, 0.0);
const vec3 YELLOW_GREEN = vec3(0.78, 1.0, 0.22);
const vec3 GREEN = vec3(0.48, 0.99, 0.0);
const vec3 COLORS[5] = vec3[](
RED, ORANGE, YELLOW, YELLOW_GREEN, GREEN
);
const vec3 BLACK = vec3(0f, 0f, 0f);

void main() {

    float d = length(gl_PointCoord - vec2(0.5));
    if (d > 0.5) discard;

    vec3 color = COLORS[int(clamp(in_confidence * 5.0f, 0.0f, 4.0f))];

    float ring = smoothstep(0.5, 0.45, d);
    frag_color = vec4(mix(color, BLACK, d > 0.4), ring);
}
