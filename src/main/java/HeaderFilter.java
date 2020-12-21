import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public final class HeaderFilter implements Filter {

    static class FilteredRequest extends HttpServletRequestWrapper {

        /* These are the characters allowed by the Javascript validation */
        static String allowedChars = "+-0123456789#*";

        public FilteredRequest(ServletRequest request) {
            super((HttpServletRequest)request);
        }

        public String sanitize(String input) {
            String result = "";
            for (int i = 0; i < input.length(); i++) {
                if (allowedChars.indexOf(input.charAt(i)) >= 0) {
                    result += input.charAt(i);
                }
            }
            return result;
        }

        public String getParameter(String paramName) {
            String value = super.getParameter(paramName);
            if ("dangerousParamName".equals(paramName)) {
                value = sanitize(value);
            }
            return value;
        }

        public String[] getParameterValues(String paramName) {
            String values[] = super.getParameterValues(paramName);
            if ("dangerousParamName".equals(paramName)) {
                for (int index = 0; index < values.length; index++) {
                    values[index] = sanitize(values[index]);
                }
            }
            return values;
        }
    }

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        chain.doFilter(new FilteredRequest(request), response);
    }

    public void destroy() {
    }

    public void init(FilterConfig filterConfig) {
    }
}