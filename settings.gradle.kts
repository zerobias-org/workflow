// settings.gradle.kts — workflow monorepo
//
// Plugin resolution order: mavenLocal (for `publishToMavenLocal` dev builds
// of build-tools) → GitHub Packages Maven → gradle plugin portal → mavenCentral.
// Never via `includeBuild` of a sibling repo path: dev iteration goes through
// `./gradlew publishToMavenLocal` from build-tools so CI and local resolve
// the artifact the same way.

pluginManagement {
    repositories {
        mavenLocal()
        maven {
            url = uri("https://maven.pkg.github.com/zerobias-org/util")
            credentials {
                username = System.getenv("GITHUB_ACTOR")?.takeIf(String::isNotBlank) ?: "zerobias-org"
                // `?:` only falls through on null, not on an empty string. CI can export
                // READ_TOKEN set-but-empty, which would short-circuit the fallback and
                // auth with a blank password (401 → "plugin not found"). Skip blanks so a
                // later valid token (e.g. NPM_TOKEN) is used.
                password = listOf("READ_TOKEN", "NPM_TOKEN", "GITHUB_TOKEN")
                    .firstNotNullOfOrNull { System.getenv(it)?.takeIf(String::isNotBlank) } ?: ""
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("zb.workspace") version "1.+"
        id("zb.base") version "1.+"
        id("zb.content") version "1.+"
    }
}

rootProject.name = "workflows"

// Auto-discover all workflow packages under package/. Mixed depth:
//   package/<vendor>/<product>/          (depth 2)
//   package/<vendor>/<suite>/<product>/  (depth 3)
// The per-package build.gradle.kts is a one-line marker:
//   plugins { id("zb.content") }
// Project paths mirror filesystem: package/zerobias/zerobias/base → :zerobias:zerobias:base.
// node_modules is skipped: installed dependencies can ship their own
// build.gradle.kts marker and must not become projects of this build.
val packageDir = file("package")
if (packageDir.exists()) {
    packageDir.walkTopDown()
        .onEnter { it.name != "node_modules" }
        .filter { it.name == "build.gradle.kts" }
        .forEach { buildFile ->
            val moduleDir = buildFile.parentFile
            val relativePath = moduleDir.relativeTo(packageDir).path
            val projectPath = relativePath.replace(File.separatorChar, ':')

            include(projectPath)
            project(":$projectPath").projectDir = moduleDir
        }
}
