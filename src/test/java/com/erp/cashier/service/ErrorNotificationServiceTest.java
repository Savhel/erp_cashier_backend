package com.erp.cashier.service;

import com.erp.cashier.security.JwtPayload;
import com.erp.cashier.service.ErrorNotificationService.TelegramTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.RequestPath;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrorNotificationServiceTest {

    @Mock
    private R2dbcEntityTemplate entityTemplate;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private DatabaseClient databaseClient;
    @Mock
    private GenericExecuteSpec genericExecuteSpec;
    @Mock
    private RowsFetchSpec<TelegramTarget> rowsFetchSpec;
    @Mock
    private ServerWebExchange exchange;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private RequestPath requestPath;
    @Mock
    private Authentication authentication;

    private ErrorNotificationService service;

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        service = new ErrorNotificationService(entityTemplate, webClientBuilder);
    }

    @Test
    void notifyError_shouldNotifyAgencyAdmin_whenCashierErrors() {
        // Arrange
        setupExchange("cashier", null, "agency-1", null);
        RuntimeException ex = new RuntimeException("Something went wrong");

        // Mock Database
        when(entityTemplate.getDatabaseClient()).thenReturn(databaseClient);
        when(databaseClient.sql(anyString())).thenReturn(genericExecuteSpec);
        when(genericExecuteSpec.bind(anyString(), any())).thenReturn(genericExecuteSpec);
        when(genericExecuteSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);

        TelegramTarget target = new TelegramTarget("123456", "bot_token_agency");
        when(rowsFetchSpec.all()).thenReturn(Flux.just(target));

        mockWebClientSuccess();

        // Act
        StepVerifier.create(service.notifyError(exchange, ex))
                .verifyComplete();

        // Assert
        verify(genericExecuteSpec).bind("roleType", "agency_admin");
        verify(genericExecuteSpec).bind("agencyId", "agency-1");
        verify(webClient).post();
    }

    @Test
    void notifyError_shouldNotifyOrgAdmin_whenAgencyAdminErrors() {
        // Arrange
        setupExchange("admin", "agency_admin", "agency-1", "org-1");
        RuntimeException ex = new RuntimeException("Admin error");

        // Mock Database
        when(entityTemplate.getDatabaseClient()).thenReturn(databaseClient);
        when(databaseClient.sql(anyString())).thenReturn(genericExecuteSpec);
        when(genericExecuteSpec.bind(anyString(), any())).thenReturn(genericExecuteSpec);
        when(genericExecuteSpec.map(any(BiFunction.class))).thenReturn(rowsFetchSpec);

        TelegramTarget target = new TelegramTarget("987654", "bot_token_org");
        when(rowsFetchSpec.all()).thenReturn(Flux.just(target));

        mockWebClientSuccess();

        // Act
        StepVerifier.create(service.notifyError(exchange, ex))
                .verifyComplete();

        // Assert
        verify(genericExecuteSpec).bind("roleType", "organization_admin");
        verify(genericExecuteSpec).bind("organizationId", "org-1");
        verify(webClient).post();
    }

    private void mockWebClientSuccess() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));
    }

    private void setupExchange(String role, String roleType, String agencyId, String orgId) {
        JwtPayload payload = new JwtPayload("user-1", "user", role, roleType, orgId, agencyId,
                Instant.now().plusSeconds(3600));

        when(exchange.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(requestPath);
        when(requestPath.value()).thenReturn("/api/test");

        // Fix for method name()
        when(request.getMethod()).thenReturn(org.springframework.http.HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("/api/test"));
        when(request.getId()).thenReturn("req-1");

        when(exchange.getResponse()).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(500));

        // Principal
        when(exchange.getPrincipal()).thenReturn(Mono.just(authentication));
        when(authentication.getDetails()).thenReturn(payload);
    }
}
