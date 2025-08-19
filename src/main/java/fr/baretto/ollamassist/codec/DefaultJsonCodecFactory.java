package fr.baretto.ollamassist.codec;

import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;

/**
 * Default implementation of {@link JsonCodecFactory} for OllamAssist.
 *
 * LangChain4j requires a JSON codec to serialize and deserialize objects.
 * Since OllamAssist does not rely on external JSON libraries (e.g., Jackson or Gson)
 * in its classpath by default, we provide our own implementation of a codec.
 *
 * This factory ensures that whenever LangChain4j needs a {@link Json.JsonCodec},
 * it can create and use {@link DefaultJsonCodec}.
 *
 * Without this, the framework would not be able to handle JSON
 * serialization and deserialization correctly, leading to runtime errors.
 */
public class DefaultJsonCodecFactory implements JsonCodecFactory {
    @Override
    public Json.JsonCodec create() {
        return new DefaultJsonCodec();
    }
}
