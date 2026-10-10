// ONIGAMI translator regression test (host-runnable, real code under test).
//
// The vertex/fragment sources below are the actual minecraft:core/gui shaders
// from the Minecraft 1.21.4 client assets (Mojang AB; reproduced here solely
// for renderer compatibility testing): the exact pipeline whose vertex shader
// failed on-device with "ERROR: Invalid #version". The 26.x gui pipeline has
// the same shape (desktop #version 150, separate attributes, mat4 uniforms),
// so these cases pin the constructs that must translate.
//
// What failed on-device was NOT the translation text but dispatch: LWJGL on
// EGL resolves GL entry points through eglGetProcAddress, which answered with
// backend pointers and bypassed translation entirely, delivering raw
// "#version 150" to the Adreno GLES compiler. That dispatch defect is fixed
// in src/proc_table.cpp; these tests pin the translation half so neither
// half can regress silently.
#include "onigami/translate.h"
#include <cstdio>
#include <cstring>
#include <string>
namespace {
int g_fail = 0;
void check(bool cond, const char* name) {
    if (cond) {
        printf("ok: %s\n", name);
    } else {
        printf("FAIL: %s\n", name);
        g_fail++;
    }
}
bool has_word(const std::string& s, const char* w) {
    size_t n = strlen(w);
    for (size_t i = 0; i + n <= s.size(); i++) {
        bool before = (i == 0) || (!isalnum((unsigned char)s[i - 1]) && s[i - 1] != '_');
        bool after = (i + n >= s.size()) || (!isalnum((unsigned char)s[i + n]) && s[i + n] != '_');
        if (before && after && s.compare(i, n, w) == 0) return true;
    }
    return false;
}
size_t count_occurrences(const std::string& s, const char* sub) {
    size_t n = 0, p = 0;
    while ((p = s.find(sub, p)) != std::string::npos) {
        n++;
        p++;
    }
    return n;
}
// Real minecraft:core/gui vertex shader (1.21.4 client assets, Mojang AB).
const char* kGuiVert = "#version 150\n\nin vec3 Position;\nin vec4 Color;\n\nuniform mat4 ModelViewMat;\nuniform mat4 ProjMat;\n\nout vec4 vertexColor;\n\nvoid main() {\n    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n\n    vertexColor = Color;\n}\n";
// Real minecraft:core/gui fragment shader (1.21.4 client assets, Mojang AB).
const char* kGuiFrag = "#version 150\n\nin vec4 vertexColor;\n\nuniform vec4 ColorModulator;\n\nout vec4 fragColor;\n\nvoid main() {\n    vec4 color = vertexColor;\n    if (color.a == 0.0) {\n        discard;\n    }\n    fragColor = color * ColorModulator;\n}\n";
// Legacy desktop shader (pre-1.17 style / older mods): attribute + varying +
// texture2D + gl_FragColor must be migrated, never passed through.
const char* kLegacyFrag = "#version 120\n\nvarying vec2 texCoord;\nuniform sampler2D Sampler;\n\nvoid main() {\n    vec4 c = texture2D(Sampler, texCoord);\n    gl_FragColor = c;\n}\n";
const char* kLegacyVert = "#version 120\n\nattribute vec3 Position;\nattribute vec2 UV;\nvarying vec2 texCoord;\n\nvoid main() {\n    texCoord = UV;\n    gl_Position = vec4(Position, 1.0);\n}\n";
} // namespace
int main() {
    using onigami::translate_shader;
    // 1. Real gui vertex shader: translates, single leading ESSL version.
    {
        onigami::TranslateResult r = translate_shader(GL_VERTEX_SHADER, kGuiVert);
        check(r.ok && !r.passthrough, "gui vertex translates (not passthrough)");
        check(r.essl.compare(0, 15, "#version 310 es") == 0, "gui vertex starts with ESSL version");
        check(count_occurrences(r.essl, "#version") == 1, "gui vertex has exactly one #version");
        check(r.essl.find("precision highp float;") != std::string::npos, "gui vertex has float precision");
        check(r.essl.find("precision highp int;") != std::string::npos, "gui vertex has int precision");
        size_t vpos = r.essl.find("#version");
        size_t ppos = r.essl.find("precision highp float;");
        check(ppos != std::string::npos && ppos > vpos, "precision comes after #version");
        check(r.essl.find("in vec3 Position;") != std::string::npos, "gui vertex keeps attributes");
        check(r.essl.find("ModelViewMat") != std::string::npos, "gui vertex keeps uniforms");
        check(r.essl.find("#version 150") == std::string::npos, "no desktop version remains");
        check(r.essl.find('\r') == std::string::npos, "no CR bytes in output");
    }
    // 2. Real gui fragment shader.
    {
        onigami::TranslateResult r = translate_shader(GL_FRAGMENT_SHADER, kGuiFrag);
        check(r.ok && !r.passthrough, "gui fragment translates");
        check(r.essl.compare(0, 15, "#version 310 es") == 0, "gui fragment starts with ESSL version");
        check(count_occurrences(r.essl, "#version") == 1, "gui fragment has exactly one #version");
        check(r.essl.find("out vec4 fragColor;") != std::string::npos, "gui fragment keeps outputs");
        check(r.essl.find("discard;") != std::string::npos, "gui fragment keeps discard");
    }
    // 3. Legacy migration.
    {
        onigami::TranslateResult v = translate_shader(GL_VERTEX_SHADER, kLegacyVert);
        onigami::TranslateResult f = translate_shader(GL_FRAGMENT_SHADER, kLegacyFrag);
        check(v.ok && f.ok, "legacy shaders translate");
        check(!has_word(v.essl, "attribute") && !has_word(v.essl, "varying"), "legacy vertex migrates qualifiers");
        check(!has_word(f.essl, "varying"), "legacy fragment migrates varying");
        check(!has_word(f.essl, "texture2D"), "legacy texture2D migrated");
        check(!has_word(f.essl, "gl_FragColor"), "legacy gl_FragColor migrated");
        check(f.essl.find("onigami_fragColor") != std::string::npos, "legacy fragColor output declared");
        check(count_occurrences(f.essl, "#version") == 1, "legacy fragment has exactly one #version");
    }
    // 4. ESSL passthrough is byte-identical and never re-translated.
    {
        const char* essl = "#version 310 es\nprecision highp float;\nvoid main() {}\n";
        onigami::TranslateResult r = translate_shader(GL_VERTEX_SHADER, essl);
        check(r.ok && r.passthrough && r.essl == essl, "ESSL passes through untouched");
    }
    // 5. Failures are honest, never silent miscompiles.
    {
        check(!translate_shader(GL_VERTEX_SHADER, "").ok, "empty source fails");
        check(!translate_shader(GL_VERTEX_SHADER, nullptr).ok, "null source fails");
        check(!translate_shader(0x91B9u, kGuiVert).ok, "desktop compute fails loudly (staged)");
        check(!translate_shader(0x8DD9u, kGuiVert).ok, "unknown stage fails loudly");
    }
    // 6. CRLF assets cannot corrupt directive placement.
    {
        std::string crlf = std::string(kGuiVert);
        std::string with_cr;
        for (char c : crlf) {
            with_cr += c;
            if (c == '\n') with_cr += '\r';
        }
        // Note: \r AFTER \n simulates stray CRs; translator strips trailing CR per line.
        onigami::TranslateResult r = translate_shader(GL_VERTEX_SHADER, with_cr.c_str());
        check(r.ok && r.essl.compare(0, 15, "#version 310 es") == 0, "CRLF input keeps version first");
    }
    // 7. Buffer-sampler constructs (minecraft:core/clouds shape, 26.x):
    //    desktop isamplerBuffer + texelFetch must gain the EXT directive
    //    (exactly once, after #version, before use) plus sampler precision.
    //    (Constructed to mirror the 26.3 clouds constructs from the device
    //    log; byte-exact 26.x sources arrive via the on-device capture.)
    {
        const char* cloudsVert =
            "#version 150\n\nin vec3 Position;\n\nuniform mat4 ModelViewMat;\nuniform mat4 ProjMat;\nuniform isamplerBuffer DataSampler;\n\nout vec4 vertexColor;\n\nvoid main() {\n    ivec4 data = texelFetch(DataSampler, 0);\n    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);\n    vertexColor = vec4(float(data.x) / 255.0);\n}\n";
        onigami::TranslateResult r = translate_shader(GL_VERTEX_SHADER, cloudsVert);
        check(r.ok && !r.passthrough, "clouds-like vertex translates");
        check(r.needsTexBufferExt, "clouds-like vertex flags texture-buffer need");
        check(count_occurrences(r.essl, "GL_EXT_texture_buffer") == 1, "exactly one texture-buffer extension directive");
        size_t vpos = r.essl.find("#version 310 es");
        size_t epos = r.essl.find("#extension GL_EXT_texture_buffer : require");
        size_t upos = r.essl.find("uniform isamplerBuffer DataSampler;");
        check(epos != std::string::npos && epos > vpos && upos != std::string::npos && epos < upos,
            "extension directive sits between version and declaration");
        check(r.essl.find("precision highp isamplerBuffer;") != std::string::npos, "isamplerBuffer precision declared");
        check(r.essl.find("texelFetch(DataSampler, 0)") != std::string::npos, "texelFetch call preserved");
        check(count_occurrences(r.essl, "#version") == 1, "clouds-like output has exactly one #version");
    }
    // 8. Shaders without buffer samplers must NOT gain the directive.
    {
        onigami::TranslateResult r = translate_shader(GL_VERTEX_SHADER, kGuiVert);
        check(!r.needsTexBufferExt, "gui vertex has no texture-buffer need");
        check(r.essl.find("GL_EXT_texture_buffer") == std::string::npos, "gui output gains no extension directive");
    }
    if (g_fail == 0) {
        printf("ALL TRANSLATOR TESTS PASSED\n");
        return 0;
    }
    printf("%d TEST(S) FAILED\n", g_fail);
    return 1;
}
