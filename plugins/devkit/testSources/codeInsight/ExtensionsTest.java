/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.codeInsight;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;

/**
 * @author Dmitry Avdeev
 * @since 10/11/11
 */
public class ExtensionsTest extends JavaCodeInsightFixtureTestCase {
  public void testInspectionMappings() throws Throwable {
    myFixture.testHighlighting("inspectionMapping.xml", "bundle.properties");
  }

  public void testInspectionMappingsWithDefaultBundle() throws Throwable {
    myFixture.testHighlighting("inspectionMappingWithDefaultBundle.xml", "bundle.properties");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    String pathForClass = PathUtil.getJarPathForClass(LocalInspectionEP.class);
    moduleBuilder.addLibrary("lang-api", pathForClass);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(PluginXmlFunctionalTest.getInspectionClasses());
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/codeInsight";
  }
}
