package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.Tenant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

class MessageStreamingServiceTest {

    /**
     * Captures exactly what the service sends over SSE — event name + raw data — without requiring
     * a real HTTP request/response to be attached, and tracks completion state.
     */
    private static class RecordingSseEmitter extends SseEmitter {
        final List<String> events = new ArrayList<>();
        boolean completed;
        Throwable completedWithError;

        @Override
        public void send(SseEventBuilder builder) {
            StringBuilder event = new StringBuilder();
            for (ResponseBodyEmitter.DataWithMediaType data : builder.build()) {
                event.append(data.getData());
            }
            events.add(event.toString());
        }

        @Override
        public void complete() {
            completed = true;
        }

        @Override
        public void completeWithError(Throwable ex) {
            completedWithError = ex;
        }
    }

    private final ConversationService conversationService = mock(ConversationService.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final MessageArticleCitationRepository messageArticleCitationRepository =
            mock(MessageArticleCitationRepository.class);
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final MessageStreamingService service =
            new MessageStreamingService(
                    conversationService,
                    messageRepository,
                    messageArticleCitationRepository,
                    articleRepository,
                    vectorStore,
                    chatModel);

    private Conversation aConversation() {
        Tenant tenant = new Tenant("Tenant");
        User owner = new User("owner@example.com");
        Conversation conversation = new Conversation(tenant, owner);
        conversation.setId(10L);
        return conversation;
    }

    private ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void persistsTheUserMessageBeforeCallingTheChatModel() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "What is X?");

        InOrder order = inOrder(messageRepository, chatModel);
        order.verify(messageRepository)
                .save(
                        argThat(
                                m ->
                                        m.getRole() == MessageRole.USER
                                                && m.getContent().equals("What is X?")));
        order.verify(chatModel).stream(any(Prompt.class));
    }

    @Test
    void retrievalIsScopedToTheCallersTenant() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        service.sendMessage(conversation.getOwner(), 42L, conversation.getId(), "question");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression().toString())
                .contains("tenant_id")
                .contains("42");
    }

    @Test
    void withNoRelevantContextThePromptTellsTheModelToSayNoArticlesWereFound() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).stream(captor.capture());
        assertThat(captor.getValue().getSystemMessage().getText()).contains("no matching articles");
    }

    @Test
    void streamedDeltasProduceOneSseMessageEventEachThenADoneEvent() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("relevant text", java.util.Map.of())));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("Hello"), chunk(", world!")));
        RecordingSseEmitter emitter = new RecordingSseEmitter();

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question", emitter);

        assertThat(emitter.events).hasSize(3);
        assertThat(emitter.events.get(0)).contains("event:message").contains("data:Hello");
        assertThat(emitter.events.get(1)).contains("event:message").contains("data:, world!");
        assertThat(emitter.events.get(2)).contains("event:done");
        assertThat(emitter.completed).isTrue();
        assertThat(emitter.completedWithError).isNull();
        verify(messageRepository, times(2))
                .save(
                        argThat(
                                m ->
                                        m.getRole() == MessageRole.ASSISTANT
                                                ? m.getContent().equals("Hello, world!")
                                                : true));
    }

    @Test
    void recordsOneCitationPerDistinctArticleAmongTheRetrievedChunks() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chunk("Answer")));
        Article articleA = mock(Article.class);
        Article articleB = mock(Article.class);
        when(articleRepository.getReferenceById(1L)).thenReturn(articleA);
        when(articleRepository.getReferenceById(2L)).thenReturn(articleB);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(
                        List.of(
                                new Document("chunk 1 of article A", Map.of("article_id", 1L)),
                                new Document("chunk 2 of article A", Map.of("article_id", 1L)),
                                new Document("chunk of article B", Map.of("article_id", 2L))));

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question");

        verify(messageArticleCitationRepository, times(2)).save(any());
    }

    @Test
    void aFailedStreamRecordsNoCitations() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("chunk", Map.of("article_id", 1L))));
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("provider unavailable")));

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question");

        verify(messageArticleCitationRepository, times(0)).save(any());
    }

    @Test
    void aChatModelErrorSendsAnSseErrorEventAndEndsTheStreamRatherThanHanging() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        RuntimeException providerError = new RuntimeException("provider unavailable");
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(providerError));
        RecordingSseEmitter emitter = new RecordingSseEmitter();

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question", emitter);

        assertThat(emitter.events).hasSize(1);
        assertThat(emitter.events.get(0)).contains("event:error");
        assertThat(emitter.completedWithError).isSameAs(providerError);
        assertThat(emitter.completed).isFalse();
        verify(messageRepository, times(1)).save(argThat(m -> m.getRole() == MessageRole.USER));
        verify(messageRepository, times(0))
                .save(argThat(m -> m.getRole() == MessageRole.ASSISTANT));
    }
}
