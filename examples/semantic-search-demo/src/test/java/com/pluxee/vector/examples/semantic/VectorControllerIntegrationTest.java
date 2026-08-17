package com.pluxee.vector.examples.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VectorControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldIndexSearchAskAndDeleteDocument() throws Exception {
        String indexPayload = """
                {
                  "dataset": "it-dataset",
                  "documentId": "rabbitmq.md",
                  "content": "RabbitMQ desacopla processamento assincrono e melhora resiliencia.",
                  "metadata": {
                    "technology": "rabbitmq",
                    "type": "documentation"
                  }
                }
                """;

        mockMvc.perform(post("/api/vector/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(indexPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset").value("it-dataset"))
                .andExpect(jsonPath("$.documentId").value("rabbitmq.md"))
                .andExpect(jsonPath("$.indexedChunks").value(1));

        String searchPayload = """
                {
                  "dataset": "it-dataset",
                  "query": "como desacoplar processamento?",
                  "topK": 3,
                  "filters": {
                    "technology": "rabbitmq"
                  }
                }
                """;

        mockMvc.perform(post("/api/vector/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value("rabbitmq.md"));

        String askPayload = """
                {
                  "dataset": "it-dataset",
                  "question": "Como funciona mensageria?"
                }
                """;

        mockMvc.perform(post("/api/vector/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(askPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Como funciona mensageria?"))
                .andExpect(jsonPath("$.context").value(org.hamcrest.Matchers.containsString("SOURCE: rabbitmq.md")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Contexto recuperado")));

        mockMvc.perform(delete("/api/vector/documents/it-dataset/rabbitmq.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        mockMvc.perform(post("/api/vector/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(searchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}

