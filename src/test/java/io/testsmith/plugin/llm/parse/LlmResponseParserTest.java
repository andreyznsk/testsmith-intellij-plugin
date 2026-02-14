package io.testsmith.plugin.llm.parse;

import io.testsmith.plugin.llm.api.LlmProtocolException;
import io.testsmith.plugin.llm.api.TestFramework;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmResponseParserTest {
    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void parsesValidJson() {
        String payload = """
                {
                  "testClassFqcn": "com.example.SampleTest",
                  "suggestedFilePath": "src/test/java/com/example/SampleTest.java",
                  "testFramework": "JUNIT5",
                  "javaSource": "package com.example;\\npublic class SampleTest {}",
                  "notes": ["note one", "note two"]
                }
                """;

        var response = parser.parse(payload);

        assertEquals("com.example.SampleTest", response.testClassFqcn());
        assertEquals("src/test/java/com/example/SampleTest.java", response.suggestedFilePath());
        assertEquals(TestFramework.JUNIT5, response.testFramework());
        assertEquals("package com.example;\npublic class SampleTest {}", response.javaSource());
        assertEquals(2, response.notes().size());
    }

    @Test
    void rejectsMarkdownFencedOutput() {
        String payload = """
                {
                  "testClassFqcn": "com.example.SampleTest",
                  "suggestedFilePath": "",
                  "testFramework": "JUNIT5",
                  "javaSource": "```java\\nclass A {}\\n```",
                  "notes": []
                }
                """;

        assertThrows(LlmProtocolException.class, () -> parser.parse(payload));
    }

    @Test
    void rejectsLeadingProseBeforeJson() {
        String payload = "Here is the result:\n{\"testClassFqcn\":\"A\",\"suggestedFilePath\":\"\",\"testFramework\":\"JUNIT5\",\"javaSource\":\"class A {}\",\"notes\":[]}";
        assertThrows(LlmProtocolException.class, () -> parser.parse(payload));
    }

    @Test
    void rejectsNonJsonPayload() {
        assertThrows(LlmProtocolException.class, () -> parser.parse("not-json"));
    }

    @Test
    void rejectsMissingRequiredFields() {
        String payload = """
                {
                  "testClassFqcn": "com.example.SampleTest",
                  "testFramework": "JUNIT5",
                  "javaSource": "class A {}",
                  "notes": []
                }
                """;

        assertThrows(LlmProtocolException.class, () -> parser.parse(payload));
    }

    @Test
    void rejectsUnknownEnumValues() {
        String payload = """
                {
                  "testClassFqcn": "com.example.SampleTest",
                  "suggestedFilePath": "",
                  "testFramework": "JUNIT3",
                  "javaSource": "class A {}",
                  "notes": []
                }
                """;

        assertThrows(LlmProtocolException.class, () -> parser.parse(payload));
    }
}
