package me.zonesafe.zonesafe_be.config;

import me.zonesafe.zonesafe_be.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "me.zonesafe.zonesafe_be.controller")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (ResourceHttpMessageConverter.class.isAssignableFrom(converterType)) return false;
        if (ResourceRegionHttpMessageConverter.class.isAssignableFrom(converterType)) return false;
        if (ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType)) return false;
        if (StringHttpMessageConverter.class.isAssignableFrom(converterType)) return false;
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) return body;
        if (body instanceof Resource) return body;
        if (body instanceof ResourceRegion) return body;
        if (body instanceof byte[]) return body;
        return ApiResponse.success(body);
    }
}