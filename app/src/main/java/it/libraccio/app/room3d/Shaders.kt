package it.libraccio.app.room3d

object Shaders {
    const val VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUV;
layout(location = 3) in float aFace;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform mat4 uNormalMat;
uniform mat4 uLightVP;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUV;
out float vFace;
out vec4 vLightPos;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = normalize(mat3(uNormalMat) * aNormal);
    vUV = aUV;
    vFace = aFace;
    vLightPos = uLightVP * w;
    gl_Position = uViewProj * w;
}
"""

    /**
     * Illuminazione: luce "da finestra" con ombre (shadow map 3x3 PCF), ambiente emisferico,
     * due luci puntiformi calde (lampade/candele), occlusione ambientale analitica dentro gli scaffali,
     * tone mapping ACES, vignettatura e grana leggera.
     */
    const val FRAGMENT = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUV;
in float vFace;
in vec4 vLightPos;

uniform int uMode;            // 0 legno/mappatura mondo, 1 libro, 2 emissivo, 3 uv locali, 4 tinta unita
uniform sampler2D uTex;
uniform sampler2D uPages;
uniform sampler2DShadow uShadow;
uniform vec3 uTint;
uniform float uTexScale;
uniform int uGrainVertical;
uniform int uHasTex;
uniform vec4 uSpineRect;
uniform vec4 uFrontRect;
uniform vec4 uBackRect;
uniform vec3 uBookDims;
uniform float uSpec;
uniform float uShininess;
uniform float uDim;
uniform float uFade;

uniform vec3 uCamPos;
uniform vec3 uKeyDir;
uniform vec3 uKeyColor;
uniform vec3 uSkyColor;
uniform vec3 uGroundColor;
uniform vec3 uLampPos;
uniform vec3 uLampColor;
uniform vec3 uLamp2Pos;
uniform vec3 uLamp2Color;

uniform vec4 uCases[4];      // x: inizio, y: fine
uniform int uCaseCount;
uniform vec4 uCaseParams;    // y0 interno, passo dei ripiani, z fondo, passo delle campate
uniform vec4 uCaseExtra;     // y cima, z fronte, spessore ripiano, -
uniform vec2 uResolution;
uniform float uExposure;

out vec4 fragColor;

vec3 toLinear(vec3 c) { return pow(c, vec3(2.2)); }

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

float shadowAt(vec3 n) {
    vec3 p = vLightPos.xyz / vLightPos.w * 0.5 + 0.5;
    if (p.z > 1.0 || p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0) return 1.0;
    float bias = max(0.0022 * (1.0 - dot(n, -uKeyDir)), 0.0005);
    vec2 texel = 1.0 / vec2(textureSize(uShadow, 0));
    float s = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            s += texture(uShadow, vec3(p.xy + vec2(float(x), float(y)) * texel * 1.25, p.z - bias));
        }
    }
    return s / 9.0;
}

float caseAO(vec3 p) {
    float ao = 1.0;
    for (int i = 0; i < 4; i++) {
        if (i >= uCaseCount) break;
        vec4 c = uCases[i];
        if (p.x > c.x && p.x < c.y && p.y > uCaseParams.x && p.y < uCaseExtra.x && p.z < uCaseExtra.y - 0.004) {
            float ly = mod(p.y - uCaseParams.x, uCaseParams.y);
            float top = uCaseParams.y - uCaseExtra.z - ly;
            ao *= mix(0.32, 1.0, smoothstep(0.0, 0.16, top));
            ao *= mix(0.72, 1.0, smoothstep(0.0, 0.02, ly));
            float back = p.z - uCaseParams.z;
            ao *= mix(0.4, 1.0, smoothstep(0.0, 0.22, back));
            float lx = mod(p.x - c.x, uCaseParams.w);
            float side = min(lx, uCaseParams.w - lx);
            ao *= mix(0.6, 1.0, smoothstep(0.0, 0.08, side));
        }
    }
    return ao;
}

vec3 pointLight(vec3 lp, vec3 lc, vec3 n, vec3 v, vec3 albedo) {
    vec3 d = lp - vWorld;
    float dist2 = dot(d, d);
    vec3 l = d * inversesqrt(dist2);
    float ndl = max(dot(n, l), 0.0);
    vec3 h = normalize(l + v);
    float sp = pow(max(dot(n, h), 0.0), uShininess) * uSpec;
    return (albedo * ndl + sp * ndl) * lc / (dist2 + 0.08);
}

void main() {
    vec3 n = normalize(vNormal);
    vec3 albedo;

    if (uMode == 2) {
        vec3 c = aces(uTint * uExposure) * uFade;
        fragColor = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
        return;
    }

    if (uMode == 1) {
        int f = int(vFace + 0.5);
        vec3 spine = toLinear(texture(uTex, uSpineRect.xy + vUV * uSpineRect.zw).rgb);
        vec3 front = toLinear(texture(uTex, uFrontRect.xy + vUV * uFrontRect.zw).rgb);
        vec3 backC = toLinear(texture(uTex, uBackRect.xy + vUV * uBackRect.zw).rgb);
        vec2 puv = vec2(vUV.x * uBookDims.x * 260.0, vUV.y * 3.0);
        vec3 pages = toLinear(texture(uPages, puv).rgb);
        if (uHasTex == 0) { spine = uTint; front = uTint; backC = uTint; }
        if (f == 0) albedo = spine;
        else if (f == 1) albedo = front;
        else if (f == 2) albedo = backC;
        else albedo = pages;
    } else if (uMode == 0) {
        vec3 p = vWorld * uTexScale;
        vec3 an = abs(n);
        vec2 uv;
        if (an.y > an.x && an.y > an.z) uv = p.xz;
        else if (an.x > an.z) uv = p.zy;
        else uv = p.xy;
        if (uGrainVertical == 1) uv = uv.yx;
        albedo = toLinear(texture(uTex, uv).rgb) * uTint;
    } else if (uMode == 3) {
        albedo = toLinear(texture(uTex, vUV).rgb) * uTint;
    } else {
        albedo = uTint;
    }

    vec3 v = normalize(uCamPos - vWorld);
    float ao = caseAO(vWorld);
    vec3 ambient = mix(uGroundColor, uSkyColor, n.y * 0.5 + 0.5) * ao;

    vec3 l = -uKeyDir;
    float ndl = max(dot(n, l), 0.0);
    float sh = shadowAt(n);
    vec3 h = normalize(l + v);
    float sp = pow(max(dot(n, h), 0.0), uShininess) * uSpec;
    float fres = pow(1.0 - max(dot(n, v), 0.0), 5.0) * uSpec * 0.6;

    vec3 color = albedo * ambient;
    color += (albedo * ndl + sp * ndl) * uKeyColor * sh * mix(0.55, 1.0, ao);
    float lampAO = mix(0.35, 1.0, ao);
    color += pointLight(uLampPos, uLampColor, n, v, albedo) * lampAO;
    color += pointLight(uLamp2Pos, uLamp2Color, n, v, albedo) * lampAO;
    color += fres * uSkyColor;

    color *= uDim * uExposure;
    color = aces(color);

    vec2 sc = gl_FragCoord.xy / uResolution - 0.5;
    color *= 1.0 - dot(sc, sc) * 0.85;
    color += (hash(gl_FragCoord.xy) - 0.5) * 0.012;
    color *= uFade;
    fragColor = vec4(pow(max(color, 0.0), vec3(1.0 / 2.2)), 1.0);
}
"""

    const val DEPTH_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uModel;
uniform mat4 uLightVP;
void main() { gl_Position = uLightVP * uModel * vec4(aPos, 1.0); }
"""

    const val DEPTH_FRAGMENT = """#version 300 es
precision mediump float;
out vec4 fragColor;
void main() { fragColor = vec4(1.0); }
"""
}
