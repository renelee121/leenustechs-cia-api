package mx.com.leenustechs.ciaApi.business.adapters.in;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ApiInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String transactionId = UUID.randomUUID().toString();
        request.setAttribute("transactionId", transactionId);
        log.info("Transaction ID set in request: {}", transactionId);

        Instant t0 = Instant.now();
        request.setAttribute("startTime", t0);
        log.info("Request start time set in request: {}", t0);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        Instant t0 = (Instant) request.getAttribute("startTime");
        Instant t1 = Instant.now();
        long duration = java.time.Duration.between(t0, t1).toMillis();
        log.info("Request completed. Duration: {} ms", duration);
        request.removeAttribute("startTime");
    }
    
}
