# Copper Oxide native renderer build (GitHub Actions only, never local).
cmake_minimum_required(VERSION 3.22.1)
project("copperoxide")
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
FetchContent_Declare(xxhash
  URL https://codeload.github.com/stbrumme/xxhash/zip/c2866db364b6ea3a11933e62235ddc166ba18565)
FetchContent_MakeAvailable(xxhash)
FetchContent_Declare(flat_hash_map
  URL https://codeload.github.com/MobileGL-Dev/flat_hash_map/zip/21c1cec95abee1beef827e4a7c95f692875d9594)
FetchContent_MakeAvailable(flat_hash_map)
file(MAKE_DIRECTORY ${CMAKE_BINARY_DIR}/copperoxide-staged-includes/ska)
file(COPY ${flat_hash_map_SOURCE_DIR}/flat_hash_map.hpp
  DESTINATION ${CMAKE_BINARY_DIR}/copperoxide-staged-includes/ska)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -w -fvisibility=hidden -funwind-tables -g -D_THREAD_SAFE -fPIC")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -w -g -std=gnu99 -funwind-tables -fvisibility=hidden")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -static-libstdc++")
add_compile_definitions(COPPER_OXIDE_BUILD=1)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    include(CheckIPOSupported)
    include(CheckCCompilerFlag)
    include(CheckCXXCompilerFlag)
    check_ipo_supported(RESULT LTOSupported OUTPUT LTOError)
    check_c_compiler_flag("-flto" HAS_LTO_C)
    check_cxx_compiler_flag("-flto" HAS_LTO_CXX)
    if (LTOSupported OR (HAS_LTO_C AND HAS_LTO_CXX))
        check_c_compiler_flag("-flto=thin" HAS_THINLTO_C)
        check_cxx_compiler_flag("-flto=thin" HAS_THINLTO_CXX)
        if (HAS_THINLTO_C AND HAS_THINLTO_CXX)
            add_compile_options(-flto=thin)
            add_link_options(-flto=thin)
        else()
            set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)
        endif()
    endif()
endif()
if (CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    add_compile_options(-O3 -ffunction-sections -fdata-sections)
    add_link_options(-Wl,--gc-sections)
else ()
    add_compile_options(-O2)
endif()
set(UPSTREAM ${CMAKE_CURRENT_SOURCE_DIR}/upstream/MobileGlues-cpp)
add_library(${CMAKE_PROJECT_NAME} SHARED
    ${UPSTREAM}/init.cpp
    ${UPSTREAM}/main.cpp
    ${UPSTREAM}/gl/gl_stub.cpp
    ${UPSTREAM}/gl/gl_native.cpp
    ${UPSTREAM}/gl/gl.cpp
    ${UPSTREAM}/gl/envvars.cpp
    ${UPSTREAM}/gl/log.cpp
    ${UPSTREAM}/gl/program.cpp
    ${UPSTREAM}/gl/shader.cpp
    ${UPSTREAM}/gl/framebuffer.cpp
    ${UPSTREAM}/gl/texture.cpp
    ${UPSTREAM}/gl/drawing.cpp
    ${UPSTREAM}/gl/multidraw.cpp
    ${UPSTREAM}/bench/multidraw_bench.cpp
    ${UPSTREAM}/gl/mg.cpp
    ${UPSTREAM}/gl/buffer.cpp
    ${UPSTREAM}/gl/getter.cpp
    ${UPSTREAM}/gl/enable.cpp
    ${UPSTREAM}/gl/restart.cpp
    ${UPSTREAM}/egl/context.cpp
    ${UPSTREAM}/gl/pixel.cpp
    ${UPSTREAM}/gl/transfer.cpp
    ${UPSTREAM}/gl/random_string_gen.cpp
    ${UPSTREAM}/gl/ExtWrappers/DSAWrapper.cpp
    ${UPSTREAM}/gl/ExtWrappers/MultiBindWrapper.cpp
    ${UPSTREAM}/gl/glsl/glsl_for_es.cpp
    ${UPSTREAM}/gl/glsl/cache.cpp
    ${UPSTREAM}/gl/FSR1/FSR1.cpp
    ${UPSTREAM}/gl/vertexattrib.cpp
    ${UPSTREAM}/glx/lookup.cpp
    ${UPSTREAM}/egl/egl.cpp
    ${UPSTREAM}/egl/loader.cpp
    ${UPSTREAM}/gles/loader.cpp
    ${UPSTREAM}/config/cJSON.c
    ${UPSTREAM}/config/config.cpp
    ${UPSTREAM}/config/gpu_utils.cpp
    ${UPSTREAM}/config/settings.cpp
    ${UPSTREAM}/config/stats.cpp
)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${UPSTREAM}/includes
    ${xxhash_SOURCE_DIR}
    ${CMAKE_BINARY_DIR}/copperoxide-staged-includes)
target_include_directories(${CMAKE_PROJECT_NAME} PUBLIC ${UPSTREAM}/include)
target_link_libraries(${CMAKE_PROJECT_NAME} PRIVATE
    glslang::glslang
    spirv-cross-c
)
if (NOT MSVC)
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE -Wl,-Bsymbolic-functions)
endif()
target_link_libraries(${CMAKE_PROJECT_NAME} PUBLIC android log)
