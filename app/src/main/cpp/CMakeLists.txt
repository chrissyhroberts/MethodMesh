cmake_minimum_required(VERSION 3.22.1)
project(methodmesh_native LANGUAGES C CXX)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_PYTHON_WRAPPER OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/apriltag)
add_library(methodmesh_apriltag SHARED apriltag_jni.cpp)
target_compile_features(methodmesh_apriltag PRIVATE cxx_std_17)
target_compile_options(methodmesh_apriltag PRIVATE -Wall -Wextra -Werror)
target_link_libraries(methodmesh_apriltag PRIVATE apriltag)
# NDK r28 already defaults to flexible page sizes; make the JNI DSO explicit.
target_link_options(methodmesh_apriltag PRIVATE "-Wl,-z,max-page-size=16384")

# Optional module-owned native projects, discovered by convention like Kotlin modules.
file(GLOB module_native_projects CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/../java/com/example/methodmesh/modules/*/native/CMakeLists.txt")
foreach(module_native_project IN LISTS module_native_projects)
    get_filename_component(module_native_dir "${module_native_project}" DIRECTORY)
    get_filename_component(module_dir "${module_native_dir}" DIRECTORY)
    get_filename_component(module_name "${module_dir}" NAME)
    add_subdirectory("${module_native_dir}" "${CMAKE_CURRENT_BINARY_DIR}/modules/${module_name}")
endforeach()
