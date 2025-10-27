package com.aiantfarm.utils;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.time.Instant;
import java.util.Optional;
import java.util.Map;

public final class DynamoHelper {
    public static AttributeValue dynamoString(String v){ return AttributeValue.builder().s(v).build(); }
    public static AttributeValue dynamoNum(long v){ return AttributeValue.builder().n(Long.toString(v)).build(); }
    public static AttributeValue dyanmoBool(boolean v){ return AttributeValue.builder().bool(v).build(); }
    public static Optional<String> getDynamoString(Map<String, AttributeValue> it, String k){ return Optional.ofNullable(it.get(k)).map(AttributeValue::s); }
    public static Optional<Long> getDynamoNum(Map<String, AttributeValue> it, String k){ return Optional.ofNullable(it.get(k)).map(AttributeValue::n).map(Long::parseLong); }
    public static Optional<Boolean> getDynamoBool(Map<String, AttributeValue> it, String k){ return Optional.ofNullable(it.get(k)).map(AttributeValue::bool); }
    public static long toEpoch(Instant i){ return i.getEpochSecond(); }
    public static Instant fromEpoch(long e){ return Instant.ofEpochSecond(e); }
}
