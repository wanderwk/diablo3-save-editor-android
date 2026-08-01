cmake_minimum_required(VERSION 3.22.1)
project("storagesync")

add_library(storagesync SHARED
    storage_sync.cpp)

find_library(log-lib log)

target_link_libraries(storagesync ${log-lib})
