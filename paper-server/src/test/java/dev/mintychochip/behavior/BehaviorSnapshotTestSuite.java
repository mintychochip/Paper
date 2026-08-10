package dev.mintychochip.behavior;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = true)
@SuiteDisplayName("Behavior snapshot tests")
@IncludeTags("Normal")
@SelectClasses(BehaviorSnapshotIntegrationTest.class)
@ConfigurationParameter(key = "TestSuite", value = "Normal")
public class BehaviorSnapshotTestSuite {
}
