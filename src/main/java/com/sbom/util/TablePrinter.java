package com.sbom.util;

import java.io.PrintStream;
import java.util.List;

/** Prints rows as a left-aligned, column-padded text table. Null cells print as "-". */
public final class TablePrinter {

    private TablePrinter() {
    }

    public static void print(List<String> header, List<List<String>> rows) {
        print(System.out, header, rows);
    }

    public static void print(PrintStream out, List<String> header, List<List<String>> rows) {
        int[] widths = new int[header.size()];
        for (int i = 0; i < header.size(); i++) {
            widths[i] = header.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], cell(row, i).length());
            }
        }
        printRow(out, header, widths);
        for (List<String> row : rows) {
            printRow(out, row, widths);
        }
    }

    private static void printRow(PrintStream out, List<String> row, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            String value = cell(row, i);
            // pad every column except the last, so lines have no trailing spaces
            line.append(i == row.size() - 1 ? value : String.format("%-" + widths[i] + "s  ", value));
        }
        out.println(line);
    }

    private static String cell(List<String> row, int i) {
        String value = row.get(i);
        return value == null ? "-" : value;
    }
}
