cmake_minimum_required(VERSION 3.15)
project(dreamdisplays_native C)

set(CMAKE_C_STANDARD 11)

# Option to enable SIMD optimizations
option(USE_SIMD "Enable SIMD optimizations (SSE2/NEON)" ON)

# Find JNI
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# Use single optimized source file
set(SOURCE_FILE src/image_converter.c)
if(USE_SIMD)
    message(STATUS "Building with SIMD optimizations...")
else()
    message(STATUS "Building without SIMD (portable mode)...")
endif()

# Create shared library
add_library(dreamdisplays_native SHARED
    ${SOURCE_FILE}
)

# Enable SIMD flags if supported
if(USE_SIMD)
    if(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|i686|i386")
        target_compile_options(dreamdisplays_native PRIVATE -msse2)
    elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "^arm$|^armv")
        target_compile_options(dreamdisplays_native PRIVATE -mfpu=neon)
    endif()
endif()

# Link JNI
target_link_libraries(dreamdisplays_native ${JNI_LIBRARIES})

# Set output name
set_target_properties(dreamdisplays_native PROPERTIES
    OUTPUT_NAME "dreamdisplays_native"
)

# Platform-specific settings
if(WIN32)
    set_target_properties(dreamdisplays_native PROPERTIES
        PREFIX ""
        SUFFIX ".dll"
    )
elseif(APPLE)
    set_target_properties(dreamdisplays_native PROPERTIES
        PREFIX "lib"
        SUFFIX ".dylib"
    )
else()
    set_target_properties(dreamdisplays_native PROPERTIES
        PREFIX "lib"
        SUFFIX ".so"
    )
endif()

# Install to resources
install(TARGETS dreamdisplays_native
    LIBRARY DESTINATION ${CMAKE_SOURCE_DIR}/../mod-common/src/main/resources/natives
    RUNTIME DESTINATION ${CMAKE_SOURCE_DIR}/../mod-common/src/main/resources/natives
)
