package com.playtheatria.buildersWand.prefab;

import java.util.List;

/** Checked validation failure containing administrator-facing diagnostics. */
public final class PrefabValidationException extends Exception {

    private final List<String> issues;

    public PrefabValidationException(String issue) {
        this(List.of(issue));
    }

    public PrefabValidationException(List<String> issues) {
        super(String.join("; ", issues));
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("At least one validation issue is required");
        }
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
