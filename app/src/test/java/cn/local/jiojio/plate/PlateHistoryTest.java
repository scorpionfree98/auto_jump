package cn.local.jiojio.plate;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlateHistoryTest {
    @Test public void normalizesAndDeduplicatesWithoutLosingPin() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);
        data.pin("0:京A12345",true);data.use(" 京a·12345 ",0,20);
        assertEquals(1,data.list().size());assertTrue(data.list().get(0).pinned);assertEquals(20,data.list().get(0).lastUsed);
    }
    @Test public void pinnedFirstThenMostRecentlyUsed() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);data.use("沪B54321",0,20);data.use("皖AD12345",1,30);
        data.pin("0:京A12345",true);
        assertEquals("京A12345",data.list().get(0).number);assertEquals("皖AD12345",data.list().get(1).number);
        data.pin("0:京A12345",false);assertEquals("皖AD12345",data.list().get(0).number);
    }
    @Test public void pinDoesNotChangeUseTime() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);data.pin("0:京A12345",true);
        assertEquals(10,data.list().get(0).lastUsed);
    }
    @Test public void selectingOldPlateMovesItUpAmongUnpinned() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);data.use("沪B54321",0,20);data.use("京A12345",0,30);
        assertEquals("京A12345",data.list().get(0).number);
    }
    @Test public void backwardsClockStillOrdersLatestUseFirst() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,100);data.use("沪B54321",0,1);
        assertEquals("沪B54321",data.list().get(0).number);assertEquals(101,data.list().get(0).lastUsed);
    }
    @Test public void deletionIsPersistentAndDoesNotRemoveOtherType() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);data.use("皖AD12345",1,20);data.pin("0:京A12345",true);
        data.remove("0:京A12345");PlateHistory restored=PlateHistory.decode(data.encode());
        assertEquals(1,restored.list().size());assertEquals("皖AD12345",restored.list().get(0).number);
        restored.remove("missing");assertEquals(1,restored.list().size());
    }
    @Test public void roundTripKeepsTypePinAndRecency() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);data.use("皖AD12345",1,20);data.pin("0:京A12345",true);
        assertEquals(data.encode(),PlateHistory.decode(data.encode()).encode());
        assertTrue(PlateHistory.decode(data.encode()).list().get(0).pinned);
    }
    @Test public void snapshotsCannotMutateLibrary() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);data.list().clear();assertEquals(1,data.list().size());
    }
    @Test public void invalidInputsDoNotMutateExistingHistory() {
        PlateHistory data=new PlateHistory();data.use("京A12345",0,10);String before=data.encode();
        for(int type:new int[]{0,1,2})try {data.use("invalid",type,20);fail();}catch(IllegalArgumentException expected){}
        try {data.use("京A12345",2,20);fail();}catch(IllegalArgumentException expected){}
        assertEquals(before,data.encode());
    }
    @Test public void refusesUnreadableAndFutureDataRatherThanSilentlyResetting() {
        for(String json:new String[]{"", "not-json", "[]", "{}", "{\"schemaVersion\":2,\"entries\":[]}",
                "{\"schemaVersion\":1,\"entries\":[{}]}","{\"schemaVersion\":1,\"entries\":null}"}) {
            try {PlateHistory.decode(json);fail("Invalid history accepted");}catch(IllegalArgumentException expected){}
        }
        assertTrue(PlateHistory.decode(null).list().isEmpty());
    }
    @Test public void unknownPinDoesNotCreateAnEntry() {
        PlateHistory data=new PlateHistory();try {data.pin("0:京A12345",true);fail();}catch(IllegalArgumentException expected){}
        assertTrue(data.list().isEmpty());
    }
    @Test public void rejectsFractionalVersionsAndMistypedFields() {
        for(String json:new String[]{"{\"schemaVersion\":1.5,\"entries\":[]}","{\"schemaVersion\":\"1\",\"entries\":[]}",
                "{\"schemaVersion\":1,\"entries\":[{\"number\":\"京A12345\",\"style\":0,\"pinned\":\"true\",\"lastUsed\":1}]}",
                "{\"schemaVersion\":1,\"entries\":[{\"number\":\"京A12345\",\"style\":0.5,\"pinned\":true,\"lastUsed\":1}]}"}) {
            try {PlateHistory.decode(json);fail("Mistyped history accepted");}catch(IllegalArgumentException expected){}
        }
    }
}
