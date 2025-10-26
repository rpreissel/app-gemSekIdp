package de.gematik.test.tiger.examples.bdd.drivers;


import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SelectFile;

@Suite
@IncludeEngines("cucumber")
@SelectFile("/home/runner/work/app-gemSekIdp/app-gemSekIdp/gsi-testsuite/src/test/resources/features/idToken.feature")
@ConfigurationParameter(key = Constants.GLUE_PROPERTY_NAME, value = "de.gematik.test.tiger.glue, de.gematik.idp.gsi.test.steps")

@ConfigurationParameter(key = Constants.PLUGIN_PROPERTY_NAME,
                        value = "io.cucumber.core.plugin.TigerSerenityReporterPlugin,json:target/cucumber-parallel/2.json")
public class Driver002IT {
}
