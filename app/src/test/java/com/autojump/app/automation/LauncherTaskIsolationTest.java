package com.autojump.app.automation;

import org.junit.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import static org.junit.Assert.*;

/** Structural guard; actual launcher warm-start behavior still needs device acceptance. */
public class LauncherTaskIsolationTest {
    @Test public void launcherCannotResumeManagementTask() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) manifest = new File("app/src/main/AndroidManifest.xml");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var activities = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("activity");
        String ns = "http://schemas.android.com/apk/res/android";
        boolean found = false;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (".MainActivity".equals(activity.getAttributeNS(ns, "name"))) {
                found = true;
                assertEquals("${applicationId}.automation", activity.getAttributeNS(ns, "taskAffinity"));
                assertEquals("true", activity.getAttributeNS(ns, "noHistory"));
                assertEquals("true", activity.getAttributeNS(ns, "excludeFromRecents"));
            }
        }
        assertTrue(found);
    }
}
