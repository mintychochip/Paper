package dev.mintychochip.customentity;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = true)
@SuiteDisplayName("Custom entity behavior integration tests")
@IncludeTags("Normal")
@SelectClasses(CustomEntityBehaviorIntegrationTest.class)
@ConfigurationParameter(key = "TestSuite", value = "Normal")
public class CustomEntityBehaviorIntegrationTestSuite {
}
