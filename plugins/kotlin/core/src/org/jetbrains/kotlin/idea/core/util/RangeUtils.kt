// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.util

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

@Deprecated("Use 'textRange' instead", ReplaceWith("textRange"))
val PsiElement.range: TextRange
    get() = textRange!!

val RangeMarker.range: TextRange?
    get() = if (isValid) {
        val start = startOffset
        val end = endOffset
        if (start in 0..end) {
            TextRange(start, end)
        } else {
            // Probably a race condition had happened and range marker is invalidated
            null
        }
    } else null