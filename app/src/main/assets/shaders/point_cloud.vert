#version 320 es
layout(location = 0) in vec3 in_position;
layout(location = 1) in float in_confidence;

uniform mat4 projection_matrix;
uniform mat4 view_projection_matrix;

layout(location = 0) flat out float out_confidence;

void main() {
    vec4 cpos = view_projection_matrix * vec4(in_position, 1f);

    float scale = min(projection_matrix[0][0], projection_matrix[1][1]);
    float point_size = max(2.0 * (scale / pow(cpos.w, 0.6)), 4.0) * 1.5;

    gl_Position = cpos;
    gl_PointSize = point_size;
    out_confidence = in_confidence;
}
