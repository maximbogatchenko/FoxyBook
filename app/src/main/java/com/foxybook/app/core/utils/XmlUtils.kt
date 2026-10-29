package com.foxybook.app.core.utils

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object XmlUtils {
    fun createParser(xml: String): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // Каталоги иногда отдают фид с пробелами/переводом строки перед
        // <?xml ...?> — KXmlParser на такой ввод падает с
        // "PI must not start with xml", поэтому режем края.
        parser.setInput(StringReader(xml.trim()))
        return parser
    }

    fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) {
            throw IllegalStateException()
        }
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
}
