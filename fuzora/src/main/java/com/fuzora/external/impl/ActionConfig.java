package com.fuzora.external.impl;

import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

public interface ActionConfig extends Function<JsonNode, Map<String, Object>> {

}
