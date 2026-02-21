package com.sh3d.mcp.command;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ColorParserTest {

    // ==================== parseRequired ====================

    @Nested
    class ParseRequired {

        @Test
        void returnsNullWhenKeyNotPresent() {
            Map<String, Object> params = new LinkedHashMap<>();
            assertNull(ColorParser.parseRequired(params, "color"));
        }

        @Test
        void parsesValidHexColor() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#FF0000");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0xFF0000, result.value);
            assertFalse(result.clear);
            assertNull(result.error);
        }

        @Test
        void parsesBlack() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#000000");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0x000000, result.value);
        }

        @Test
        void parsesWhite() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#FFFFFF");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0xFFFFFF, result.value);
        }

        @Test
        void parsesLowerCaseHex() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#abcdef");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0xABCDEF, result.value);
        }

        @Test
        void returnsErrorForNullValue() {
            Map<String, Object> params = new HashMap<>();
            params.put("color", null);
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("color"));
            assertTrue(result.error.contains("cannot be null"));
        }

        @Test
        void returnsErrorForInvalidHexFormat() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "red");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("Invalid"));
            assertTrue(result.error.contains("color"));
            assertTrue(result.error.contains("red"));
            assertTrue(result.error.contains("#RRGGBB"));
        }

        @Test
        void returnsErrorForHexWithoutHash() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "FF0000");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("Invalid"));
        }

        @Test
        void returnsErrorForShortHex() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#FFF");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
        }

        @Test
        void returnsErrorForEmptyString() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("Invalid"));
        }

        @Test
        void usesKeyNameInNullErrorMessage() {
            Map<String, Object> params = new HashMap<>();
            params.put("wallColor", null);
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "wallColor");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("wallColor"));
        }

        @Test
        void usesKeyNameInInvalidFormatErrorMessage() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("floorColor", "invalid");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "floorColor");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("floorColor"));
        }

        @Test
        void convertsNonStringObjectToString() {
            // params.get(key) returns an Integer, toString() is called
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", 12345);
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            // 12345.toString() = "12345" which doesn't match #RRGGBB
        }

        @Test
        void neverSetsClearOnRequired() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#FF0000");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertFalse(result.clear);
        }

        @Test
        void returnsNullForKeyNotPresentNotForNullValue() {
            // Distinguish between key-not-present (returns null) and key-present-with-null-value (returns error)
            Map<String, Object> paramsWithoutKey = new LinkedHashMap<>();
            assertNull(ColorParser.parseRequired(paramsWithoutKey, "color"));

            Map<String, Object> paramsWithNull = new HashMap<>();
            paramsWithNull.put("color", null);
            assertNotNull(ColorParser.parseRequired(paramsWithNull, "color"));
        }
    }

    // ==================== parseNullable ====================

    @Nested
    class ParseNullable {

        @Test
        void returnsNullWhenKeyNotPresent() {
            Map<String, Object> params = new LinkedHashMap<>();
            assertNull(ColorParser.parseNullable(params, "color"));
        }

        @Test
        void parsesValidHexColor() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#00FF00");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0x00FF00, result.value);
            assertFalse(result.clear);
            assertNull(result.error);
        }

        @Test
        void returnsClearForNullValue() {
            Map<String, Object> params = new HashMap<>();
            params.put("color", null);
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertTrue(result.clear);
            assertEquals(0, result.value);
            assertNull(result.error);
        }

        @Test
        void returnsErrorForInvalidHexFormat() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "not-a-color");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("Invalid"));
            assertTrue(result.error.contains("color"));
            assertTrue(result.error.contains("not-a-color"));
        }

        @Test
        void returnsErrorForHexWithoutHash() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "00FF00");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
        }

        @Test
        void returnsErrorForEmptyString() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
        }

        @Test
        void parsesLowerCaseHex() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#aabbcc");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0xAABBCC, result.value);
        }

        @Test
        void parsesMixedCaseHex() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#AaBbCc");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
            assertEquals(0xAABBCC, result.value);
        }

        @Test
        void usesKeyNameInErrorMessage() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ceilingColor", "bad");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "ceilingColor");

            assertNotNull(result);
            assertTrue(result.hasError());
            assertTrue(result.error.contains("ceilingColor"));
        }

        @Test
        void neverSetsClearForValidColor() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#123456");
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertFalse(result.clear);
        }

        @Test
        void returnsNullForKeyNotPresentNotForNullValue() {
            // key-not-present -> null (param was not sent)
            // key-present-with-null -> ColorResult with clear=true (user wants to clear)
            Map<String, Object> paramsWithoutKey = new LinkedHashMap<>();
            assertNull(ColorParser.parseNullable(paramsWithoutKey, "color"));

            Map<String, Object> paramsWithNull = new HashMap<>();
            paramsWithNull.put("color", null);
            ColorParser.ColorResult result = ColorParser.parseNullable(paramsWithNull, "color");
            assertNotNull(result);
            assertTrue(result.clear);
        }
    }

    // ==================== ColorResult ====================

    @Nested
    class ColorResultBehavior {

        @Test
        void hasErrorReturnsTrueWhenErrorIsPresent() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "invalid");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertTrue(result.hasError());
        }

        @Test
        void hasErrorReturnsFalseWhenNoError() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#FF0000");
            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
        }

        @Test
        void hasErrorReturnsFalseForClearResult() {
            Map<String, Object> params = new HashMap<>();
            params.put("color", null);
            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");

            assertNotNull(result);
            assertFalse(result.hasError());
        }
    }

    // ==================== Comparison: parseRequired vs parseNullable for null ====================

    @Nested
    class RequiredVsNullableNullHandling {

        @Test
        void requiredReturnsErrorForNull() {
            Map<String, Object> params = new HashMap<>();
            params.put("color", null);

            ColorParser.ColorResult result = ColorParser.parseRequired(params, "color");
            assertNotNull(result);
            assertTrue(result.hasError());
            assertFalse(result.clear);
        }

        @Test
        void nullableReturnsClearForNull() {
            Map<String, Object> params = new HashMap<>();
            params.put("color", null);

            ColorParser.ColorResult result = ColorParser.parseNullable(params, "color");
            assertNotNull(result);
            assertFalse(result.hasError());
            assertTrue(result.clear);
        }

        @Test
        void bothReturnNullWhenKeyAbsent() {
            Map<String, Object> params = new LinkedHashMap<>();

            assertNull(ColorParser.parseRequired(params, "color"));
            assertNull(ColorParser.parseNullable(params, "color"));
        }

        @Test
        void bothParseValidColorIdentically() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "#ABCDEF");

            ColorParser.ColorResult required = ColorParser.parseRequired(params, "color");
            ColorParser.ColorResult nullable = ColorParser.parseNullable(params, "color");

            assertNotNull(required);
            assertNotNull(nullable);
            assertFalse(required.hasError());
            assertFalse(nullable.hasError());
            assertEquals(required.value, nullable.value);
            assertFalse(required.clear);
            assertFalse(nullable.clear);
        }

        @Test
        void bothReturnErrorForInvalidFormat() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("color", "xyz");

            ColorParser.ColorResult required = ColorParser.parseRequired(params, "color");
            ColorParser.ColorResult nullable = ColorParser.parseNullable(params, "color");

            assertNotNull(required);
            assertNotNull(nullable);
            assertTrue(required.hasError());
            assertTrue(nullable.hasError());
        }
    }
}
