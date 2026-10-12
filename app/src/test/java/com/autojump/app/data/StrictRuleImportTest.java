package com.autojump.app.data;
import org.junit.Test;
import static org.junit.Assert.*;
public class StrictRuleImportTest {
    @Test(expected=RuntimeException.class) public void unknownActionCannotDisappearSilently() {
        StrictRuleImport.decode("{\"schemaVersion\":3,\"rules\":[{\"id\":\"x\",\"name\":\"x\",\"actions\":[{\"type\":\"DELETE_FILES\"}]}]}");
    }
    @Test public void legacyStringLinkIsMigrated() {
        assertEquals("example://page", StrictRuleImport.decode("[{\"id\":\"x\",\"actions\":[\"example://page\"]}]").get(0).getActions().get(0).getDeeplink());
    }
    @Test(expected=RuntimeException.class) public void unsupportedBackupVersionIsRejected() {
        StrictRuleImport.decode("{\"schemaVersion\":99,\"rules\":[]}");
    }
    @Test(expected=RuntimeException.class) public void brokenTypedActionIsRejected() {
        StrictRuleImport.decode("{\"schemaVersion\":4,\"rules\":[{\"id\":\"x\",\"actions\":[{\"type\":\"CLICK\",\"timeoutMs\":10,\"targetPackage\":\"x\"}]}]}");
    }
}
