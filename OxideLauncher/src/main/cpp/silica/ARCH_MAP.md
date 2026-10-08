# Reference map (studied, not copied)

Studied in cpp/copperoxide/upstream/MobileGlues-cpp to learn the Minecraft contract:
- egl/egl.cpp: context negotiation attrs, makeCurrent failure modes, error rearm need.
- gl/getter.cpp: GL_RENDERER/GL_VERSION built from backend strings; null crash site.
- gles/loader.cpp: ANGLE vs system driver selection; half-load hazard.
- gl/{program,shader}.cpp: program/link/caching expectations Iris relies on.
- gl/{framebuffer,texture,buffer}.cpp: attachment/sampler/upload lifetime rules.
- gl/{pixel,transfer,vertexattrib}.cpp: upload/attrib paths and stall sources.

Silica equivalents live in silica/{egl,gl,shader,cache}/ with own design.
No file, function, or table is copied; behavior contracts are reimplemented.
