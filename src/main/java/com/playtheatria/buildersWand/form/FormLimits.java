package com.playtheatria.buildersWand.form;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable limit inputs for geometry and final-plan admission. The builder is deliberately free
 * of Bukkit configuration types so startup code can validate configuration once and pass the same
 * policy to measurement, preview, and commit.
 */
public final class FormLimits {

    /** Maximum cells one invocation may actually change. */
    public static final int DEFAULT_MAX_CELLS = 1_024;
    /** Maximum candidate cells one live plan may inspect before world-state filtering. */
    public static final int DEFAULT_MAX_SCANNED_CELLS = 4_096;
    public static final int DEFAULT_MAX_CHUNKS = 9;
    public static final int MAX_CONFIGURED_CELLS = 32_768;
    public static final int MAX_CONFIGURED_SCANNED_CELLS = 1_000_000;
    public static final int MAX_CONFIGURED_CHUNKS = 256;
    public static final int MAX_CONFIGURED_SPAN = 4_096;
    public static final long MAX_GEOMETRY_WORK_VOLUME = 1_000_000L;
    public static final FormLimits DEFAULTS = builder().build();

    private final int maxCellsPerPrint;
    private final int maxScannedCellsPerPlan;
    private final int maxChunksPerPrint;
    private final Map<Form, Dims> maxSpans;

    private FormLimits(Builder builder) {
        if (builder.maxCellsPerPrint <= 0) {
            throw new IllegalArgumentException("max cells per print must be positive");
        }
        if (builder.maxChunksPerPrint <= 0) {
            throw new IllegalArgumentException("max chunks per print must be positive");
        }
        if (builder.maxScannedCellsPerPlan <= 0) {
            throw new IllegalArgumentException("max scanned cells per plan must be positive");
        }
        if (builder.maxCellsPerPrint > MAX_CONFIGURED_CELLS) {
            throw new IllegalArgumentException("max cells per print cannot exceed "
                    + MAX_CONFIGURED_CELLS);
        }
        if (builder.maxChunksPerPrint > MAX_CONFIGURED_CHUNKS) {
            throw new IllegalArgumentException("max chunks per print cannot exceed "
                    + MAX_CONFIGURED_CHUNKS);
        }
        if (builder.maxScannedCellsPerPlan > MAX_CONFIGURED_SCANNED_CELLS) {
            throw new IllegalArgumentException("max scanned cells per plan cannot exceed "
                    + MAX_CONFIGURED_SCANNED_CELLS);
        }
        if (builder.maxScannedCellsPerPlan < builder.maxCellsPerPrint) {
            throw new IllegalArgumentException(
                    "max scanned cells per plan cannot be below max cells per print");
        }
        EnumMap<Form, Dims> checked = new EnumMap<>(Form.class);
        for (Form form : Form.values()) {
            Dims spans = builder.maxSpans.get(form);
            if (spans == null) {
                throw new IllegalArgumentException("missing span limits for " + form.key());
            }
            if (spans.primary() <= 0 || spans.secondary() <= 0 || spans.tertiary() <= 0) {
                throw new IllegalArgumentException(form.key() + " span limits must be positive");
            }
            if (spans.primary() > MAX_CONFIGURED_SPAN
                    || spans.secondary() > MAX_CONFIGURED_SPAN
                    || spans.tertiary() > MAX_CONFIGURED_SPAN) {
                throw new IllegalArgumentException(form.key() + " span cannot exceed "
                        + MAX_CONFIGURED_SPAN);
            }
            long workVolume = estimatedWorkVolume(form, spans);
            if (workVolume > MAX_GEOMETRY_WORK_VOLUME) {
                throw new IllegalArgumentException(form.key() + " configured bounds require up to "
                        + workVolume + " geometry cells; safe max " + MAX_GEOMETRY_WORK_VOLUME);
            }
            checked.put(form, spans);
        }
        this.maxCellsPerPrint = builder.maxCellsPerPrint;
        this.maxScannedCellsPerPlan = builder.maxScannedCellsPerPlan;
        this.maxChunksPerPrint = builder.maxChunksPerPrint;
        this.maxSpans = Collections.unmodifiableMap(checked);
    }

