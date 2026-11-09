pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 注意：本机全局 init.gradle 会向 project 注入国内镜像仓库，
    // 因此这里不能用 FAIL_ON_PROJECT_REPOS，否则构建会直接失败。
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LDDC"
include(":app")
