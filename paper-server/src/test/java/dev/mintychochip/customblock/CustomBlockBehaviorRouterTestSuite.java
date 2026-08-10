package dev.mintychochip.customblock;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = true)
@SuiteDisplayName("Custom block behavior router tests")
@IncludeTags("Normal")
@SelectClasses(CustomBlockBehaviorRouterTest.class)
@ConfigurationParameter(key = "TestSuite", value = "Normal")
public class CustomBlockBehaviorRouterTestSuite {
}
