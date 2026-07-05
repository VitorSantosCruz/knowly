package br.com.conectabyte.knowly.auth;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CaptchaService {

    private static final String SITEVERIFY_URI = "/turnstile/v0/siteverify";

    private final RestClient restClient;
    private final AuthProperties properties;

    public CaptchaService(RestClient.Builder restClientBuilder, AuthProperties properties) {
        this.restClient = restClientBuilder.baseUrl("https://challenges.cloudflare.com").build();
        this.properties = properties;
    }

    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        var response =
                restClient
                        .post()
                        .uri(SITEVERIFY_URI)
                        .body(
                                new TurnstileVerifyRequest(
                                        properties.captcha().turnstileSecret(), token))
                        .retrieve()
                        .body(TurnstileVerifyResponse.class);

        return response != null && response.success();
    }

    private record TurnstileVerifyRequest(String secret, String response) {}

    private record TurnstileVerifyResponse(boolean success) {}
}
