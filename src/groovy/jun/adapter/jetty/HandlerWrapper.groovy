package jun.adapter.jetty;

import java.io.OutputStream;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request as ServerRequest;
import org.eclipse.jetty.server.handler.AbstractHandler;

import jun.handler.Handler;
import jun.core.Request;
import jun.core.Response;


public class HandlerWrapper extends AbstractHandler {
    public final Handler handler;

    public HandlerWrapper(final Handler handler) {
        this.handler = handler;
    }

    public Map getHeadersFromServletRequest(final HttpServletRequest servletRequest) {
        def headerNames = servletRequest.getHeaderNames();
        def headers = [:];

        headerNames.each { name ->
            headers[name] = servletRequest.getHeader(name);
        }

        return headers;
    }

    public Request makeRequest(final HttpServletRequest servletRequest) {
        final Request req = new Request();

        req.serverPort = servletRequest.getServerPort();
        req.serverName = servletRequest.getServerName();
        req.remoteAddress = servletRequest.getRemoteAddr();
        req.queryString = servletRequest.getQueryString();
        req.path = servletRequest.getRequestURI();
        req.scheme = servletRequest.getScheme();
        req.method = servletRequest.getMethod().toLowerCase();
        req.contentType = servletRequest.getContentType();
        req.encoding = servletRequest.getCharacterEncoding();
        req.contentLength = servletRequest.getContentLength();
        req.body = servletRequest.getInputStream();
        req.headers = this.getHeadersFromServletRequest(servletRequest);
        req.contextPath = servletRequest.getContextPath();

        return req;
    }

    public void updateServletResponse(final HttpServletResponse servletResponse, final Response response) {
        // Set headers
        response.headers.each { key, val ->
            keylowercase = key.toLowerCase();
            if (keylowercase != "content-type") {
                if (val instanceof List) {
                    val.each { servletResponse.addHeader(key, it); }
                } else {
                    servletResponse.setHeader(key, val);
                }
            }
        }

        // Set status code
        servletResponse.setStatus(response.statusCode);
        servletResponse.setContentType(response.contentType);

        // Set encoding if response has not null encoding value
        if (response.encoding) {
            servletResponse.setCharacterEncoding(response.encoding);
        }

        final InputStream input = response.body;
        final OutputStream output = servletResponse.getOutputStream();

        this.copy(input, output, 1024*2);

        input.close();
        output.close();
    }

    public void copy(InputStream input, OutputStream output, final int bufferSize) {
        final byte[] buffer = new byte[bufferSize];

        while (true) {
            final int s = input.read(buffer);

            if (s > 0) {
                output.write(buffer, 0, s);
            } else {
                break;
            }
        }
    }

    public void handle(String target, ServerRequest serverRequest,
                       HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        final Request request = this.makeRequest(servletRequest);
        final Response response = this.handler.handle(request);

        this.updateServletResponse(servletResponse, response);
        serverRequest.setHandled(true);
    }
}