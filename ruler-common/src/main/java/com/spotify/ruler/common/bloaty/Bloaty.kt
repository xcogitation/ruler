package com.spotify.ruler.common.bloaty

import com.spotify.ruler.common.apk.ApkEntry
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private const val COLUMN_SIZE = 3
/**
 * Utility class for working with Bloaty, a binary size analysis tool.
 *
 * Bloaty is a command-line tool and a helpful utility for analyzing binary sizes and understanding
 * the space usage of compiled executables, object files, and other binary formats. This `BloatyUtil`
 * class provides convenient methods to work with Bloaty and extract information about native
 * libraries found in binaries.
 *
 * Bloaty Source Code Repository: [https://github.com/google/bloaty](https://github.com/google/bloaty)
 */
object Bloaty {

    private val bloatyPath: String? by lazy { findBloatyPath() }

    private fun findBloatyPath(): String? {
        val path = executeCommandAndGetOutput("which bloaty").singleOrNull()
        return if (path.isNullOrEmpty()) {
            println("Could not find Bloaty. Install Bloaty for more information about native libraries.")
            null
        } else {
            println( "Bloaty detected at: $path")
            path
        }
    }

    /**
     * Analyzes the size of compiled units within a binary using Bloaty.
     *
     * This function utilizes Bloaty to perform a detailed analysis of a binary, focusing on its compiled units.
     * It takes the path to the binary (native library) to be analyzed and the associated debug file, which
     * contains debug symbols and additional metadata necessary for accurate size analysis.
     *
     * Flags used in the Bloaty command:
     *
     * -d compileunits
     *    Instructs Bloaty to focus its analysis on the compiled units within the binary. This flag
     *    will break down the binary into classes and show the size information
     *    for each compiled unit.
     *
     * -n 0
     *    Sets the minimum number of top output rows to display. In this case, it is set to 0, meaning
     *    Bloaty will display information for all compiled units without any truncation.
     *
     * --csv
     *    Format ther output in CSV (Comma-Separated Values) format.
     *
     * @param bytes The bytes of the native library.
     * @param debugFile The debug file containing the un-stripped file native library information.
     * @return A list of [ApkEntry.Default] representing the compiled units in the native library.
     */
    fun parseNativeLibraryEntry(bytes: ByteArray, debugFile: File?): List<ApkEntry.Default> {
        println("Parsing unstripped library at: $debugFile")
        if (bloatyPath == null || debugFile == null) {
            println("Unable to parse library")
            return emptyList()
        }


        val tmpFile = File.createTempFile("native-lib", ".so").apply {
            writeBytes(bytes)
        }.also { it.deleteOnExit() }



        val command =
            "$bloatyPath --debug-file=${debugFile.absolutePath} ${tmpFile.absolutePath} -d compileunits -n 0 --csv"

        println("Running bloaty command:")
        println(command)

        return parseBloatyOutputToApkEntry(command)
    }

    /**
     * Executes a Bloaty command and returns the parsed [ApkEntry.Default].
     *
     * @param command The Bloaty command to be executed.
     * @return A list of [ApkEntry.Default] representing the compiled units parsed from Bloaty's output.
     */
    private fun parseBloatyOutputToApkEntry(command: String): List<ApkEntry.Default> {
        val rows = mutableListOf<ApkEntry.Default>()

        val outputLines = executeCommandAndGetOutput(command)

        for (line in outputLines) {
            val cols = line.split(",")
            if (cols.size == COLUMN_SIZE) {
                val size = cols.last().toLongOrNull() ?: continue
                val entry = ApkEntry.Default(
                    cols.first().substringAfter("../.."),
                    size,
                    size
                )
                rows.add(entry)
            }
        }
        println("Parsed ${rows.count()} APK entries")
        return rows
    }

    /**
     * Executes the specified command on the command-line and captures its output as a list of strings.
     * This function is designed to run a command and retrieve the lines of output generated by the command.
     * It is important to note that this function will block until the command execution is complete.
     *
     * @param command The command to be executed on the command-line.
     * @return A list of strings representing the output lines generated by the executed command.
     *         Each element of the list corresponds to a line of text from the command's standard output.
     */
    private fun executeCommandAndGetOutput(command: String): List<String> {
        val process = Runtime.getRuntime().exec(command)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val outputLines = mutableListOf<String>()

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            outputLines.add(line ?: "")
        }

        process.waitFor()
        return outputLines
    }
}
