package org.modulartestorchestrator.http.model;

import java.util.List;

public record HttpCallRequest<T>(String url, String method, List<Header> headers, T dto) {}
