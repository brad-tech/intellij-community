// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.klib

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinDecompiledFileViewProvider
import org.jetbrains.kotlin.analysis.decompiler.psi.text.DecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.buildDecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.createIncompatibleAbiVersionDecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.defaultDecompilerRendererOptions
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.library.metadata.KlibMetadataProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.NameResolverImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.serialization.SerializerExtensionProtocol
import org.jetbrains.kotlin.serialization.deserialization.ClassDeserializer
import org.jetbrains.kotlin.serialization.deserialization.FlexibleTypeDeserializer
import org.jetbrains.kotlin.serialization.deserialization.getClassId
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.IOException

abstract class KlibMetadataDecompiler<out V : BinaryVersion>(
    private val fileType: FileType,
    private val serializerProtocol: () -> SerializerExtensionProtocol,
    private val flexibleTypeDeserializer: FlexibleTypeDeserializer,
    private val expectedBinaryVersion: () -> V,
    private val invalidBinaryVersion: () -> V,
    stubVersion: Int
) : ClassFileDecompilers.Full() {

    private val metadataStubBuilder: KlibMetadataStubBuilder =
        KlibMetadataStubBuilder(
            stubVersion,
            fileType,
            serializerProtocol,
            ::readFileSafely
        )

    private val renderer: DescriptorRenderer by lazy {
        DescriptorRenderer.withOptions { defaultDecompilerRendererOptions() }
    }

    protected abstract fun doReadFile(file: VirtualFile): FileWithMetadata?

    override fun accepts(file: VirtualFile) = FileTypeRegistry.getInstance().isFileOfType(file, fileType)

    override fun getStubBuilder() = metadataStubBuilder

    override fun createFileViewProvider(file: VirtualFile, manager: PsiManager, physical: Boolean) =
        KotlinDecompiledFileViewProvider(manager, file, physical) { provider ->
            KlibDecompiledFile(
                provider,
                ::buildDecompiledText
            )
        }

    private fun readFileSafely(file: VirtualFile): FileWithMetadata? {
        if (!file.isValid) return null

        return try {
            doReadFile(file)
        } catch (e: IOException) {
            // This is needed because sometimes we're given VirtualFile instances that point to non-existent .jar entries.
            // Such files are valid (isValid() returns true), but an attempt to read their contents results in a FileNotFoundException.
            // Note that although calling "refresh()" instead of catching an exception would seem more correct here,
            // it's not always allowed and also is likely to degrade performance
            null
        }
    }

    private fun buildDecompiledText(virtualFile: VirtualFile): DecompiledText {
        assert(FileTypeRegistry.getInstance().isFileOfType(virtualFile, fileType)) { "Unexpected file type ${virtualFile.fileType}" }

        return when (val file = readFileSafely(virtualFile)) {
            is FileWithMetadata.Incompatible -> createIncompatibleAbiVersionDecompiledText(expectedBinaryVersion(), file.version)
            is FileWithMetadata.Compatible -> decompiledText(
                file,
                serializerProtocol(),
                flexibleTypeDeserializer,
                renderer
            )
            null -> createIncompatibleAbiVersionDecompiledText(expectedBinaryVersion(), invalidBinaryVersion())
        }
    }
}

sealed class FileWithMetadata {
    class Incompatible(val version: BinaryVersion) : FileWithMetadata()

    open class Compatible(val proto: ProtoBuf.PackageFragment) : FileWithMetadata() {
        val nameResolver = NameResolverImpl(proto.strings, proto.qualifiedNames)
        val packageFqName = FqName(proto.getExtension(KlibMetadataProtoBuf.fqName))

        open val classesToDecompile: List<ProtoBuf.Class> =
            proto.class_List.filter { proto ->
                val classId = nameResolver.getClassId(proto.fqName)
                !classId.isNestedClass && classId !in ClassDeserializer.BLACK_LIST
            }
    }
}

//todo: this function is extracted for KotlinNativeMetadataStubBuilder, that's the difference from Big Kotlin.
fun decompiledText(
    file: FileWithMetadata.Compatible,
    serializerProtocol: SerializerExtensionProtocol,
    flexibleTypeDeserializer: FlexibleTypeDeserializer,
    renderer: DescriptorRenderer
): DecompiledText {
    val packageFqName = file.packageFqName
    val resolver = KlibMetadataDeserializerForDecompiler(
        packageFqName, file.proto, file.nameResolver,
        serializerProtocol, flexibleTypeDeserializer
    )
    val declarations = arrayListOf<DeclarationDescriptor>()
    declarations.addAll(resolver.resolveDeclarationsInFacade(packageFqName))
    for (classProto in file.classesToDecompile) {
        val classId = file.nameResolver.getClassId(classProto.fqName)
        declarations.addIfNotNull(resolver.resolveTopLevelClass(classId))
    }
    return buildDecompiledText(packageFqName, declarations, renderer)
}
