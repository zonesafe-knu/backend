package me.zonesafe.zonesafe_be.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // ZonedDateTime 같은 Java 8 날짜/시간 타입을 JSON으로 변환하기 위한 필수 모듈!
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
