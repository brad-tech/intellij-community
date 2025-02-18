// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.fasterxml.jackson.jr.ob.JSON
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.BuiltinModulesFileData
import org.jetbrains.intellij.build.impl.SkipTransientPropertiesJrExtension

/**
 * Generates product-info.json file containing meta-information about product installation.
 */
@CompileStatic
final class ProductInfoGenerator {
  public static final String FILE_NAME = "product-info.json"

  private final BuildContext context

  ProductInfoGenerator(BuildContext context) {
    this.context = context
  }

  byte[] generateMultiPlatformProductJson(
    @NotNull String relativePathToBin,
    @Nullable BuiltinModulesFileData builtinModules,
    @NotNull List<ProductInfoLaunchData> launch
  ) {
    ApplicationInfoProperties appInfo = context.applicationInfo
    ProductInfoData json = new ProductInfoData(
      name: appInfo.productName,
      version: appInfo.fullVersion,
      versionSuffix: appInfo.versionSuffix,
      buildNumber: context.buildNumber,
      productCode: appInfo.productCode,
      dataDirectoryName: context.systemSelector,
      svgIconPath: appInfo.svgRelativePath == null ? null : "$relativePathToBin/${context.productProperties.baseFileName}.svg",
      launch: launch,
      customProperties: context.productProperties.generateCustomPropertiesForProductInfo(),
      bundledPlugins: builtinModules?.bundledPlugins,
      fileExtensions: builtinModules?.fileExtensions,
      modules: builtinModules?.modules,
    )
    return JSON.builder().enable(JSON.Feature.PRETTY_PRINT_OUTPUT).register(new SkipTransientPropertiesJrExtension()).build().asBytes(json)
  }
}
