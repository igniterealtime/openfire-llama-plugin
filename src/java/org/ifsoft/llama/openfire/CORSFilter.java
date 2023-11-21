package org.ifsoft.llama.openfire;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class CORSFilter implements Filter {
    private static final Logger Log = LoggerFactory.getLogger(CORSFilter.class);
	
	public CORSFilter() {

	}

	public void destroy() {

	}

	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)	throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		Log.debug("CORSFilter HTTP Request: " + request.getMethod());

		((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Private-Network", "true");
		((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Origin", "*");
		((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Headers", "*");
		((HttpServletResponse) servletResponse).addHeader("Access-Control-Request-Headers", "*");
		((HttpServletResponse) servletResponse).addHeader("Access-Control-Allow-Methods","GET, OPTIONS, HEAD, PUT, POST");
		
		HttpServletResponse resp = (HttpServletResponse) servletResponse;

		if (request.getMethod().equals("OPTIONS")) {
			resp.setStatus(HttpServletResponse.SC_ACCEPTED);
			return;
		}
		chain.doFilter(request, servletResponse);
	}

	public void init(FilterConfig fConfig) throws ServletException {

	}
}