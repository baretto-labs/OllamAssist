package fr.baretto.ollamassist.codec;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.langchain4j.internal.Json;

import java.lang.reflect.Type;

public class DefaultJsonCodec implements Json.JsonCodec {
    private final ObjectMapper mapper;

    public DefaultJsonCodec() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new Jdk8Module());
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Type type) {
        try {
            JavaType javaType = mapper.getTypeFactory().constructType(type);
            return mapper.readValue(json, javaType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to " + type, e);
        }
    }
}
