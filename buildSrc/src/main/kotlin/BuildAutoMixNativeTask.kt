/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */


import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(because = "Cargo manages its own compiler cache")
abstract class BuildAutoMixNativeTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val crateDirectory: DirectoryProperty

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    val ndkVersionFile: RegularFile
        get() = ndkDirectory.file("source.properties").get()

    @get:Input
    abstract val rustVersion: Property<String>

    @get:Input
    abstract val minApi: Property<Int>

    @get:LocalState
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDirectory: DirectoryProperty

    @get:Inject
    protected abstract val exec: ExecOperations

    @TaskAction
    fun build() {
        val bin = ndkDirectory.dir("toolchains/llvm/prebuilt").get().asFile
            .listFiles { file -> file.isDirectory && file.resolve("bin").isDirectory }
            ?.singleOrNull()?.resolve("bin")
            ?: throw GradleException("Expected an Android NDK with one LLVM host toolchain")
        val crate = crateDirectory.get().asFile
        val targetDir = cargoTargetDirectory.get().asFile
        val jniLibs = jniLibsDirectory.get().asFile
        val api = minApi.get()
        val abis = listOf(
            Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android"),
            Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi"),
            Triple("x86", "i686-linux-android", "i686-linux-android"),
            Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android"),
        )
        val library = "libarchivetune_automix.so"
        for ((abi, target, clangTarget) in abis) {
            val linkerName = "$clangTarget$api-clang"
            val linker = bin.resolve(linkerName).takeIf { it.isFile } ?: bin.resolve("$linkerName.cmd")
            if (!linker.isFile) throw GradleException("Android NDK linker missing: $linker")
            val linkerEnv = target.uppercase().replace('-', '_')
            exec.exec { spec ->
                spec.workingDir(crate)
                spec.environment("CARGO_TARGET_DIR", targetDir.absolutePath)
                spec.environment("CARGO_TARGET_${linkerEnv}_LINKER", linker.absolutePath)
                spec.commandLine("cargo", "build", "--release", "--locked", "--target", target)
            }
            targetDir.resolve("$target/release/$library")
                .copyTo(jniLibs.resolve("$abi/$library"), overwrite = true)
        }
    }
}
