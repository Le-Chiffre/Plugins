package io.github.lechiffre.plugins;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Instantiates plugins and services defined in XML files.
 */
public class XmlLoader {
    private XmlLoader() {}

    /**
     * Loads all plugins defined in the provided XML string.
     * @return True if any plugins were loaded.
     */
    public static boolean loadString (String contents) {
        InputStream input = new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8));
        return loadStream(input);
    }

    /**
     * Loads the provided XML file name and the plugins defined in it.
     * @return True if any plugins were loaded.
     */
    public static boolean loadFile (String fileName) {
        try {
            return loadStream(new BufferedInputStream(new FileInputStream(new File(fileName))));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Loads plugins from the provided XML input stream.
     * @return True if any plugins were loaded.
     */
    public static boolean loadStream(InputStream input) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            loadDocument(builder.parse(input));
            return true;
        } catch (SAXException|ParserConfigurationException|IOException e) {
            return false;
        }
    }

    /**
     * Loads all plugins in the provided XML document.
     * @return True if any plugins were loaded.
     */
    private static boolean loadDocument(Document doc) {
        boolean anyLoaded = false;
        final Element root =  doc.getDocumentElement();

        // Find any specified overrides.
        final NodeList overrides = root.getElementsByTagName("Overrides");
        if(overrides != null && overrides.getLength() >= 1) {
            loadOverrides(overrides.item(0));
        }

        // Load defined plugins for each category.
        anyLoaded |= loadCategory(root, "Services", "Service");
        anyLoaded |= loadCategory(root, "Plugins", "Plugin");
        return anyLoaded;
    }

    private static boolean loadCategory(Element root, String catName, String elementName) {
        boolean anyLoaded = false;
        final NodeList nodes = root.getElementsByTagName(catName);
        if(nodes != null && nodes.getLength() >= 1) {
            Node node = nodes.item(0);
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                final Node item = children.item(i);
                if(item.getNodeType() == Node.ELEMENT_NODE && item.getNodeName().equals(elementName)) {
                    final NamedNodeMap attributeMap = item.getAttributes();
                    final Node namedNode = attributeMap.getNamedItem("Name");
                    if(namedNode != null) {
                        final String className = namedNode.getNodeValue();
                        PluginLoader.currentLoader.loadPlugin(className, true);
                        anyLoaded = true;
                    }
                }
            }
        }
        return anyLoaded;
    }

    private static void loadOverrides(Node overrides) {
        NodeList children = overrides.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node item = children.item(i);
            if(item.getNodeType() == Node.ELEMENT_NODE && item.getNodeName().equals("Override")) {
                final NamedNodeMap attributeMap = item.getAttributes();
                final Node typeName = attributeMap.getNamedItem("Type");
                final Node targetName = attributeMap.getNamedItem("Target");
                if(typeName != null && targetName != null) {
                    PluginLoader.currentLoader.override(typeName.getNodeValue(), targetName.getNodeValue());
                }
            }
        }
    }
}
