#----------------------------------------------------------------
# Generated CMake target import file.
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "raylib" for configuration ""
set_property(TARGET raylib APPEND PROPERTY IMPORTED_CONFIGURATIONS NOCONFIG)
set_target_properties(raylib PROPERTIES
  IMPORTED_LOCATION_NOCONFIG "${_IMPORT_PREFIX}/lib/libraylib.so.5.5.0"
  IMPORTED_SONAME_NOCONFIG "libraylib.so.550"
  )

list(APPEND _cmake_import_check_targets raylib )
list(APPEND _cmake_import_check_files_for_raylib "${_IMPORT_PREFIX}/lib/libraylib.so.5.5.0" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
