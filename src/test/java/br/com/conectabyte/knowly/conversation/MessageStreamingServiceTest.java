package br.com.conectabyte.knowly.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.Tenant;
import java.util.List;
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
import reactor.core.publisher.Flux;

class MessageStreamingServiceTest {

    private final ConversationService conversationService = mock(ConversationService.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ChatModel chatModel = mock(ChatModel.class);
    private final MessageStreamingService service =
            new MessageStreamingService(
                    conversationService, messageRepository, vectorStore, chatModel);

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
    void streamedDeltasAreConcatenatedAndPersistedAsTheAssistantMessageOnCompletion() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("relevant text", java.util.Map.of())));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chunk("Hello"), chunk(", world!")));

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question");

        verify(messageRepository, times(2))
                .save(
                        argThat(
                                m ->
                                        m.getRole() == MessageRole.ASSISTANT
                                                ? m.getContent().equals("Hello, world!")
                                                : true));
    }

    @Test
    void aChatModelErrorEndsTheStreamRatherThanHanging() {
        Conversation conversation = aConversation();
        when(conversationService.requireOwnConversation(any(), any())).thenReturn(conversation);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(Flux.error(new RuntimeException("provider unavailable")));

        service.sendMessage(conversation.getOwner(), 1L, conversation.getId(), "question");

        verify(messageRepository, times(1)).save(argThat(m -> m.getRole() == MessageRole.USER));
        verify(messageRepository, times(0))
                .save(argThat(m -> m.getRole() == MessageRole.ASSISTANT));
    }
}
