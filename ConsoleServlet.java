/*
Copyright 2011 Selenium committers
Copyright 2011 Software Freedom Conservancy
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
     http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import com.google.common.io.ByteStreams;

import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;

import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Front end to monitor what is currently happening on the proxies. The display is defined by
 * HtmlRenderer returned by the RemoteProxy.getHtmlRenderer() method.
 */
public class ConsoleServlet extends RegistryBasedServlet {

    private static final long serialVersionUID = 8484071790930378855L;
    private static final Logger log = Logger.getLogger(ConsoleServlet.class.getName());
    private static String coreVersion;
    private static String coreRevision;

    public ConsoleServlet() {
        this(null);
    }

    public ConsoleServlet(Registry registry) {
        super(registry);
        getVersion();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }

    protected void process(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int refresh = -1;

        if (request.getParameter("refresh") != null) {
            try {
                refresh = Integer.parseInt(request.getParameter("refresh"));
            } catch (NumberFormatException e) {
                // ignore wrong param
            }

        }

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);

        StringBuilder builder = new StringBuilder();

        builder.append("<html>");
        builder.append("<head>");

        if (refresh != -1) {
            builder.append(String.format("<meta http-equiv='refresh' content='%d' />", refresh));
        }
        builder.append("<title>Grid overview</title>");

        builder.append("<style>");
        builder.append(".busy {");
        builder.append(" opacity : 0.4;");
        builder.append("filter: alpha(opacity=40);");
        builder.append("}");
        builder.append("</style>");
        builder.append("</head>");

        builder.append("<body>");
        builder.append("<H1>Grid Hub ");
        builder.append(coreVersion).append(coreRevision);
        builder.append("</H1>");

        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            builder.append(proxy.getHtmlRender().renderSummary());
        }

        int numUnprocessedRequests = getRegistry().getNewSessionRequestCount();

        if (numUnprocessedRequests > 0) {
            builder.append(String.format("%d requests waiting for a slot to be free.", numUnprocessedRequests));
        }

        builder.append("<ul>");
        for (DesiredCapabilities req : getRegistry().getDesiredCapabilities()) {
            builder.append("<li>").append(req).append("</li>");
        }
        builder.append("</ul>");

        if (request.getParameter("config") != null) {
            builder.append(getConfigInfo(request.getParameter("configDebug") != null));
        } else {
            builder.append("<a href='?config=true&configDebug=true'>view config</a>");
        }

        builder.append("</body>");
        builder.append("</html>");

        InputStream in = new ByteArrayInputStream(builder.toString().getBytes("UTF-8"));
        try {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            in.close();
            response.flushBuffer();
        }
    }

    /**
     * retracing how the hub config was built to help debugging.
     *
     * @return
     */
    private String getConfigInfo(boolean verbose) {
        StringBuilder builder = new StringBuilder();

        GridHubConfiguration config = getRegistry().getConfiguration();
        builder.append("<b>Config for the hub :</b><br/>");
        builder.append(prettyHtmlPrint(config));

        if (verbose) {

            GridHubConfiguration tmp = new GridHubConfiguration();

            builder.append("<b>Config details :</b><br/>");
            builder.append("<b>hub launched with :</b>");
            builder.append(tmp.hubConfig);


            builder.append("<br/><b>the final configuration comes from :</b><br/>");
            builder.append("<b>the default :</b><br/>");
            builder.append(prettyHtmlPrint(tmp));

            builder.append(prettyHtmlPrint(tmp));
        }
        return builder.toString();
    }

    private String key(String key) {
        return "<abbr title='" + "GridDocHelper.GridParam(key)" + "'>" + key + " : </abbr>";
    }

    private String prettyHtmlPrint(GridHubConfiguration config) {
        StringBuilder b = new StringBuilder();

        b.append(key("host")).append(config.host).append("</br>");
        b.append(key("port")).append(config.port).append("</br>");
        b.append(key("cleanUpCycle")).append(config.cleanUpCycle).append("</br>");
        b.append(key("timeout")).append(config.timeout).append("</br>");
        b.append(key("browserTimeout")).append(config.browserTimeout).append("</br>");


        b.append(key("newSessionWaitTimeout")).append(config.newSessionWaitTimeout)
                .append("</br>");
        b.append(key("throwOnCapabilityNotPresent")).append(config.throwOnCapabilityNotPresent)
                .append("</br>");

        b.append(key("capabilityMatcher"))
                .append(
                        config.capabilityMatcher == null ? "null" : config.capabilityMatcher
                                .getClass().getCanonicalName()).append("</br>");
        b.append(key("prioritizer"))
                .append(
                        config.prioritizer == null ? "null" : config.prioritizer.getClass()
                                .getCanonicalName())
                .append("</br>");
        b.append(key("servlets"));
        for (String s : config.servlets) {
            b.append(s.getClass().getCanonicalName()).append(",");
        }
        b.append("</br></br>");
        b.append("<u>all params :</u></br></br>");
        List<String> keys = new ArrayList<String>();
        keys.addAll(config.custom.keySet());
        Collections.sort(keys);
        for (String s : keys) {
            b.append(key(s.replaceFirst("-", ""))).append(config.custom.get(s)).append("</br>");
        }
        b.append("</br>");
        return b.toString();
    }

    private void getVersion() {
        final Properties p = new Properties();

        InputStream stream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("VERSION.txt");
        if (stream == null) {
            log.severe("Couldn't determine version number");
            return;
        }
        try {
            p.load(stream);
        } catch (IOException e) {
            log.severe("Cannot load version from VERSION.txt" + e.getMessage());
        }
        coreVersion = p.getProperty("selenium.core.version");
        coreRevision = p.getProperty("selenium.core.revision");
        if (coreVersion == null) {
            log.severe("Cannot load selenium.core.version from VERSION.txt");
        }
    }

}