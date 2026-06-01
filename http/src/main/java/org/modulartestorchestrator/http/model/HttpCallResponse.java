package org.modulartestorchestrator.http.model;

import java.util.List;
import java.util.Map;

public record HttpCallResponse<T>(int status, Map<String, List<String>> headers, T dto) {}
