# Silica native backend build (GitHub Actions only, never local).
# Produces libsilica.so for 4 ABIs. No MobileGlues sources, no copperoxide lib.
cmake_minimum_required(VERSION 3.22.1)
project("silica")
enable_language(CXX)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
include(FetchContent)
set(ENABLE_RTTI             ON  CACHE BOOL "" FORCE)
set(ENABLE_HLSL             OFF CACHE BOOL "" FORCE)
set(BUILD_EXTERNAL          OFF CACHE BOOL "" FORCE)
set(ENABLE_GLSLANG_BINARIES OFF CACHE BOOL "" FORCE)
set(ENABLE_OPT              OFF CACHE BOOL "" FORCE)
FetchContent_Declare(glslang
  URL https://codeload.github.com/KhronosGroup/glslang/zip/f5f664dee8146676b04a332a7233959fc3ce9681)
FetchContent_MakeAvailable(glslang)
set(SPIRV_CROSS_ENABLE_C_API   ON  CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_GLSL    ON  CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_REFLECT OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_HLSL    OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_MSL     OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_CPP     OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_CLI            OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_STATIC         ON  CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_UTIL    OFF CACHE BOOL "" FORCE)
FetchContent_Declare(spirv_cross
  URL https://codeload.github.com/KhronosGroup/SPIRV-Cross/zip/a0fba56c34a6700f1724bf9b751da5b488a3775c)
FetchContent_MakeAvailable(spirv_cross)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -funwind-tables -O3 -D_THREAD_SAFE -fPIC")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=gnu99 -fvisibility=hidden -O3")
add_compile_definitions(SILICA_BUILD=1)
add_library(${CMAKE_PROJECT_NAME} SHARED
    src/silica.cpp
    src/gl/state.cpp
    src/shader/pipeline.cpp
    src/cache/program_cache.cpp
    src/oxide_bridge.cpp
    src/driver/loader.cpp
    src/config/config.cpp
    src/egl/exports.cpp
    src/gl/exports.cpp
)
# Pinned third-party roots for direct header use (FetchContent targets
# also propagate their own include dirs; both are harmless together and this
# survives either layout. No version changes.)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE include ${glslang_SOURCE_DIR} ${spirv_cross_SOURCE_DIR})
# glslang-default-resource-limits provides GetDefaultResources() (same pin,
# no version change). Everything else stays on the two main libraries.
target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE glslang::glslang glslang-default-resource-limits spirv-cross-c)
if (NOT MSVC)
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE -Wl,-Bsymbolic-functions)
endif()
target_link_libraries(${CMAKE_PROJECT_NAME} PUBLIC android log EGL GLESv3)
