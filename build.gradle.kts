import com.zerobias.buildtools.content.SchemaPrimitives

plugins {
    id("zb.workspace")
}

group = "com.zerobias.content"

// Directories the platform workflow loader reads, in its processing order.
val loaderDirs = listOf("skill", "status", "field", "role", "workflow", "activity", "triggers")

fun yamlFilesUnder(dir: java.io.File): List<java.io.File> =
    if (!dir.isDirectory) emptyList()
    else dir.walkTopDown()
        .onEnter { it.name != "node_modules" }
        .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        .toList()

// ════════════════════════════════════════════════════════════
// Workflow content validator — owned by this repo.
//
// Philosophy (per Chris/Kevin): the dataloader is the source of truth
// for content rules (code charset, references, field types, transition
// flags, RACI roles, etc.). Re-validating those here just creates drift
// risk — when the dataloader tightens a rule, the gate gets stale.
//
// This validator only enforces things the dataloader CANNOT or DOES NOT
// check:
//
//   1. Filesystem ↔ npm ↔ zerobias-block triangulation. Dataloader
//      reads zerobias.package but never the npm `name` field, and has
//      no view of the on-disk directory layout.
//        package/<a>/<b>/      → npm @zerobias-org/workflow-<a>-<b>
//                                pkg <a>.<b>.workflow
//        package/<a>/<b>/<c>/  → npm @zerobias-org/workflow-<a>-<b>-<c>
//                                pkg <a>.<b>.<c>.workflow
//
//   2. At least one loader directory has content. An empty package
//      publishes cleanly but contributes nothing.
//
//   3. `files` in package.json covers every loader directory present.
//      The gate loads the source directory; the published tarball only
//      carries what `files` lists, so a missing entry passes the gate
//      and silently drops that content in every environment.
//
//   4. Every activity trigger states an `id`. The loader accepts a
//      trigger without one and gives it a fresh id on every load, so
//      each reload adds another copy of the trigger.
//
//   5. Repo-wide unique `zerobias.package` values and file `id` UUIDs
//      (separate :validateUniquePackages task below). Dataloader sees
//      one package at a time; collisions only surface in prod.
//
// Everything else is delegated to the dataloader running in
// testDataloader during gate.
// ════════════════════════════════════════════════════════════
extra["contentValidator"] = { proj: org.gradle.api.Project ->
    val projectDir = proj.projectDir
    val tag = "[workflow-validator] ${proj.path}"

    require(projectDir.resolve("package.json").isFile) { "$tag package.json missing in ${projectDir.path}" }
    require(projectDir.resolve(".npmrc").isFile)       { "$tag .npmrc missing in ${projectDir.path}" }

    // ── 1. Filesystem ↔ npm ↔ zerobias-block triangulation ──
    val packageRoot = proj.rootProject.projectDir.resolve("package")
    val parts = projectDir.relativeTo(packageRoot).path.split(java.io.File.separator)
    require(parts.size in 2..3) {
        "$tag unexpected directory depth — parts=$parts. Use package/<vendor>/<product>/ or package/<vendor>/<suite>/<product>/."
    }
    val pkgDoc = SchemaPrimitives.parseJson(projectDir.resolve("package.json"))
    SchemaPrimitives.requirePackageIdentity(
        pkgDoc,
        expectedNpmName = "@zerobias-org/workflow-${parts.joinToString("-")}",
        expectedZerobiasPackage = "${parts.joinToString(".")}.workflow",
        field = "$tag package.json",
    )

    // ── 2. At least one loader directory has content ──
    val present = loaderDirs.filter { yamlFilesUnder(projectDir.resolve(it)).isNotEmpty() }
    require(present.isNotEmpty()) {
        "$tag must contain at least one of $loaderDirs with at least one .yml file"
    }

    // ── 3. `files` covers every loader directory present ──
    @Suppress("UNCHECKED_CAST")
    val files = pkgDoc["files"] as? List<String> ?: emptyList()
    val uncovered = present.filter { dir -> files.none { it == dir || it.startsWith("$dir/") } }
    require(uncovered.isEmpty()) {
        "$tag package.json files=$files does not cover $uncovered; add \"<dir>/**\" for each, or it is left out of the published package."
    }

    // ── 4. Every activity trigger states an `id` ──
    yamlFilesUnder(projectDir.resolve("triggers")).forEach { f ->
        val doc = SchemaPrimitives.parseYaml(f)
        val triggers = doc["triggers"] as? List<*> ?: emptyList<Any?>()
        triggers.forEachIndexed { i, t ->
            SchemaPrimitives.requireUuid((t as? Map<*, *>)?.get("id"), "$tag ${f.relativeTo(projectDir)} triggers[$i].id")
        }
    }

    proj.logger.lifecycle("$tag: parts=${parts.joinToString("/")} dirs=$present")
}

