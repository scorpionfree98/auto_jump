package com.autojump.app.condition;

import com.autojump.app.domain.ConditionType;
import com.autojump.app.domain.RuleCondition;

/**
 * 地理位置条件
 * 判断当前位置是否在目标位置的指定半径内
 */
@Deprecated
public class LocationCondition extends RuleCondition {

    public LocationCondition(double latitude, double longitude, int radiusMeters, String placeName) {
        super(ConditionType.LOCATION);
        RuleCondition value = RuleCondition.location(latitude, longitude, radiusMeters, placeName);
        setLatitude(value.getLatitude());
        setLongitude(value.getLongitude());
        setRadiusMeters(value.getRadiusMeters());
        setPlaceName(value.getPlaceName());
    }
}
