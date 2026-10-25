cmake_minimum_required(VERSION 3.22.1)
project(mobileclient LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(shadowhook REQUIRED CONFIG)

add_library(mobileclient SHARED native_bridge.cpp hooks.cpp)
target_include_directories(mobileclient PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_options(mobileclient PRIVATE -Wall -fvisibility=hidden)
target_link_libraries(mobileclient PRIVATE shadowhook::shadowhook android log)
