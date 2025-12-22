#version 320 es
precision mediump float;

layout(location = 0) in vec2 in_texcoord;

uniform sampler2D confidence_texture;

out vec4 frag_color;

const vec3 BLACK = vec3(0.0, 0.0, 0.0);
const vec3 RED = vec3(1.0, 0.35, 0.45);
const vec3 YELLOW = vec3(0.99, 1.0, 0.0);
const vec3 GREEN = vec3(0.48, 0.99, 0.0);
const vec3 CYAN = vec3(0.0, 1.0, 1.0);
const vec3 WHITE = vec3(1.0, 1.0, 1.0);

float remap(float val, float in_min, float in_max) {
    return clamp((val - in_min) / (in_max - in_min), 0.0, 1.0);
}

void main() {
    vec2 texcoord = vec2(in_texcoord.x, 1.0 - in_texcoord.y);
    float confidence = texture(confidence_texture, texcoord).r;
    float alpha = 0.4;
    if (confidence < 0.2) {
        float t = remap(confidence, 0.0, 0.2);
//        frag_color = vec4(mix(BLACK, RED, t), alpha);
        frag_color = vec4(BLACK, alpha);
    } else if (confidence < 0.5) {
        float t = remap(confidence, 0.2, 0.5);
//        frag_color = vec4(mix(RED, YELLOW, t), alpha);
        frag_color = vec4(BLACK, alpha);
    } else if (confidence < 0.7) {
        float t = remap(confidence, 0.5, 0.7);
        frag_color = vec4(mix(YELLOW, GREEN, t), alpha);
    } else if (confidence < 0.9) {
        float t = remap(confidence, 0.7, 0.9);
        frag_color = vec4(mix(GREEN, CYAN, t), alpha);
    } else {
        float t = remap(confidence, 0.9, 1.0);
        frag_color = vec4(mix(CYAN, WHITE, t), alpha);
    }
}
