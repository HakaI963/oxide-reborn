cmake_minimum_required(VERSION 3.22.1)
project("onigami")
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
  URL https://codeload.github.com/KhronosGroup/glslang/zip/refs/tags/vulkan-sdk-1.4.363.0)
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
  URL https://codeload.github.com/KhronosGroup/SPIRV-Cross/zip/refs/tags/vulkan-sdk-1.4.363.0)
FetchContent_MakeAvailable(spirv_cross)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden -funwind-tables -O3 -D_THREAD_SAFE -fPIC")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=gnu99 -fvisibility=hidden -O3")
add_compile_definitions(ONIGAMI_BUILD=1)
if(DEFINED ONIGAMI_BUILD_COMMIT)
  add_compile_definitions(ONIGAMI_BUILD_COMMIT_STR="${ONIGAMI_BUILD_COMMIT}")
endif()
add_library(${CMAKE_PROJECT_NAME} SHARED
    src/onigami.cpp
    src/bridge.cpp
    src/config.cpp
    src/backend.cpp
    src/egl_context.cpp
    src/gl_frontend.cpp
    src/shader_translate.cpp
    src/program_store.cpp
    src/state_cache.cpp
    src/proc_table.cpp
    src/gl_longtail.cpp
)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE include ${glslang_SOURCE_DIR} ${spirv_cross_SOURCE_DIR} src)
target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE glslang::glslang glslang-default-resource-limits spirv-cross-c)
if (NOT MSVC)
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE -Wl,-Bsymbolic-functions)
endif()
target_link_libraries(${CMAKE_PROJECT_NAME} PUBLIC android log EGL GLESv3)
