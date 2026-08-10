package io.papermc.paper.registry;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({CatalogRegistryTest.class, CatalogNativeBoundaryTest.class})
public class CatalogRegistryTestSuite {
}
