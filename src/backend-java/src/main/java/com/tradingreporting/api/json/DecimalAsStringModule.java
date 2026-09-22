package com.tradingreporting.api.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Serializes {@link BigDecimal} as a JSON string preserving the exact scale (e.g. {@code "10.5000"}),
 * matching Pydantic v2's default JSON encoding of {@code Decimal} fields in the Python backend.
 * Accepts both string and numeric JSON on the way in for client compatibility.
 */
public class DecimalAsStringModule extends SimpleModule {

    public DecimalAsStringModule() {
        addSerializer(BigDecimal.class, new BigDecimalStringSerializer());
        addDeserializer(BigDecimal.class, new BigDecimalLenientDeserializer());
    }

    private static final class BigDecimalStringSerializer extends StdSerializer<BigDecimal> {
        BigDecimalStringSerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeString(value.toPlainString());
        }
    }

    private static final class BigDecimalLenientDeserializer extends StdDeserializer<BigDecimal> {
        BigDecimalLenientDeserializer() {
            super(BigDecimal.class);
        }

        @Override
        public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getValueAsString();
            if (text == null) {
                return null;
            }
            return new BigDecimal(text);
        }
    }
}
