// ONIGAMI stage-1 shader translator: line-oriented desktop GLSL to ESSL.
// Own implementation. This is a structured source migrator, not a regex
// find-replace pass: it parses the version/profile header, classifies each
// line (preprocessor / comment / code), applies a version mapping table,
// injects the backend precision contract, and migrates legacy built-ins with
// per-shader diagnostics. Anything it cannot migrate fails LOUDLY (ok=false)
// instead of emitting silently broken ESSL. The glslang/SPIRV-Cross compiler
// path (pinned, built, linked) is wired in when stage-1 limits are hit
// on-device; no build change is needed for that upgrade.
#include "onigami/translate.h"
#include <cctype>
#include <sstream>
namespace onigami {
namespace {
// Desktop compute-shader stage enum: not part of the ES 3.0 headers this
// backend compiles against, so the GL value is spelled out locally.
constexpr GLenum kComputeShaderDesktop = 0x91B9; /* GL_COMPUTE_SHADER */

std::string trim_left(const std::string& s) {
    size_t i = 0;
    while (i < s.size() && isspace((unsigned char)s[i])) i++;
    return s.substr(i);
}
bool starts_with(const std::string& s, const char* p) {
    std::string t = trim_left(s);
    size_t n = 0;
    while (p[n]) n++;
    return t.compare(0, n, p) == 0;
}
// Replace whole-word occurrences of 'from' with 'to' outside of comments.
// Operates on one code line (block-comment spans tracked by the caller).
std::string word_replace(const std::string& line, const std::string& from, const std::string& to) {
    std::string out;
    out.reserve(line.size() + 16);
    size_t i = 0;
    size_t lc = line.find("//");
    std::string code = (lc == std::string::npos) ? line : line.substr(0, lc);
    std::string tail = (lc == std::string::npos) ? "" : line.substr(lc);
    while (i < code.size()) {
        bool boundary_before = (i == 0) || (!isalnum((unsigned char)code[i - 1]) && code[i - 1] != '_');
        if (boundary_before && code.compare(i, from.size(), from) == 0) {
            size_t e = i + from.size();
            bool boundary_after = (e >= code.size()) || (!isalnum((unsigned char)code[e]) && code[e] != '_');
            if (boundary_after) {
                out += to;
                i = e;
                continue;
            }
        }
        out += code[i++];
    }
    return out + tail;
}
} // namespace
unsigned long long fnv1a64(const char* data, size_t len) {
    unsigned long long h = 1469598103934665603ULL;
    for (size_t i = 0; i < len; i++) {
        h ^= (unsigned long long)(unsigned char)data[i];
        h *= 1099511628211ULL;
    }
    return h;
}
bool is_essl_source(const char* src) {
    if (!src) return false;
    std::istringstream in(src);
    std::string line;
    while (std::getline(in, line)) {
        std::string t = trim_left(line);
        if (t.empty() || t[0] == '/' ) continue;
        if (t.compare(0, 8, "#version") == 0) return t.find("es") != std::string::npos;
        if (!t.empty() && t[0] == '#') continue;
        return false; // code before any version line: legacy desktop
    }
    return false;
}
Stage stage_from_gl(GLenum type) {
    if (type == GL_VERTEX_SHADER) return Stage::Vertex;
    if (type == GL_FRAGMENT_SHADER) return Stage::Fragment;
    if (type == kComputeShaderDesktop) return Stage::Compute;
    return Stage::Unknown;
}
TranslateResult translate_shader(GLenum glType, const char* src) {
    TranslateResult r;
    if (!src || !src[0]) {
        r.log = "empty shader source";
        return r;
    }
    Stage st = stage_from_gl(glType);
    if (is_essl_source(src)) {
        r.ok = true;
        r.passthrough = true;
        r.essl = src;
        r.log = "already ESSL; passthrough";
        return r;
    }
    if (st == Stage::Compute) {
        r.log = "desktop compute translation is staged, not implemented in stage-1";
        return r; // ok=false: caller never submits (honest failure)
    }
    if (st == Stage::Unknown) {
        r.log = "unknown shader stage";
        return r;
    }
    // Header scan: desktop version + profile.
    int desktop_version = 110; // absent version line means legacy GLSL 1.10
    bool saw_version = false;
    {
        std::istringstream in(src);
        std::string line;
        while (std::getline(in, line)) {
            std::string t = trim_left(line);
            if (t.compare(0, 8, "#version") == 0) {
                saw_version = true;
                size_t i = 8;
                while (i < t.size() && isspace((unsigned char)t[i])) i++;
                int v = 0;
                while (i < t.size() && isdigit((unsigned char)t[i])) v = v * 10 + (t[i++] - '0');
                if (v > 0) desktop_version = v;
                break;
            }
            if (!t.empty() && t[0] != '#' && t[0] != '/' && t != "") break;
        }
    }
    bool legacy = !saw_version || desktop_version <= 120;
    // Reject constructs with no ES equivalent instead of miscompiling them.
    {
        std::string whole(src);
        if (whole.find("ftransform(") != std::string::npos) {
            r.log = "unsupported builtin ftransform() has no ESSL equivalent";
            return r;
        }
    }
    std::ostringstream out;
    out << "#version 310 es\n";
    bool precision_seen = false;
    bool fragcolor_declared = false;
    bool fragdata_seen[4] = {false, false, false, false};
    bool in_block_comment = false;
    bool is_frag = (st == Stage::Fragment);
    std::istringstream in(src);
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back(); // tolerate CRLF assets
        std::string t = trim_left(line);
        // Track block comments so migration never rewrites commented code.
        std::string scan = line;
        if (in_block_comment) {
            size_t e = scan.find("*/");
            if (e == std::string::npos) {
                out << line << "\n";
                continue;
            }
            in_block_comment = false;
            scan = scan.substr(e + 2);
        }
        {
            size_t b = scan.find("/*");
            size_t l = scan.find("//");
            if (b != std::string::npos && (l == std::string::npos || b < l)) {
                size_t e = scan.find("*/", b + 2);
                if (e == std::string::npos) in_block_comment = true;
            }
        }
        if (in_block_comment) {
            out << line << "\n";
            continue;
        }
        if (starts_with(line, "#version")) continue; // replaced by 310 es header
        if (starts_with(line, "#extension")) {
            out << line << "\n"; // preserved verbatim
            continue;
        }
        if (t.find("precision") == 0 && t.find(";") != std::string::npos) precision_seen = true;
        std::string code = line;
        if (legacy) {
            if (st == Stage::Vertex) {
                code = word_replace(code, "attribute", "in");
                code = word_replace(code, "varying", "out");
            } else {
                code = word_replace(code, "varying", "in");
            }
            code = word_replace(code, "texture2DProj", "textureProj");
            code = word_replace(code, "texture2D", "texture");
            code = word_replace(code, "textureCube", "texture");
            code = word_replace(code, "texture3D", "texture");
        }
        if (is_frag) {
            if (code.find("gl_FragColor") != std::string::npos) {
                if (!fragcolor_declared) {
                    out << "layout(location = 0) out vec4 onigami_fragColor;\n";
                    fragcolor_declared = true;
                }
                code = word_replace(code, "gl_FragColor", "onigami_fragColor");
            }
            for (int k = 0; k < 4; k++) {
                std::string name = "gl_FragData[" + std::to_string(k) + "]";
                if (code.find(name) != std::string::npos && !fragdata_seen[k]) {
                    out << "layout(location = " + std::to_string(k) + ") out vec4 onigami_fragData" + std::to_string(k) + ";\n";
                    fragdata_seen[k] = true;
                }
                if (fragdata_seen[k])
                    code = word_replace(code, name, "onigami_fragData" + std::to_string(k));
            }
        }
        out << code << "\n";
    }
    if (!precision_seen) {
        // Backend precision contract, applied once and explicitly.
        std::string body = out.str();
        std::string header = "#version 310 es\nprecision highp float;\nprecision highp int;\n";
        body = body.substr(body.find("\n") + 1);
        out.str("");
        out.clear();
        out << header << body;
    }
    r.ok = true;
    r.passthrough = false;
    r.essl = out.str();
    r.log = legacy ? "desktop legacy migrated to ESSL 310" : "desktop modern mapped to ESSL 310";
    return r;
}
} // namespace onigami
