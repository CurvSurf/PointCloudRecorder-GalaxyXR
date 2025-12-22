#version 320 es
precision mediump float;

layout(location = 0) in vec2 in_texcoord;

uniform sampler2D depth_texture;

uniform float max_depth;

out vec4 frag_color;

void main() {
    vec2 texcoord = vec2(in_texcoord.x, 1.0 - in_texcoord.y);
    float depth = texture(depth_texture, texcoord).r;

    float normalized_depth = clamp(depth / max_depth, 0.0, 1.0);
    float brightness = 1.0 - normalized_depth;

    frag_color = vec4(vec3(brightness), 0.4);
}
