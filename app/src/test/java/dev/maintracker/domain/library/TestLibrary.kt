package dev.maintracker.domain.library

import java.io.File

/** The shipped assets, read straight from src/main (unit tests run with the module dir as cwd). */
object TestLibrary {
    private fun asset(path: String) = File("src/main/assets/$path").readText()

    val library: LibraryFile by lazy { LibraryFile.decode(asset(LibraryFile.ASSET)) }
    val setup: SetupFile by lazy { SetupFile.decode(asset(SetupFile.ASSET)) }
    val templates by lazy { library.templates.map { it.toEntity() } }
}
