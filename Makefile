export JAVA_OPTS = --enable-native-access=ALL-UNNAMED # to silence some huge warning

KONANC    = konanc
CINTEROP  = cinterop

SOURCES   = $(wildcard src/nativeMain/kotlin/*.kt)
KLIB      = build/raylib.klib
BINARY    = build/game.kexe

ifdef WAYLAND_DISPLAY
RAYLIB = raylib-wayland-5.6
DEF    = src/nativeInterop/cinterop/wayland-libraylib.def
$(info Display backend: Wayland ($(WAYLAND_DISPLAY)))
else ifdef DISPLAY
RAYLIB = raylib-x11-5.6
DEF    = src/nativeInterop/cinterop/x11-libraylib.def
$(info Display backend: X11 ($(DISPLAY)))
else
$(error No display server detected: neither WAYLAND_DISPLAY nor DISPLAY is set)
endif

all: $(BINARY)

$(KLIB): $(DEF) $(wildcard $(RAYLIB)/include/*.h)
	@mkdir -p build
	@echo "===> building cinterop klib for $(RAYLIB)"
	$(CINTEROP) -def $< -o $(KLIB)

$(BINARY): $(KLIB) $(SOURCES)
	@echo "===> compiling $(words $(SOURCES)) source files"
	$(KONANC) $(SOURCES) -library $(KLIB) -o $(BINARY) \
	  -linker-options "-Wl,-rpath,$(PWD)/$(RAYLIB)/lib"
	@echo "===> binary ready: $(BINARY)"

clean:
	rm -rf build

.PHONY: all clean
