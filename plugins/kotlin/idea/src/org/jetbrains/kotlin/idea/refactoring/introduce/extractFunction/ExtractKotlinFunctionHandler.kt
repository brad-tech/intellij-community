// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.extractMethod.newImpl.inplace.*
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.unifier.toRange
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.core.util.range
import org.jetbrains.kotlin.idea.refactoring.KotlinNamesValidator
import org.jetbrains.kotlin.idea.refactoring.getExtractionContainers
import org.jetbrains.kotlin.idea.refactoring.introduce.extractFunction.ui.KotlinExtractFunctionDialog
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.*
import org.jetbrains.kotlin.idea.refactoring.introduce.selectElementsWithTargetSibling
import org.jetbrains.kotlin.idea.refactoring.introduce.validateExpressionElements
import org.jetbrains.kotlin.idea.util.nonBlocking
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ExtractKotlinFunctionHandler(
    private val allContainersEnabled: Boolean = false,
    private val helper: ExtractionEngineHelper = getDefaultHelper(allContainersEnabled)
) : RefactoringActionHandler {

    companion object {
        private val isInplaceRefactoringEnabled: Boolean
            get() {
                return EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled
                       && Registry.`is`("kotlin.enable.inplace.extract.method")
            }

        fun getDefaultHelper(allContainersEnabled: Boolean): ExtractionEngineHelper {
            return if (isInplaceRefactoringEnabled) InplaceExtractionHelper(allContainersEnabled) else InteractiveExtractionHelper
        }
    }

    object InteractiveExtractionHelper : ExtractionEngineHelper(EXTRACT_FUNCTION) {
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            fun afterFinish(extraction: ExtractionResult){
                processDuplicates(extraction.duplicateReplacers, project, editor)
                onFinish(extraction)
            }
            KotlinExtractFunctionDialog(descriptorWithConflicts.descriptor.extractionData.project, descriptorWithConflicts) {
                doRefactor(it.currentConfiguration, ::afterFinish)
            }.show()
        }
    }

    class InplaceExtractionHelper(private val allContainersEnabled: Boolean) : ExtractionEngineHelper(EXTRACT_FUNCTION) {
        override fun configureAndRun(
            project: Project,
            editor: Editor,
            descriptorWithConflicts: ExtractableCodeDescriptorWithConflicts,
            onFinish: (ExtractionResult) -> Unit
        ) {
            val activeTemplateState = TemplateManagerImpl.getTemplateState(editor)
            if (activeTemplateState != null) {
                activeTemplateState.gotoEnd(true)
                ExtractKotlinFunctionHandler(allContainersEnabled, InteractiveExtractionHelper)
                    .invoke(project, editor, descriptorWithConflicts.descriptor.extractionData.originalFile, null)
            }

            val descriptor = descriptorWithConflicts.descriptor.copy(suggestedNames = listOf("extracted"))
            val elements = descriptor.extractionData.originalElements
            val file = descriptor.extractionData.originalFile
            val callRange = editor.document.createRangeMarker(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
                .apply { isGreedyToLeft = true; isGreedyToRight = true }
            val editorState = EditorState(editor)
            fun afterFinish(extraction: ExtractionResult){
                val callIdentifier = findSingleCallExpression(file, callRange.range)?.calleeExpression ?: throw IllegalStateException()
                val methodIdentifier = extraction.declaration.nameIdentifier ?: throw IllegalStateException()
                val methodRange = extraction.declaration.textRange
                val methodOffset = extraction.declaration.navigationElement.textRange.endOffset
                val callOffset = callIdentifier.textRange.endOffset
                val preview = InplaceExtractUtils.createPreview(editor, methodRange, methodOffset, callRange.range!!, callOffset)
                ExtractMethodTemplateBuilder(editor, EXTRACT_FUNCTION)
                    .withCompletionNames(descriptor.suggestedNames)
                    .onBroken {
                        editorState.revert()
                    }
                    .onSuccess {
                        processDuplicates(extraction.duplicateReplacers, file.project, editor)
                    }
                    .withValidation { variableRange ->
                        val error = getIdentifierError(file, variableRange)
                        if (error != null) {
                            CommonRefactoringUtil.showErrorHint(project, editor, error, EXTRACT_FUNCTION, null)
                        }
                        error == null
                    }
                    .disposeWithTemplate(preview)
                    .createTemplate(file, methodIdentifier.range, callIdentifier.range)
                onFinish(extraction)
            }
            val configuration = ExtractionGeneratorConfiguration(descriptor, ExtractionGeneratorOptions.DEFAULT)
            doRefactor(configuration, ::afterFinish)
        }

        @Nls
        private fun getIdentifierError(file: PsiFile, variableRange: TextRange): String? {
            val call = PsiTreeUtil.findElementOfClassAtOffset(file, variableRange.startOffset, KtCallExpression::class.java, false)
            val name = file.viewProvider.document.getText(variableRange)
            return if (! KotlinNamesValidator().isIdentifier(name, file.project)) {
                JavaRefactoringBundle.message("extract.method.error.invalid.name")
            } else if (call?.getResolvedCall(call.analyze())?.resultingDescriptor == null) {
                JavaRefactoringBundle.message("extract.method.error.method.conflict")
            } else {
                null
            }
        }

        private fun findSingleCallExpression(file: KtFile, range: TextRange?): KtCallExpression? {
            if (range == null) return null
            val container = PsiTreeUtil.findCommonParent(file.findElementAt(range.startOffset), file.findElementAt(range.endOffset))
            val callExpressions = PsiTreeUtil.findChildrenOfType(container, KtCallExpression::class.java)
            return callExpressions.singleOrNull { it.textRange in range }
        }
    }

    fun doInvoke(
        editor: Editor,
        file: KtFile,
        elements: List<PsiElement>,
        targetSibling: PsiElement
    ) {
        nonBlocking(file.project, {
            val adjustedElements = elements.singleOrNull().safeAs<KtBlockExpression>()?.statements ?: elements
            ExtractionData(file, adjustedElements.toRange(false), targetSibling)
        }) { extractionData ->
            ExtractionEngine(helper).run(editor, extractionData) { }
        }
    }

    fun selectElements(editor: Editor, file: KtFile, continuation: (elements: List<PsiElement>, targetSibling: PsiElement) -> Unit) {
        selectElementsWithTargetSibling(
            EXTRACT_FUNCTION,
            editor,
            file,
            KotlinBundle.message("title.select.target.code.block"),
            listOf(CodeInsightUtils.ElementKind.EXPRESSION),
            ::validateExpressionElements,
            { elements, parent -> parent.getExtractionContainers(elements.size == 1, allContainersEnabled) },
            continuation
        )
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is KtFile) return
        selectElements(editor, file) { elements, targetSibling -> doInvoke(editor, file, elements, targetSibling) }
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
        throw AssertionError("Extract Function can only be invoked from editor")
    }
}

val EXTRACT_FUNCTION: String
    @Nls
    get() = KotlinBundle.message("extract.function")
