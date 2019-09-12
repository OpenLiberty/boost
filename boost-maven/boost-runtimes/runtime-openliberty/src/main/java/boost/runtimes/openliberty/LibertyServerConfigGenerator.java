/*******************************************************************************
 * Copyright (c) 2018, 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package boost.runtimes.openliberty;

import static boost.common.config.ConfigConstants.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import boost.common.BoostLoggerI;
import boost.common.boosters.JDBCBoosterConfig;
import boost.common.config.BoostProperties;
import boost.common.utils.BoostUtil;
import net.wasdev.wlp.common.plugins.util.OSUtil;

/**
 * Create a Liberty server.xml
 *
 */
public class LibertyServerConfigGenerator {

    public static final String CONFIG_DROPINS_DIR = "/configDropins/defaults";

    private final String serverPath;
    private final String libertyInstallPath;
    private final String encryptionKey;

    private final BoostLoggerI logger;

    private Document serverXml;
    private Element featureManager;
    private Element serverRoot;
    private Element httpEndpoint;

    private Document variablesXml;
    private Element variablesRoot;

    private Set<String> featuresAdded;

    public LibertyServerConfigGenerator(String serverPath, String encryptionKey, BoostLoggerI logger)
            throws ParserConfigurationException {

        this.serverPath = serverPath;
        this.libertyInstallPath = serverPath + "/../../.."; // Three directories
                                                            // back from
                                                            // 'wlp/usr/servers/defaultServer'
        this.encryptionKey = encryptionKey;
        this.logger = logger;

        generateServerXml();
        generateVariablesXml();

        featuresAdded = new HashSet<String>();
    }

    private void generateServerXml() throws ParserConfigurationException {
        DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        // Create top level server config element
        serverXml = docBuilder.newDocument();
        serverRoot = serverXml.createElement("server");
        serverRoot.setAttribute("description", "Liberty server generated by Boost");
        serverXml.appendChild(serverRoot);

        // Create featureManager config element
        featureManager = serverXml.createElement(FEATURE_MANAGER);
        serverRoot.appendChild(featureManager);

        // Create httpEndpoint config element
        httpEndpoint = serverXml.createElement(HTTP_ENDPOINT);
        httpEndpoint.setAttribute("id", DEFAULT_HTTP_ENDPOINT);
        serverRoot.appendChild(httpEndpoint);
    }

    private void generateVariablesXml() throws ParserConfigurationException {
        DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        // Create top level server config element
        variablesXml = docBuilder.newDocument();
        variablesRoot = variablesXml.createElement("server");
        variablesRoot.setAttribute("description", "Boost variables");
        variablesXml.appendChild(variablesRoot);
    }

    /**
     * Add a Liberty feature to the server configuration
     *
     */
    public void addFeature(String featureName) {
        if (!featuresAdded.contains(featureName)) {
            Element feature = serverXml.createElement(FEATURE);
            feature.appendChild(serverXml.createTextNode(featureName));
            featureManager.appendChild(feature);
            featuresAdded.add(featureName);
        }
    }

    /**
     * Add a list of features to the server configuration
     *
     */
    public void addFeatures(List<String> features) {

        for (String featureName : features) {
            addFeature(featureName);
        }
    }

    /**
     * Write the server.xml and bootstrap.properties to the server config
     * directory
     *
     * @throws TransformerException
     * @throws IOException
     */
    public void writeToServer() throws TransformerException, IOException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        // Replace auto-generated server.xml
        DOMSource server = new DOMSource(serverXml);
        StreamResult serverResult = new StreamResult(new File(serverPath + "/server.xml"));
        transformer.transform(server, serverResult);

        // Create configDropins/default path
        Path configDropins = Paths.get(serverPath + CONFIG_DROPINS_DIR);
        Files.createDirectories(configDropins);

