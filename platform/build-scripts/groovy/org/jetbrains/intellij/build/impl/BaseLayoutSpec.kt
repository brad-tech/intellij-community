// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

open class BaseLayoutSpec(private val layout: BaseLayout) {
  /**
   * Register an additional module to be included into the plugin distribution. Module-level libraries from
   * {@code moduleName} with scopes 'Compile' and 'Runtime' will be also copied to the 'lib' directory of the plugin.
   */
  fun withModule(moduleName: String) {
    layout.withModule(moduleName)
  }

  /**
   * Register an additional module to be included into the plugin distribution. If {@code relativeJarPath} doesn't contain '/' (i.e. the
   * JAR will be added to the plugin's classpath) this will also cause modules library from {@code moduleName} with scopes 'Compile' and
   * 'Runtime' to be copied to the 'lib' directory of the plugin.
   *
   * @param relativeJarPath target JAR path relative to 'lib' directory of the plugin; different modules may be packed into the same JAR,
   * but <strong>don't use this for new plugins</strong>; this parameter is temporary added to keep layout of old plugins.
   */
  fun withModule(moduleName: String, relativeJarPath: String) {
    layout.withModule(moduleName, relativeJarPath)
  }

  @SuppressWarnings("unused")
  @Deprecated(message = "localizable resources are now put to the module JAR, so {@code localizableResourcesJars} parameter is ignored now")
  fun withModule(moduleName: String, relativeJarPath: String, localizableResourcesJar: String) {
    withModule(moduleName, relativeJarPath)
  }

  /**
   * Include the project library to 'lib' directory or its subdirectory of the plugin distribution
   * @relativeOutputPath path relative to 'lib' plugin directory
   */
  @JvmOverloads
  fun withProjectLibrary(libraryName: String, outPath: String? = null) {
    layout.includedProjectLibraries.add(ProjectLibraryData(libraryName, outPath, ProjectLibraryData.PackMode.MERGED))
  }

  fun withProjectLibrary(libraryName: String, packMode: ProjectLibraryData.PackMode) {
    layout.includedProjectLibraries.add(ProjectLibraryData(libraryName, null, packMode))
  }

  fun withProjectLibrary(libraryName: String, outPath: String?, packMode: ProjectLibraryData.PackMode) {
    layout.includedProjectLibraries.add(ProjectLibraryData(libraryName, outPath, packMode))
  }

  /**
   * Include the module library to the plugin distribution. Please note that it makes sense to call this method only
   * for additional modules which aren't copied directly to the 'lib' directory of the plugin distribution, because for ordinary modules
   * their module libraries are included into the layout automatically.
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  fun withModuleLibrary(libraryName: String, moduleName: String, relativeOutputPath: String) {
    layout.includedModuleLibraries.add(ModuleLibraryData(
      moduleName = moduleName,
      libraryName = libraryName,
      relativeOutputPath = relativeOutputPath))
  }

  /**
   * Exclude the module library from plugin distribution.
   */
  fun withoutModuleLibrary(moduleName: String, libraryName: String) {
    layout.excludedModuleLibraries.putValue(moduleName, libraryName)
  }

  /**
   * Exclude the specified files when {@code moduleName} is packed into JAR file.
   * <strong>This is a temporary method added to keep layout of some old plugins. If some files from a module shouldn't be included into the
   * module JAR it's strongly recommended to move these files outside of the module source roots.</strong>
   * @param excludedPattern Ant-like pattern describing files to be excluded (relatively to the module output root); e.g. foo&#47;**
   * to exclude `foo` directory
  */
  fun excludeFromModule(moduleName: String, excludedPattern: String) {
    layout.excludeFromModule(moduleName, excludedPattern)
  }

  /**
   * Include an artifact output to the plugin distribution.
   * @param artifactName name of the project configuration
   * @param relativeOutputPath target path relative to 'lib' directory
   */
  fun withArtifact(artifactName: String, relativeOutputPath: String) {
    layout.includedArtifacts[artifactName] = relativeOutputPath
  }

  /**
   * Include contents of JARs of the project library {@code libraryName} into JAR {@code jarName}
   */
  fun withProjectLibraryUnpackedIntoJar(libraryName: String, jarName: String) {
    layout.withProjectLibraryUnpackedIntoJar(libraryName, jarName)
  }
}
