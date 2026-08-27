package br.com.globoplast.oee.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Evita que outra máquina reutilize o documento inicial de uma versão antiga. */
@Component
public final class CachePolicyFilter implements Filter {
    private static final String NO_STORE = "no-store, no-cache, must-revalidate, max-age=0";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest http && response instanceof HttpServletResponse httpResponse) {
            String path = http.getRequestURI();
            if ("/".equals(path) || "/index.html".equals(path) || "/health".equals(path)) {
                httpResponse.setHeader("Cache-Control", NO_STORE);
                httpResponse.setHeader("Pragma", "no-cache");
                httpResponse.setDateHeader("Expires", 0);
            }
        }
        chain.doFilter(request, response);
    }
}
