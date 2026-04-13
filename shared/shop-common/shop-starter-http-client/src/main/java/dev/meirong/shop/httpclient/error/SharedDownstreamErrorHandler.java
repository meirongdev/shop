package dev.meirong.shop.httpclient.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.meirong.shop.common.api.ApiResponse;
import dev.meirong.shop.common.error.BusinessException;
import dev.meirong.shop.common.error.CommonErrorCode;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseSpec;

/**
 * Shared error handler for downstream service calls.
 *
 * <p>Parses the response body from a failed downstream request (either
 * {@link ApiResponse} with {@code SC_*} error codes or RFC 7807
 * {@code ProblemDetail}) and maps it to a local {@link BusinessException}.
 *
 * <p>This handler is designed to be used with
 * {@code RestClient.Builder.defaultStatusHandler(HttpStatusCode::isError, handler)}
 * and is automatically applied to all {@code @HttpExchange} proxy calls
 * created via {@link org.springframework.web.client.support.RestClientAdapter}.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/integration/rest-clients.html">Spring REST Clients docs</a>
 */
public final class SharedDownstreamErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(SharedDownstreamErrorHandler.class);

    private final ObjectMapper objectMapper;

    public SharedDownstreamErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Handles an error response from a downstream service.
     *
     * @param request  the outgoing HTTP request
     * @param response the error response from the downstream service
     * @throws BusinessException always throws with downstream error info
     */
    public void handleError(HttpRequest request, ClientHttpResponse response) {
        CommonErrorCode errorCode = CommonErrorCode.DOWNSTREAM_ERROR;
        String message = "Downstream request failed: " + request.getURI();

        try {
            byte[] body = response.getBody().readAllBytes();
            if (body.length > 0) {
                JsonNode errorBody = objectMapper.readTree(body);
                String status = extractStatus(errorBody);
                String downstreamMsg = extractMessage(errorBody);

                if (!status.isBlank()) {
                    errorCode = resolveErrorCode(status);
                }
                if (!downstreamMsg.isBlank()) {
                    message = downstreamMsg;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to parse downstream error body", e);
        }

        throw new BusinessException(errorCode, message);
    }

    private static String extractStatus(JsonNode body) {
        // Try ApiResponse.status
        String code = body.path("status").asText();
        if (code.isBlank()) {
            code = body.path("code").asText();
        }
        // Try ProblemDetail.status
        if (code.isBlank()) {
            code = body.path("status").asText();
        }
        // Try ProblemDetail.type (use last segment as status)
        if (code.isBlank()) {
            String type = body.path("type").asText();
            if (!type.isBlank() && type.contains("/")) {
                code = type.substring(type.lastIndexOf('/') + 1);
            }
        }
        return code;
    }

    private static String extractMessage(JsonNode body) {
        // Try ApiResponse.message
        String msg = body.path("message").asText();
        if (msg.isBlank()) {
            msg = body.path("detail").asText();
        }
        // Try ProblemDetail.title
        if (msg.isBlank()) {
            msg = body.path("title").asText();
        }
        return msg;
    }

    private static CommonErrorCode resolveErrorCode(String code) {
        for (CommonErrorCode errorCode : CommonErrorCode.values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return CommonErrorCode.DOWNSTREAM_ERROR;
    }
}
