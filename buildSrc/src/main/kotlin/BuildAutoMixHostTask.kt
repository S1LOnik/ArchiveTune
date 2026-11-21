/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.util.PatternSet
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Cargo manages its own compiler cache")
abstract class BuildAutoMixHostTask : DefaultTask() {
    @get:Internal
    abstract val crateDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val crateFiles: FileTree
        get() = crateDirectory.get().asFileTree.matching(PatternSet().exclude("target/**"))

    @get:Input
    abstract val rustVersion: Property<String>

    @get:LocalState
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:OutputFile
    abstract val libraryFile: RegularFileProperty

    @get:Inject
    protected abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val targetDirectory = cargoTargetDirectory.get().asFile
        val library = libraryFile.get().asFile
        exec.exec { spec ->
            spec.workingDir(crateDirectory.get().asFile)
            spec.environment("CARGO_TARGET_DIR", targetDirectory.absolutePath)
            spec.commandLine("cargo", "build", "--release", "--locked")
        }
        targetDirectory.resolve("release/${library.name}").copyTo(library, overwrite = true)
    }
}
