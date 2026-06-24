export JAVA_OPTS = --enable-native-access=ALL-UNNAMED -Xmx4G # to silence some huge warning

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


NDEBUG ?= 0
ifeq ($(NDEBUG),1)
  LLVM_FLAGS =
else
  LLVM_FLAGS = -g -Xllvm-module-passes="default<O0>"
endif

all: $(BINARY)

$(KLIB): $(DEF)
	@mkdir -p build
	@echo ""
	@echo "===> building cinterop klib for $(RAYLIB)"
	$(CINTEROP) -def $< -o $@

$(BINARY): $(KLIB) $(SOURCES)
	@echo ""
	@echo "===> compiling $(words $(SOURCES)) source files"
	$(KONANC) $(SOURCES) -library $(KLIB) -o $@ \
	  -linker-options "-Wl,-rpath,$(PWD)/$(RAYLIB)/lib" $(LLVM_FLAGS)
	@echo ""
	@echo "===> binary ready: $@"

clean:
	rm -rf build

.PHONY: all clean
