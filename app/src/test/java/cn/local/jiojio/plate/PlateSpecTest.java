package cn.local.jiojio.plate;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlateSpecTest {
    @Test public void keepsDifferentOuterRatiosAndGlyphGeometry() {
        assertEquals(440,PlateSpec.width(0));assertEquals(480,PlateSpec.width(1));
        for(int type=0;type<2;type++)for(int i=0;i<PlateSpec.length(type);i++) {
            assertTrue(PlateSpec.characterX(i,type)>=0);
            assertTrue(PlateSpec.characterX(i,type)+PlateSpec.characterWidth(i,type)<PlateSpec.width(type));
        }
        assertEquals(43,PlateSpec.characterWidth(1,1),0);assertEquals(45,PlateSpec.characterWidth(0,1),0);
    }
    @Test public void acceptsBothSupportedTypesAndAllProvinces() {
        assertNull(PlateSpec.error("皖AD12345",1));assertNull(PlateSpec.error("京A12345",0));
        for(char province:PlateSpec.PROVINCES.toCharArray())assertNull(PlateSpec.error(province+"A12345",0));
    }
    @Test public void cleansSeparatorsWithoutUsingDefaultLocale() {
        assertEquals("京A12345",PlateSpec.normalize(" 京a·12 345 "));assertEquals("",PlateSpec.normalize(null));
    }
    @Test public void rejectsWrongLengthUnknownProvinceAndUnsupportedGlyphs() {
        for(String value:new String[]{"", "京A1234", "京A123456", "XAA1234", "京I12345", "京A12O45", "京A12🙂"})
            assertNotNull(PlateSpec.error(value,0));
        assertNotNull(PlateSpec.error("京A12345",1));
    }
}