    private static long estimatedWorkVolume(Form form, Dims spans) {
        long p = spans.primary();
        long s = spans.secondary();
        long t = spans.tertiary();
        return switch (form) {
            case LINE -> p;
            case DIAGONAL, WALL, FLOOR, EXTEND_SURFACE -> Math.multiplyExact(p, s);
            case BOX -> Math.multiplyExact(Math.multiplyExact(p, s), t);
            case CYLINDER -> {
                long diameter = Math.subtractExact(Math.multiplyExact(2L, p), 1L);
                yield Math.multiplyExact(Math.multiplyExact(diameter, diameter), s);
            }
            case SPHERE -> {
                long radius = p - 1L;
                long diameter = Math.addExact(Math.multiplyExact(2L, radius), 1L);
                long length = Math.addExact(s, Math.multiplyExact(2L, radius));
                yield Math.multiplyExact(Math.multiplyExact(diameter, diameter), length);
            }
        };
    }

    public int maxCellsPerPrint() {
        return maxCellsPerPrint;
    }

    public int maxScannedCellsPerPlan() {
        return maxScannedCellsPerPlan;
    }

    public int maxChunksPerPrint() {
        return maxChunksPerPrint;
    }

    public Dims maxSpans(Form form) {
        return maxSpans.get(Objects.requireNonNull(form, "form"));
    }

    public int maxSpan(Form form, int axisIndex) {
        Dims spans = maxSpans(form);
        return switch (axisIndex) {
            case 0 -> spans.primary();
            case 1 -> spans.secondary();
            case 2 -> spans.tertiary();
            default -> throw new IllegalArgumentException("axis index must be 0, 1, or 2");
        };
    }

    public Map<Form, Dims> allMaxSpans() {
        return maxSpans;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.maxCellsPerPrint = maxCellsPerPrint;
        builder.maxScannedCellsPerPlan = maxScannedCellsPerPlan;
        builder.maxChunksPerPrint = maxChunksPerPrint;
        builder.maxSpans.clear();
        builder.maxSpans.putAll(maxSpans);
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxCellsPerPrint = DEFAULT_MAX_CELLS;
        private int maxScannedCellsPerPlan = DEFAULT_MAX_SCANNED_CELLS;
        private int maxChunksPerPrint = DEFAULT_MAX_CHUNKS;
        private final EnumMap<Form, Dims> maxSpans = defaultSpans();

        private Builder() {
        }

        public Builder maxCellsPerPrint(int maxCellsPerPrint) {
            this.maxCellsPerPrint = maxCellsPerPrint;
            return this;
        }

        public Builder maxScannedCellsPerPlan(int maxScannedCellsPerPlan) {
            this.maxScannedCellsPerPlan = maxScannedCellsPerPlan;
            return this;
        }

        public Builder maxChunksPerPrint(int maxChunksPerPrint) {
            this.maxChunksPerPrint = maxChunksPerPrint;
            return this;
        }

        public Builder maxSpans(Form form, int primary, int secondary, int tertiary) {
            maxSpans.put(Objects.requireNonNull(form, "form"), new Dims(primary, secondary, tertiary));
            return this;
        }

        public FormLimits build() {
            return new FormLimits(this);
        }

        private static EnumMap<Form, Dims> defaultSpans() {
            EnumMap<Form, Dims> spans = new EnumMap<>(Form.class);
            spans.put(Form.DIAGONAL, new Dims(8, 5, 1));
            spans.put(Form.BOX, new Dims(16, 16, 16));
            spans.put(Form.CYLINDER, new Dims(9, 8, 1));
            spans.put(Form.SPHERE, new Dims(7, 8, 1));
            spans.put(Form.WALL, new Dims(32, 32, 1));
            spans.put(Form.LINE, new Dims(64, 1, 1));
            spans.put(Form.FLOOR, new Dims(32, 32, 1));
            // Traversal supplies these bounding spans; the source need not be rectangular.
            spans.put(Form.EXTEND_SURFACE, new Dims(64, 64, 1));
            return spans;
        }
    }
}
