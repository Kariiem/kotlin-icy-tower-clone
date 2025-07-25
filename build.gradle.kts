plugins {
    kotlin("multiplatform") version "2.2.0"
}
repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val nativeTarget = when {
        hostOs == "Linux" && !isArm64 -> linuxX64()
        else -> throw GradleException("Only linuxX64 is supported for now")
    }

    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val libraylib by creating
            }
        }
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }

    sourceSets {
        val linuxX64Main by getting
    }
}