// ════════════════════════════════════════════════════════════
// :validateUniquePackages — repo-wide cross-cut.
// Fails when two packages share a zerobias.package, or when a file `id`
// (or trigger id) appears more than once anywhere under package/.
// ════════════════════════════════════════════════════════════
val validateUniquePackages by tasks.registering {
    group = "verification"
    description = "Fail if two packages share a zerobias.package or any id UUID repeats"

    val packageDir = layout.projectDirectory.dir("package").asFile
    inputs.files(
        fileTree(packageDir) {
            include("**/package.json", "**/*.yml", "**/*.yaml")
            exclude("**/node_modules/**")
        }
    )

    doLast {
        val byPkg = mutableMapOf<String, MutableList<String>>()
        val byId = mutableMapOf<String, MutableList<String>>()
        packageDir.walkTopDown()
            .onEnter { it.name != "node_modules" }
            .filter { it.isFile && it.name == "package.json" && it.parentFile.resolve("build.gradle.kts").isFile }
            .forEach { pj ->
                val pkgName = SchemaPrimitives.getPath(SchemaPrimitives.parseJson(pj), "zerobias.package") as? String
                if (pkgName != null) {
                    byPkg.getOrPut(pkgName) { mutableListOf() }.add(pj.relativeTo(rootDir).path)
                }
                loaderDirs.flatMap { yamlFilesUnder(pj.parentFile.resolve(it)) }.forEach { f ->
                    val doc = SchemaPrimitives.parseYaml(f)
                    val where = f.relativeTo(rootDir).path
                    (doc["id"] as? String)?.let { byId.getOrPut(it.lowercase()) { mutableListOf() }.add(where) }
                    (doc["triggers"] as? List<*>)?.forEach { t ->
                        ((t as? Map<*, *>)?.get("id") as? String)?.let {
                            byId.getOrPut(it.lowercase()) { mutableListOf() }.add("$where (trigger)")
                        }
                    }
                }
            }

        val report = listOf("zerobias.package" to byPkg, "id" to byId).flatMap { (kind, map) ->
            map.filterValues { it.size > 1 }.map { (key, paths) -> "  $kind $key\n    " + paths.joinToString("\n    ") }
        }
        if (report.isNotEmpty()) {
            throw GradleException("[validateUniquePackages] duplicates across the repo:\n" + report.joinToString("\n"))
        }
        logger.lifecycle("[validateUniquePackages] ${byPkg.size} packages, ${byId.size} unique ids")
    }
}

subprojects {
    tasks.matching { it.name == "validateContent" }.configureEach {
        dependsOn(rootProject.tasks.named("validateUniquePackages"))
    }
}

val projectPaths by tasks.registering {
    group = "info"
    description = "Output project-to-directory mappings for tooling (used by zbb CLI)"
    doLast {
        subprojects.filter { it.buildFile.exists() }.forEach { p ->
            println("${p.path}=${p.projectDir.relativeTo(rootDir)}")
        }
    }
}

val changedModules by tasks.registering {
    group = "info"
    description = "List workflow packages changed since last version tag"
    doLast {
        val lastTag = try {
            providers.exec { commandLine("git", "describe", "--tags", "--abbrev=0") }
                .standardOutput.asText.get().trim()
        } catch (e: Exception) {
            logger.warn("No version tags found -- listing all packages as changed")
            null
        }

        val diffArgs = if (lastTag != null) listOf("git", "diff", "--name-only", lastTag, "HEAD")
                       else listOf("git", "ls-files")

        val result = providers.exec { commandLine(diffArgs) }.standardOutput.asText.get()

        // Mixed-depth: walk up each changed file to its nearest
        // build.gradle.kts to know which package it belongs to.
        val packageDir = rootDir.resolve("package")
        val changed = mutableSetOf<String>()
        result.lines()
            .filter { it.startsWith("package/") }
            .forEach { line ->
                var dir = rootDir.resolve(line).parentFile
                while (dir != null && dir != packageDir && dir.startsWith(packageDir)) {
                    if (dir.resolve("build.gradle.kts").isFile) {
                        changed.add(dir.relativeTo(packageDir).path)
                        break
                    }
                    dir = dir.parentFile
                }
            }
        changed.forEach { println(it) }
    }
}
