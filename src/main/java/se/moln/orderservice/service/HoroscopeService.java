package se.moln.orderservice.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import se.moln.orderservice.dto.HoroscopeRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class HoroscopeService {

    @Value("${OPENAI_API_KEY:}")
    private String openAiApiKey;

    @Value("${OPENAI_MODEL:gpt-4o-mini}")
    private String openAiModel;

    // Gör endpoint och auth scheme konfigurerbar så alternativa leverantörer (OpenAI‑kompatibla) kan användas
    @Value("${OPENAI_API_BASE:https://api.openai.com/v1}")
    private String openAiApiBase;

    @Value("${OPENAI_AUTH_SCHEME:Bearer}")
    private String openAiAuthScheme; // Scheme used in Authorization header (e.g., Bearer, Api-Key)

    // Allow custom header name and optional prefix for providers that don't use Authorization
    @Value("${OPENAI_AUTH_HEADER:Authorization}")
    private String openAiAuthHeader;

    @Value("${OPENAI_AUTH_PREFIX:}")
    private String openAiAuthPrefix; // e.g. empty, or "Api-Key ", etc.

    @Value("${HOROSCOPE_STORAGE_DIR:./data/horoscopes}")
    private String storageDir;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String generateHoroscopePdf(HoroscopeRequest req) {
        ensureApiKey();
        ensureStorageDir();

        String prompt = buildPrompt(req);
        String text = callOpenAi(prompt);

        String id = UUID.randomUUID().toString();
        File outFile = new File(storageDir, id + ".pdf");
        try {
            writePdf(req, text, outFile);
        } catch (IOException | DocumentException e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
        return id;
    }

    public File resolvePdf(String id) {
        File f = new File(storageDir, id + ".pdf");
        return f.exists() ? f : null;
    }

    private void ensureApiKey() {
        String mock = System.getenv("MOCK_OPENAI");
        if ("true".equalsIgnoreCase(mock)) {
            // allow mock mode without API key
            return;
        }
        if (!StringUtils.hasText(openAiApiKey)) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }
    }

    private void ensureStorageDir() {
        File dir = new File(storageDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create storage dir: " + storageDir);
        }
    }

    private String buildPrompt(HoroscopeRequest r) {
        String birthTime = StringUtils.hasText(r.getBirthTime()) ? r.getBirthTime() : "unknown";
        return "Generate a concise, inspiring personal horoscope in professional English for the following person.\n" +
                "Avoid factual planetary positions; keep it uplifting and practical.\n" +
                "Return plain text under 400 words.\n\n" +
                "Name: " + r.getName() + "\n" +
                "Gender: " + r.getGender() + "\n" +
                "Date of birth: " + r.getBirthDate() + "\n" +
                "Place of birth: " + r.getBirthPlace() + "\n" +
                "Time of birth: " + birthTime + "\n\n" +
                "Structure with short sections: Introduction (2–3 sentences); Strengths (bulleted); Relationships (2–3 sentences); Career & Growth (2–3 sentences); Guidance next 3 months (3–4 bullets).";
    }

    private String callOpenAi(String prompt) {
        // Support mock mode for local dev/testing
        if ("true".equalsIgnoreCase(System.getenv("MOCK_OPENAI"))) {
            return "Introduction: A personalized horoscope preview.\n\nStrengths:\n- Curious and resilient\n- Kind and collaborative\n\nRelationships: You communicate clearly and bring warmth to your circle.\n\nCareer & Growth: Focus on one key goal; your consistency will pay off.\n\nGuidance next 3 months:\n- Prioritize sleep\n- Take a 20‑minute walk daily\n- Journal weekly intentions\n- Celebrate small wins.";
        }
        String base = openAiApiBase != null ? openAiApiBase.replaceAll("/+$", "") : "https://api.openai.com/v1";
        boolean use1MinAi = base.contains("api.1min.ai") || "1MINAI".equalsIgnoreCase(System.getenv("OPENAI_PROVIDER"));

        Map<String, Object> payload;
        URI uri;
        if (use1MinAi) {
            // 1minAI non-streaming features endpoint
            uri = URI.create(base + "/api/features");
            payload = Map.of(
                    "type", "CHAT_WITH_AI",
                    "model", openAiModel,
                    "promptObject", Map.of(
                            "prompt", prompt,
                            "isMixed", false,
                            "webSearch", false,
                            "maxWord", 500
                    )
            );
        } else {
            // OpenAI-compatible chat completions
            payload = Map.of(
                    "model", openAiModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are an expert astrologer and skilled writer."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.7
            );
            uri = URI.create(base + "/chat/completions");
        }
        String json = toJson(payload);
        String authScheme = (openAiAuthScheme == null || openAiAuthScheme.isBlank()) ? "Bearer" : openAiAuthScheme.trim();

        // Build auth header flexibly
        String headerName = (openAiAuthHeader == null || openAiAuthHeader.isBlank()) ? HttpHeaders.AUTHORIZATION : openAiAuthHeader.trim();
        String headerValue;
        if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(headerName)) {
            headerValue = authScheme + " " + openAiApiKey;
        } else {
            String prefix = (openAiAuthPrefix == null) ? "" : openAiAuthPrefix;
            headerValue = prefix + openAiApiKey;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header(headerName, headerValue)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new RuntimeException("OpenAI request failed: " + resp.statusCode() + " - " + resp.body());
            }
            Map<String, Object> response = parseJson(resp.body());
            // Branch: parse 1minAI response
            if (use1MinAi) {
                // Expect aiRecord.aiRecordDetail.resultObject containing the text
                Object aiRecord = response.get("aiRecord");
                if (aiRecord instanceof Map<?, ?> ar) {
                    Object detail = ((Map<?, ?>) ar).get("aiRecordDetail");
                    if (detail instanceof Map<?, ?> d) {
                        Object resultObject = d.get("resultObject");
                        if (resultObject instanceof String s && StringUtils.hasText(s)) return s.trim();
                        if (resultObject instanceof List<?> list && !list.isEmpty()) {
                            Object first = list.get(0);
                            if (first instanceof String s && StringUtils.hasText(s)) return s.trim();
                            if (first instanceof Map<?, ?> m && m.get("text") instanceof String s2 && StringUtils.hasText(s2)) return s2.trim();
                        }
                    }
                }
                Object err = response.get("error");
                if (err instanceof Map<?, ?> em && ((Map<?, ?>) em).get("message") instanceof String m && StringUtils.hasText(m)) {
                    throw new RuntimeException("1minAI error: " + m);
                }
                throw new RuntimeException("Unexpected 1minAI response: " + resp.body());
            }
            // Standard Chat Completions: choices[0].message.content (string)
            try {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                Map<String, Object> first = choices != null && !choices.isEmpty() ? choices.get(0) : null;
                Map<String, Object> message = first != null ? (Map<String, Object>) first.get("message") : null;
                Object contentObj = message != null ? message.get("content") : null;
                String content = contentObj instanceof String ? (String) contentObj : null;
                if (StringUtils.hasText(content)) return content.trim();
            } catch (Exception ignore) { }

            // Fallback: sometimes providers return 'choices[0].text'
            try {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                Map<String, Object> first = choices != null && !choices.isEmpty() ? choices.get(0) : null;
                Object text = first != null ? first.get("text") : null;
                if (text instanceof String s && StringUtils.hasText(s)) return s.trim();
            } catch (Exception ignore) { }

            // Another fallback: Responses API style 'output_text'
            Object out = response.get("output_text");
            if (out instanceof String s && StringUtils.hasText(s)) return s.trim();

            // If API returned an error object but with 200 status
            Object err = response.get("error");
            if (err instanceof Map<?, ?> em && ((Map<?, ?>) err).get("message") instanceof String m && StringUtils.hasText(m)) {
                throw new RuntimeException("OpenAI logical error: " + m);
            }

            throw new RuntimeException("Unexpected OpenAI response: " + resp.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI request error", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected OpenAI response format", e);
        }
    }

    // Minimal JSON helpers using simple String building and Jackson if available via Spring (fallback naive)
    private String toJson(Map<String, Object> map) {
        try {
            // Prefer Jackson if on classpath
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.writeValueAsString(map);
        } catch (Throwable ignore) {
            // Naive fallback
            return map.toString();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(json, Map.class);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    private void writePdf(HoroscopeRequest req, String content, File out) throws IOException, DocumentException {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, fos);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            doc.add(new Paragraph("Personal AI Horoscope", titleFont));
            doc.add(new Paragraph("Name: " + req.getName(), normalFont));
            doc.add(new Paragraph("Birth date: " + req.getBirthDate(), normalFont));
            doc.add(new Paragraph("Birth place: " + req.getBirthPlace(), normalFont));
            if (StringUtils.hasText(req.getBirthTime())) {
                doc.add(new Paragraph("Birth time: " + req.getBirthTime(), normalFont));
            }
            doc.add(new Paragraph("\n"));

            // Split content into paragraphs by double newlines
            String[] blocks = content.split("\n\n+");
            for (String b : blocks) {
                String trimmed = b.trim();
                if (trimmed.isEmpty()) continue;
                // Heuristic: first line as section header if ends with ':'
                String[] lines = trimmed.split("\n");
                if (lines.length > 1 && lines[0].endsWith(":")) {
                    doc.add(new Paragraph(lines[0], sectionFont));
                    String rest = String.join("\n", java.util.Arrays.copyOfRange(lines, 1, lines.length));
                    doc.add(new Paragraph(rest, normalFont));
                } else {
                    doc.add(new Paragraph(trimmed, normalFont));
                }
                doc.add(new Paragraph("\n"));
            }

            doc.close();
        }
    }
}
