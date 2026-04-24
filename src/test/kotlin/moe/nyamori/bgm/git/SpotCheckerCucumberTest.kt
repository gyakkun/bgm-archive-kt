package moe.nyamori.bgm.git

import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectPackages
import org.junit.platform.suite.api.Suite
import io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME

@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "moe.nyamori.bgm.git")
class SpotCheckerCucumberTest