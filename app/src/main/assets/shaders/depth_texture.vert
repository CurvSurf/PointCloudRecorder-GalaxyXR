#version 320 es

const ivec2 triangle_corners[6] = ivec2[6](
    ivec2(0, 0), ivec2(1, 0), ivec2(0, 1),
    ivec2(1, 0), ivec2(1, 1), ivec2(0, 1)
);

void uv_from_vertex(int vertexID, ivec2 grid, out float u, out float v) {
    int nu = grid.x;
    int nv = grid.y;

    int tri_vertex = vertexID % 6;
    int cell = vertexID / 6;
    int i = cell % nu;
    int j = cell / nu;

    ivec2 c = triangle_corners[tri_vertex];
    u = (float(i) + float(c.x)) / float(nu);
    v = (float(j) + float(c.y)) / float(nv);
}

const ivec2 grid = ivec2(1, 1);

layout(location = 0) out vec2 out_texcoord;

void main() {

    float u, v;
    uv_from_vertex(gl_VertexID, grid, u, v);

    vec2 texcoord = vec2(u, v);
    vec2 pos = texcoord * 2.0f - 1.0f;
    gl_Position = vec4(pos, 0.0f, 1.0f);
    out_texcoord = texcoord;
}