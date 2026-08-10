package dev.mintychochip.particle;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = true)
@SuiteDisplayName("Custom particle transport tests")
@IncludeTags("Normal")
@SelectClasses(CustomParticleTransportTest.class)
@ConfigurationParameter(key = "TestSuite", value = "Normal")
public class CustomParticleTransportTestSuite {
}
