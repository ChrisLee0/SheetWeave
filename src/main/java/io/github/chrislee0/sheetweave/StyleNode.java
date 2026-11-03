package io.github.chrislee0.sheetweave;

import java.util.*;

/** Logical element used for selector matching; independent of Excel coordinates. */
final class StyleNode {
    final String tag;
    final String id;
    final Set<String> classes;
    final StyleNode parent;
    StyleNode(String tag, String id, String classes, StyleNode parent) {
        this.tag = tag;
        this.id = id;
        this.classes = new HashSet<String>(Arrays.asList(classes.trim().split("\\s+")));
        this.parent = parent;
    }
}
