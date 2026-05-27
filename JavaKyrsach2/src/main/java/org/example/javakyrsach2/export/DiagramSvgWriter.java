package org.example.javakyrsach2.export;

import org.example.javakyrsach2.model.RelationType;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiagramSvgWriter {
    private static final double ARROW_LENGTH = 14;
    private static final double ARROW_WIDTH = 9;

    private DiagramSvgWriter() {
    }

    public static void write(DiagramRenderSnapshot snapshot, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(snapshot, writer);
        }
    }

    public static void write(DiagramRenderSnapshot snapshot, Writer writer) throws IOException {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        writer.write("width=\"" + format(snapshot.width()) + "\" ");
        writer.write("height=\"" + format(snapshot.height()) + "\" ");
        writer.write("viewBox=\"0 0 " + format(snapshot.width()) + " " + format(snapshot.height()) + "\">\n");
        writer.write("<rect width=\"100%\" height=\"100%\" fill=\"#ececec\"/>\n");

        for (DiagramRenderSnapshot.ClassBlock block : snapshot.classes()) {
            writeClassBlock(writer, block);
        }

        for (DiagramRenderSnapshot.EdgeSegment edge : snapshot.edges()) {
            writeEdge(writer, edge);
        }

        if (snapshot.legend() != null) {
            writeLegend(writer, snapshot.legend());
        }

        writer.write("</svg>\n");
    }

    private static void writeClassBlock(Writer writer, DiagramRenderSnapshot.ClassBlock block) throws IOException {
        String fill = block.interfaceType() ? "#e8f4fc" : "#fafafa";
        String stroke = block.interfaceType() ? "#2c6e9e" : "#333333";
        writer.write("<rect x=\"" + format(block.x()) + "\" y=\"" + format(block.y()) + "\" ");
        writer.write("width=\"" + format(block.width()) + "\" height=\"" + format(block.height()) + "\" ");
        writer.write("fill=\"" + fill + "\" stroke=\"" + stroke + "\" stroke-width=\"1.5\"/>\n");

        double textX = block.x() + 8;
        double textY = block.y() + 18;
        for (int i = 0; i < block.lines().size(); i++) {
            String line = block.lines().get(i);
            boolean title = i == 0;
            boolean section = line.startsWith("—");
            boolean packageLine = !title && !section && line.contains(".") && !line.contains("(");

            writer.write("<text x=\"" + format(textX) + "\" y=\"" + format(textY) + "\" ");
            writer.write("font-family=\"" + (section || title || packageLine
                    ? "Segoe UI, Arial, sans-serif"
                    : "Consolas, monospace") + "\" ");
            writer.write("font-size=\"" + (title ? "12" : section ? "10" : "11") + "\" ");
            if (title) {
                writer.write("font-weight=\"bold\" ");
                if (block.interfaceType()) {
                    writer.write("font-style=\"italic\" ");
                }
                writer.write("fill=\"#1a1a1a\" ");
            } else if (section) {
                writer.write("font-weight=\"bold\" fill=\"#888888\" ");
            } else if (packageLine) {
                writer.write("fill=\"#666666\" ");
            } else {
                writer.write("fill=\"#1a1a1a\" ");
            }
            writer.write(">");
            writer.write(escapeXml(line));
            writer.write("</text>\n");
            textY += section ? 14 : 15;
        }
    }

    private static void writeEdge(Writer writer, DiagramRenderSnapshot.EdgeSegment edge) throws IOException {
        String color = colorFor(edge.relationType());
        writer.write("<line x1=\"" + format(edge.startX()) + "\" y1=\"" + format(edge.startY()) + "\" ");
        writer.write("x2=\"" + format(edge.endX()) + "\" y2=\"" + format(edge.endY()) + "\" ");
        writer.write("stroke=\"" + color + "\" stroke-width=\"2\" stroke-linecap=\"round\"");
        if (edge.relationType() == RelationType.IMPLEMENTATION) {
            writer.write(" stroke-dasharray=\"10 6\"");
        } else if (edge.relationType() == RelationType.ASSOCIATION) {
            writer.write(" stroke-dasharray=\"6 4\"");
        } else if (edge.relationType() == RelationType.DEPENDENCY) {
            writer.write(" stroke-dasharray=\"3 5\"");
        }
        writer.write("/>\n");

        writeArrowHead(writer, edge, color);
        writeEdgeLabel(writer, edge, color);
    }

    private static void writeArrowHead(Writer writer, DiagramRenderSnapshot.EdgeSegment edge, String color)
            throws IOException {
        double dx = edge.endX() - edge.startX();
        double dy = edge.endY() - edge.startY();
        double length = Math.hypot(dx, dy);
        if (length < 0.001) {
            return;
        }
        double ux = dx / length;
        double uy = dy / length;
        double tipX = edge.endX();
        double tipY = edge.endY();
        double baseX = tipX - ux * ARROW_LENGTH;
        double baseY = tipY - uy * ARROW_LENGTH;
        double leftX = baseX + (-uy) * (ARROW_WIDTH / 2.0);
        double leftY = baseY + ux * (ARROW_WIDTH / 2.0);
        double rightX = baseX + uy * (ARROW_WIDTH / 2.0);
        double rightY = baseY - ux * (ARROW_WIDTH / 2.0);

        String fill = edge.relationType() == RelationType.ASSOCIATION ? color : "#ffffff";
        writer.write("<polygon points=\"");
        writer.write(format(tipX) + "," + format(tipY) + " ");
        writer.write(format(leftX) + "," + format(leftY) + " ");
        writer.write(format(rightX) + "," + format(rightY) + "\" ");
        writer.write("fill=\"" + fill + "\" stroke=\"" + color + "\" stroke-width=\"1.5\"/>\n");
    }

    private static void writeEdgeLabel(Writer writer, DiagramRenderSnapshot.EdgeSegment edge, String color)
            throws IOException {
        double width = Math.max(48, edge.label().length() * 7.0);
        double height = 18;
        double x = edge.labelX() - width / 2.0;
        double y = edge.labelY() - height / 2.0;
        writer.write("<rect x=\"" + format(x) + "\" y=\"" + format(y) + "\" ");
        writer.write("width=\"" + format(width) + "\" height=\"" + format(height) + "\" ");
        writer.write("fill=\"rgba(255,255,255,0.92)\" stroke=\"" + color + "\" rx=\"4\"/>\n");
        writer.write("<text x=\"" + format(edge.labelX()) + "\" y=\"" + format(edge.labelY() + 4) + "\" ");
        writer.write("text-anchor=\"middle\" font-family=\"Segoe UI, Arial, sans-serif\" ");
        writer.write("font-size=\"10\" font-weight=\"bold\" fill=\"" + color + "\">");
        writer.write(escapeXml(edge.label()));
        writer.write("</text>\n");
    }

    private static void writeLegend(Writer writer, DiagramRenderSnapshot.Legend legend) throws IOException {
        writer.write("<rect x=\"" + format(legend.x()) + "\" y=\"" + format(legend.y()) + "\" ");
        writer.write("width=\"" + format(legend.width()) + "\" height=\"" + format(legend.height()) + "\" ");
        writer.write("fill=\"rgba(255,255,255,0.95)\" stroke=\"#cccccc\" stroke-width=\"1\" rx=\"6\"/>\n");

        double textY = legend.y() + 18;
        for (int i = 0; i < legend.lines().size(); i++) {
            String line = legend.lines().get(i);
            writer.write("<text x=\"" + format(legend.x() + 10) + "\" y=\"" + format(textY) + "\" ");
            writer.write("font-family=\"Segoe UI, Arial, sans-serif\" ");
            if (i == 0) {
                writer.write("font-size=\"12\" font-weight=\"bold\" ");
            } else {
                writer.write("font-size=\"10\" ");
            }
            writer.write("fill=\"#333333\">");
            writer.write(escapeXml(line));
            writer.write("</text>\n");
            textY += i == 0 ? 20 : 16;
        }
    }

    private static String colorFor(RelationType relationType) {
        return switch (relationType) {
            case INHERITANCE -> "#1a1a1a";
            case IMPLEMENTATION -> "#1b7a3d";
            case ASSOCIATION -> "#2c4f9e";
            case DEPENDENCY -> "#8a3db0";
        };
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static String escapeXml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