        // Write variables.xml to configDropins
        DOMSource variables = new DOMSource(variablesXml);
        StreamResult variablesResult = new StreamResult(new File(serverPath + CONFIG_DROPINS_DIR + "/variables.xml"));
        transformer.transform(variables, variablesResult);

    }

    public void addConfigVariables(Properties properties) throws IOException {

        if (properties != null) {
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);

                addConfigVariable(key, value);
            }
        }
    }

    private void addConfigVariable(String key, String value) throws IOException {

        // Using this to hold the properties we want to encrypt and the type of
        // encryption we want to use
        Map<String, String> propertiesToEncrypt = BoostProperties.getPropertiesToEncrypt();

        if (propertiesToEncrypt.containsKey(key) && value != null && !value.equals("")) {
            value = encrypt(key, value);
        }

        Element variable = variablesXml.createElement("variable");
        variable.setAttribute("name", key);
        variable.setAttribute("defaultValue", value);
        variablesRoot.appendChild(variable);
    }

    public void addKeystore(Map<String, String> keystoreProps, Map<String, String> keyProps) {
        Element keystore = serverXml.createElement(KEYSTORE);
        keystore.setAttribute("id", DEFAULT_KEYSTORE);

        for (String key : keystoreProps.keySet()) {
            keystore.setAttribute(key, keystoreProps.get(key));
        }

        if (!keyProps.isEmpty()) {
            Element keyEntry = serverXml.createElement(KEY_ENTRY);

            for (String key : keyProps.keySet()) {
                keyEntry.setAttribute(key, keyProps.get(key));
            }

            keystore.appendChild(keyEntry);
        }

        serverRoot.appendChild(keystore);
    }

    public void addApplication(String appName) {
        Element appCfg = serverXml.createElement(APPLICATION);
        appCfg.setAttribute(CONTEXT_ROOT, "/");
        appCfg.setAttribute(LOCATION, appName + "." + WAR_PKG_TYPE);
        appCfg.setAttribute(TYPE, WAR_PKG_TYPE);
        serverRoot.appendChild(appCfg);

    }

    public void addHostname(String hostname) throws Exception {
        httpEndpoint.setAttribute("host", BoostUtil.makeVariable(BoostProperties.ENDPOINT_HOST));

        addConfigVariable(BoostProperties.ENDPOINT_HOST, hostname);
    }

    public void addHttpPort(String httpPort) throws Exception {
        httpEndpoint.setAttribute("httpPort", BoostUtil.makeVariable(BoostProperties.ENDPOINT_HTTP_PORT));

        addConfigVariable(BoostProperties.ENDPOINT_HTTP_PORT, httpPort);
    }

    public void addHttpsPort(String httpsPort) throws Exception {
        httpEndpoint.setAttribute("httpsPort", BoostUtil.makeVariable(BoostProperties.ENDPOINT_HTTPS_PORT));

        addConfigVariable(BoostProperties.ENDPOINT_HTTPS_PORT, httpsPort);
    }

    public Document getServerXmlDoc() {
        return serverXml;
    }

    private String getSecurityUtilCmd(String libertyInstallPath) {
        if (OSUtil.isWindows()) {
            return libertyInstallPath + "/bin/securityUtility.bat";
        } else {
            return libertyInstallPath + "/bin/securityUtility";
        }
    }

    private String encrypt(String propertyKey, String propertyValue) throws IOException {

        // Won't encode the property if it contains the aes flag
        if (!isEncoded(propertyValue)) {
            Runtime rt = Runtime.getRuntime();
            List<String> commands = new ArrayList<String>();

            commands.add(getSecurityUtilCmd(libertyInstallPath));
            commands.add("encode");
            commands.add(propertyValue);

            // Get the internal encryption type set for this property
            String encryptionType = BoostProperties.getPropertiesToEncrypt().get(propertyKey);
            if (encryptionType != null && !encryptionType.equals("")) {
                commands.add("--encoding=" + encryptionType);
            } else {
                commands.add("--encoding=aes");
            }

            // Set the defined encryption key
            if (encryptionKey != null && !encryptionKey.equals("")) {
                commands.add("--key=" + encryptionKey);
            }

            Process proc = rt.exec(commands.toArray(new String[0]));

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

            BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

            String s = null;

            StringBuilder out = new StringBuilder();
            while ((s = stdInput.readLine()) != null) {
                out.append(s);
            }

            StringBuilder error = new StringBuilder();
            while ((s = stdError.readLine()) != null) {
                error.append(s + "\n");
            }

            if (error.length() != 0) {
                throw new IOException("Password encryption failed: " + error);
            }

            return out.toString();
        }
        return propertyValue;
    }

    public boolean isEncoded(String property) {
        return property.contains("{aes}") || property.contains("{hash}") || property.contains("{xor}");
    }

    public void addDataSource(Map<String, String> driverInfo, Properties datasourceProperties) throws Exception {
        String datasourcePropertiesElement = null;

        String driverName = driverInfo.get(JDBCBoosterConfig.DRIVER_NAME);
        if (driverName.equals(JDBCBoosterConfig.DERBY_DRIVER_NAME)) {
            datasourcePropertiesElement = PROPERTIES_DERBY_EMBEDDED;
        } else if (driverName.equals(JDBCBoosterConfig.DB2_DRIVER_NAME)) {
            datasourcePropertiesElement = PROPERTIES_DB2_JCC;
        } else if (driverName.equals(JDBCBoosterConfig.MYSQL_DRIVER_NAME)) {
            datasourcePropertiesElement = PROPERTIES;
        } else if (driverName.equals(JDBCBoosterConfig.POSTGRESQL_DRIVER_NAME)) {
            datasourcePropertiesElement = PROPERTIES_POSTGRESQL;
        }

        // Add library
        Element lib = serverXml.createElement(LIBRARY);
        lib.setAttribute("id", JDBC_LIBRARY_1);
        Element fileLoc = serverXml.createElement(FILESET);
        fileLoc.setAttribute("dir", RESOURCES);
        fileLoc.setAttribute("includes", driverInfo.get(JDBCBoosterConfig.DRIVER_JAR));
        lib.appendChild(fileLoc);
        serverRoot.appendChild(lib);

        // Add datasource
        Element dataSource = serverXml.createElement(DATASOURCE);
        dataSource.setAttribute("id", DEFAULT_DATASOURCE);
        dataSource.setAttribute(JDBC_DRIVER_REF, JDBC_DRIVER_1);

        // Add all configured datasource properties
        Element props = serverXml.createElement(datasourcePropertiesElement);
        addDatasourceProperties(datasourceProperties, props);
        dataSource.appendChild(props);

        serverRoot.appendChild(dataSource);

        // Add jdbc driver
        Element jdbcDriver = serverXml.createElement(JDBC_DRIVER);
        jdbcDriver.setAttribute("id", JDBC_DRIVER_1);
        jdbcDriver.setAttribute(LIBRARY_REF, JDBC_LIBRARY_1);
        serverRoot.appendChild(jdbcDriver);

        // Add variables
        addConfigVariables(datasourceProperties);
    }

    private void addDatasourceProperties(Properties serverProperties, Element propertiesElement) {
        for (String property : serverProperties.stringPropertyNames()) {
            String attribute = property.replace(BoostProperties.DATASOURCE_PREFIX, "");
            propertiesElement.setAttribute(attribute, BoostUtil.makeVariable(property));
        }
    }

}
