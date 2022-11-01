package moe.nyamori.bgm.util

import net.sf.saxon.lib.NamespaceConstant
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory

object XPathHelper {

    val G_XPATH_FACTORY = XPathFactory.newInstance(NamespaceConstant.OBJECT_MODEL_SAXON)
    val G_XPATH_INSTANCE: XPath = G_XPATH_FACTORY.newXPath()

    val XP_404_MSG: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[1]/div[2]/div[1]/div/div/p[1]")

    val XP_GROUP_NAME: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[2]/h1/span/a[1]")
    val XP_GROUP_TOPIC_TITLE: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[2]/h1/text()")
    val XP_GROUP_TOPIC_TOP_POST_DIV: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[3]/div[1]")
    val XP_GROUP_TOPIC_TOP_POST_SMALL_NUM_AND_DATE: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[3]/div[1]/div[1]/small/text()")
    val XP_GROUP_TOPIC_TOP_POST_A_FOR_USERNAME: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[3]/div[1]/a")
    val XP_GROUP_TOPIC_TOP_POST_A_FOR_USERID: XPathExpression = G_XPATH_INSTANCE.compile("/html/body/div[3]/div[1]/a/span")

}