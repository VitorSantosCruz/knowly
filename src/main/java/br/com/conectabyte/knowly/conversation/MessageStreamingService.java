package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.auth.User;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class MessageStreamingService {

    private static final Logger log = LoggerFactory.getLogger(MessageStreamingService.class);
    private static final double SIMILARITY_THRESHOLD = 0.5;
    private static final int TOP_K = 5;
    private static final String SYSTEM_PROMPT_WITH_CONTEXT =
            """
            You are a helpful assistant answering questions using only the following context \
            extracted from the tenant's articles. If the context does not answer the question, \
            say so plainly instead of guessing.

            Context:
            %s""";
    private static final String SYSTEM_PROMPT_WITHOUT_CONTEXT =
            """
            You are a helpful assistant. No matching articles were found for this question in \
            the tenant's knowledge base. Tell the user plainly that you found no matching \
            articles, rather than answering from general knowledge as if you had.""";

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final MessageArticleCitationRepository messageArticleCitationRepository;
    private final ArticleRepository articleRepository;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    public MessageStreamingService(
            ConversationService conversationService,
            MessageRepository messageRepository,
            MessageArticleCitationRepository messageArticleCitationRepository,
            ArticleRepository articleRepository,
            VectorStore vectorStore,
            ChatModel chatModel) {
        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
        this.messageArticleCitationRepository = messageArticleCitationRepository;
        this.articleRepository = articleRepository;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
    }

    public SseEmitter sendMessage(User owner, Long tenantId, Long conversationId, String content) {
        return sendMessage(owner, tenantId, conversationId, content, new SseEmitter(0L));
    }

    /** Package-private seam so tests can observe exactly what's sent through the emitter. */
    SseEmitter sendMessage(
            User owner, Long tenantId, Long conversationId, String content, SseEmitter emitter) {
        Conversation conversation =
                conversationService.requireOwnConversation(owner, conversationId);
        messageRepository.save(new Message(conversation, MessageRole.USER, content));

        List<Document> relevantChunks = retrieveRelevantChunks(tenantId, content);
        Prompt prompt = buildPrompt(conversationId, relevantChunks);

        StringBuilder fullResponse = new StringBuilder();

        chatModel.stream(prompt)
                .subscribe(
                        chatResponse -> onNext(emitter, fullResponse, chatResponse),
                        error -> onError(emitter, conversationId, error),
                        () -> onComplete(emitter, conversation, fullResponse, relevantChunks));

        return emitter;
    }

    private List<Document> retrieveRelevantChunks(Long tenantId, String query) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(TOP_K)
                        .similarityThreshold(SIMILARITY_THRESHOLD)
                        .filterExpression(
                                new FilterExpressionBuilder().eq("tenant_id", tenantId).build())
                        .build());
    }

    private Prompt buildPrompt(Long conversationId, List<Document> chunks) {
        SystemMessage systemMessage =
                chunks.isEmpty()
                        ? new SystemMessage(SYSTEM_PROMPT_WITHOUT_CONTEXT)
                        : new SystemMessage(
                                SYSTEM_PROMPT_WITH_CONTEXT.formatted(joinChunks(chunks)));
        List<org.springframework.ai.chat.messages.Message> history =
                messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                        .map(this::toChatMessage)
                        .toList();

        return new Prompt(Stream.concat(Stream.of(systemMessage), history.stream()).toList());
    }

    private String joinChunks(List<Document> chunks) {
        return chunks.stream().map(Document::getText).reduce("", (a, b) -> a + "\n---\n" + b);
    }

    private org.springframework.ai.chat.messages.Message toChatMessage(Message message) {
        return message.getRole() == MessageRole.USER
                ? new UserMessage(message.getContent())
                : new AssistantMessage(message.getContent());
    }

    private void onNext(
            SseEmitter emitter,
            StringBuilder fullResponse,
            org.springframework.ai.chat.model.ChatResponse chatResponse) {
        String delta = chatResponse.getResult().getOutput().getText();

        if (delta == null || delta.isEmpty()) {
            return;
        }

        fullResponse.append(delta);
        try {
            emitter.send(SseEmitter.event().name("message").data(delta));
        } catch (Exception e) {
            log.error("conversation.stream_send_failed reason={}", e.getMessage());
        }
    }

    private void onError(SseEmitter emitter, Long conversationId, Throwable error) {
        log.error(
                "conversation.stream_failed conversationId={} reason={}",
                conversationId,
                error.getMessage());
        try {
            emitter.send(SseEmitter.event().name("error").data("The assistant is unavailable."));
        } catch (Exception e) {
            log.error("conversation.stream_send_failed reason={}", e.getMessage());
        } finally {
            emitter.completeWithError(error);
        }
    }

    private void onComplete(
            SseEmitter emitter,
            Conversation conversation,
            StringBuilder fullResponse,
            List<Document> relevantChunks) {
        Message assistantMessage =
                messageRepository.save(
                        new Message(conversation, MessageRole.ASSISTANT, fullResponse.toString()));
        recordCitations(assistantMessage, relevantChunks);
        try {
            emitter.send(SseEmitter.event().name("done").data(""));
        } catch (Exception e) {
            log.error("conversation.stream_send_failed reason={}", e.getMessage());
        } finally {
            emitter.complete();
        }
    }

    private void recordCitations(Message assistantMessage, List<Document> relevantChunks) {
        Set<Long> distinctArticleIds = new LinkedHashSet<>();

        for (Document chunk : relevantChunks) {
            Object articleId = chunk.getMetadata().get("article_id");

            if (articleId instanceof Number number) {
                distinctArticleIds.add(number.longValue());
            }
        }

        for (Long articleId : distinctArticleIds) {
            Article article = articleRepository.getReferenceById(articleId);
            messageArticleCitationRepository.save(
                    new MessageArticleCitation(assistantMessage, article));
        }
    }
}
