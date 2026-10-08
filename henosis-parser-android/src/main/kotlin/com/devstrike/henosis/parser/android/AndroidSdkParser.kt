package com.devstrike.henosis.parser.android

import com.devstrike.henosis.core.api.ApiClass
import com.devstrike.henosis.core.api.ApiModel
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path

/**
 * Reads an Android SDK archive (`.aar`, or a `.jar` of compiled classes) and produces its [ApiModel].
 *
 * Kotlin classes are read through their metadata ([KotlinClassParser]); everything else goes
 * through [JavaClassParser]. Only public, non-synthetic classes are kept, and the result is sorted
 * by class name so the same archive always yields the same model.
 */
object AndroidSdkParser {
    fun parse(archive: Path): ApiModel {
        val classes =
            ArchiveReader
                .readClassFiles(archive)
                .mapNotNull { bytes -> parseClass(bytes) }
                .sortedBy { it.name }
        return ApiModel(classes)
    }

    /** Parses one class file; returns `null` when the class is not part of the public API. */
    fun parseClass(bytes: ByteArray): ApiClass? {
        val node = ClassNode()
        ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES)
        if (node.access and Opcodes.ACC_PUBLIC == 0 || node.access and Opcodes.ACC_SYNTHETIC != 0) return null
        val metadata = KotlinClassParser.readMetadata(node)
        return if (metadata != null) KotlinClassParser.parse(node, metadata) else JavaClassParser.parse(node)
    }
}
