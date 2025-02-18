// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.test.junit.JUnit5MalformedParameterizedInspection
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import java.io.File

private const val inspectionPath = "/codeInspection/junit5MalformedParameterized"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinJUnit5MalformedParameterizedTest : JavaCodeInsightFixtureTestCase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
  }

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)

    myFixture.addFileToProject("kotlin/jvm/JvmStatic.kt",
                               "package kotlin.jvm public annotation class JvmStatic")

    myFixture.addClass("""
    package java.util.stream;
    public interface Stream {}
    """.trimIndent())
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture)
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    finally {
      super.tearDown()
    }
  }

  companion object {
    private val inspection = JUnit5MalformedParameterizedInspection()
  }

  fun `test CantResolveTarget`() {
    myFixture.testHighlighting("CantResolveTarget.kt")
  }

  fun `test CantResolveTarget highlighting`() {
    myFixture.testHighlighting("CantResolveTarget.kt")
  }

  fun `test StaticMethodSourceTest quickFix from object`() {
    val quickfixes = myFixture.getAllQuickFixes("StaticMethodSourceObject.kt")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("StaticMethodSourceObject.after.kt")
  }

  fun `test StaticMethodSourceTest quickFix from class`() {
    val quickfixes = myFixture.getAllQuickFixes("StaticMethodSourceClass.kt")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("StaticMethodSourceClass.after.kt")
  }

  fun `test SuspiciousCombination quickFixes`() {
    myFixture.testHighlighting("SuspiciousCombination.kt")
  }

  fun `test NoSourcesProvided quickFixes`() {
    myFixture.testHighlighting("NoSourcesProvided.kt")
  }

  fun `test ExactlyOneType quickFixes`() {
    myFixture.testHighlighting("ExactlyOneType.kt")
  }

  fun `test NoParams quickFixes`() {
    myFixture.testHighlighting("NoParams.kt")
  }

  fun `test ReturnType quickFixes`() {
    myFixture.testHighlighting("ReturnType.kt")
  }

  fun `test EnumResolve quickFixes`() {
    myFixture.testHighlighting("EnumResolve.kt")
  }
}