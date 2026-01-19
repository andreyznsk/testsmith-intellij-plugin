package io.testsmith.plugin.coverage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

public final class JacocoXmlCoverageReader implements CoverageReader {
    @Override
    public CoverageSnapshot read(Path jacocoXml) {
        Map<String, Map<String, ClassCoverage>> packages = new LinkedHashMap<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();

        try (InputStream inputStream = Files.newInputStream(jacocoXml)) {
            XMLEventReader reader = factory.createXMLEventReader(inputStream);
            String currentPackage = null;
            String currentClass = null;
            LineCoverage currentLineCoverage = null;

            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                if (event.isStartElement()) {
                    StartElement startElement = event.asStartElement();
                    String name = startElement.getName().getLocalPart();
                    if ("package".equals(name)) {
                        currentPackage = attributeValue(startElement, "name");
                    } else if ("class".equals(name)) {
                        currentClass = attributeValue(startElement, "name");
                        currentLineCoverage = null;
                    } else if ("counter".equals(name) && currentClass != null) {
                        String type = attributeValue(startElement, "type");
                        if ("LINE".equals(type)) {
                            int missed = parseNonNegativeInt(attributeValue(startElement, "missed"), "missed");
                            int covered = parseNonNegativeInt(attributeValue(startElement, "covered"), "covered");
                            currentLineCoverage = new LineCoverage(missed, covered);
                        }
                    }
                } else if (event.isEndElement()) {
                    String name = event.asEndElement().getName().getLocalPart();
                    if ("class".equals(name)) {
                        if (currentClass != null && currentLineCoverage != null && currentPackage != null) {
                            String packageName = currentPackage.replace('/', '.');
                            String fqcn = packageName.isEmpty() ? currentClass : packageName + "." + currentClass;
                            ClassCoverage coverage = new ClassCoverage(fqcn, currentLineCoverage);
                            packages
                                .computeIfAbsent(packageName, key -> new LinkedHashMap<>())
                                .put(fqcn, coverage);
                        }
                        currentClass = null;
                        currentLineCoverage = null;
                    } else if ("package".equals(name)) {
                        currentPackage = null;
                    }
                }
            }
        } catch (IOException | XMLStreamException ex) {
            throw new IllegalStateException("Failed to read JaCoCo XML coverage", ex);
        }

        Map<String, PackageCoverage> packageCoverages = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, ClassCoverage>> entry : packages.entrySet()) {
            packageCoverages.put(entry.getKey(), new PackageCoverage(entry.getKey(), Map.copyOf(entry.getValue())));
        }
        return new CoverageSnapshot(Map.copyOf(packageCoverages));
    }

    private static String attributeValue(StartElement element, String name) {
        var attribute = element.getAttributeByName(new javax.xml.namespace.QName(name));
        return attribute == null ? null : attribute.getValue();
    }

    private static int parseNonNegativeInt(String value, String name) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return parsed;
    }
}
