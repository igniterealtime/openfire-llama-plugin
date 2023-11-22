<%@ page import="java.util.*" %>
<%@ page import="org.ifsoft.llama.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    LLaMA plugin = LLaMA.self;

    if (update)
    {    
        String username = request.getParameter("username");     
        JiveGlobals.setProperty("llama.username", username);     

        String password = request.getParameter("password");     
        JiveGlobals.setProperty("llama.password", password);   

        String host = request.getParameter("host");     
        JiveGlobals.setProperty("llama.host", host); 
        
        String port = request.getParameter("port");     
        JiveGlobals.setProperty("llama.port", port);   
		
        String alias = request.getParameter("alias");     
        JiveGlobals.setProperty("llama.alias", alias); 
		
        String model_path = request.getParameter("model_path");     
        JiveGlobals.setProperty("llama.model.path", model_path); 	
		
        String model_url = request.getParameter("model_url");     
        JiveGlobals.setProperty("llama.model.url", model_url); 		
        
        String system_prompt = request.getParameter("system_prompt");     
        JiveGlobals.setProperty("llama.system.prompt", system_prompt);   

        String top_k_sampling = request.getParameter("top_k_sampling");     
        JiveGlobals.setProperty("llama.top.k.sampling", top_k_sampling);  
        
        String top_p_sampling = request.getParameter("top_p_sampling");     
        JiveGlobals.setProperty("llama.top.p.sampling", top_p_sampling);   
        
        String predictions = request.getParameter("predictions");     
        JiveGlobals.setProperty("llama.predictions", predictions);         
        
        String temperature = request.getParameter("temperature");     
        JiveGlobals.setProperty("llama.temperature", temperature);         
        
        String cache_prompt = request.getParameter("cache_prompt");
        JiveGlobals.setProperty("llama.cache.prompt", (cache_prompt != null && cache_prompt.equals("on")) ? "true": "false");     

        String enabled = request.getParameter("enabled");
        JiveGlobals.setProperty("llama.enabled", (enabled != null && enabled.equals("on")) ? "true": "false");  		
    }

    String service_url = plugin.getUrl();    

%>
<html>
<head>
   <title><fmt:message key="config.page.settings" /></title>
   <meta name="pageID" content="llama-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
<form action="llama-settings.jsp" method="post">
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.settings.description"/></th>
            </tr>
            </thead>
            <tbody>  
            <tr>
                <td nowrap  colspan="2">
                    <input type="checkbox" name="enabled"<%= (JiveGlobals.getProperty("llama.enabled", "true").equals("true")) ? " checked" : "" %>>
                    <fmt:message key="config.page.configuration.enabled" />       
                </td>  
            </tr>
            <tr>
                <td nowrap  colspan="2">
                    <input type="checkbox" name="cache_prompt"<%= (JiveGlobals.getProperty("llama.cache.prompt", "true").equals("true")) ? " checked" : "" %>>
                    <fmt:message key="config.page.configuration.cache.prompt" />       
                </td>  
            </tr>
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.model.path"/>
                </td>
                <td><input type="text" size="100" maxlength="256" name="model_path" required
                       value="<%= JiveGlobals.getProperty("llama.model.path", plugin.getModelPath()) %>">
                </td>                               
            </tr>			
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.model.url"/>
                </td>
                <td><input type="text" size="100" maxlength="256" name="model_url" required
                       value="<%= JiveGlobals.getProperty("llama.model.url", plugin.getModelUrl()) %>">
                </td>                               
            </tr>  	
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.system.prompt"/>
                </td>
                <td><input type="text" size="100" maxlength="256" name="system_prompt" required
                       value="<%= JiveGlobals.getProperty("llama.system.prompt", plugin.getSystemPrompt()) %>">
                </td>                               
            </tr>			
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.username"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="username" required
                       value="<%= JiveGlobals.getProperty("llama.username", "llama") %>">
                </td>
            </tr>   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.password"/>
                </td>
                <td><input type="password" size="50" maxlength="100" name="password" required
                       value="<%= JiveGlobals.getProperty("llama.password", "llama") %>">
                </td>
            </tr>              
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.alias"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="alias" required
                       value="<%= JiveGlobals.getProperty("llama.alias", "LLaMA") %>">
                </td>                               
            </tr> 
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.host"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="host" required
                       value="<%= JiveGlobals.getProperty("llama.host", plugin.getIpAddress()) %>">
                </td>                               
            </tr> 			
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.port"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="port" required
                       value="<%= JiveGlobals.getProperty("llama.port", plugin.getPort()) %>">
                </td>                               
            </tr>  			
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.predictions"/>
                </td>
                <td><input type="text" size="20" name="predictions" required
                       value="<%= JiveGlobals.getProperty("llama.predictions", "256") %>">
                </td>                               
            </tr>                   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.temperature"/>
                </td>
                <td><input type="text" size="20" name="temperature" required
                       value="<%= JiveGlobals.getProperty("llama.temperature", "0.5") %>">
                </td>                               
            </tr>            
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.top.k.sampling"/>
                </td>
                <td><input type="text" size="20" name="top_k_sampling" required
                       value="<%= JiveGlobals.getProperty("llama.top.k.sampling", "40") %>">
                </td>                               
            </tr> 
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.top.p.sampling"/>
                </td>
                <td><input type="text" size="20" name="top_p_sampling" required
                       value="<%= JiveGlobals.getProperty("llama.top.p.sampling", "0.9") %>">
                </td>                               
            </tr> 			
            </tbody>
        </table>
    </p>
   <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.save.title"/></th>
            </tr>
            </thead>
            <tbody>         
            <tr>
                <th colspan="2"><input type="submit" name="update" value="<fmt:message key="config.page.configuration.submit" />">&nbsp;&nbsp;<fmt:message key="config.page.configuration.restart.warning"/></th>
            </tr>       
            </tbody>            
        </table> 
    </p>
</form>
</div>
</body>
</html>
