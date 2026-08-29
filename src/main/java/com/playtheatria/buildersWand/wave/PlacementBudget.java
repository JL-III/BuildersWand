package com.playtheatria.buildersWand.wave;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure budget simulation shared by preview and commit. Consumed feedstock needs one item per cell;
 * a reusable catalyst needs one retained source item for the plan. Per-cell decisions explain
 * shortages. Callers use those decisions to color unavailable preview cells, but placement is
 * admitted only when {@link Result#fullyAffordable()} is true.
 */
public final class PlacementBudget {

    public record Cost(Material sourceItem, boolean consumesFeedstock, int uses) {
        public Cost {
            Objects.requireNonNull(sourceItem, "sourceItem");
            if (uses < 1) {
                throw new IllegalArgumentException("uses must be positive");
            }
        }
    }

    public record Decision(boolean affordable, boolean materialAffordable, boolean usesAffordable) {
    }

    public record Result(List<Decision> decisions, int affordableCells, long affordableUses,
                         long requiredUses, Map<Material, Integer> requiredMaterials,
                         Map<Material, Integer> missingMaterials) {
        public Result {
            decisions = List.copyOf(decisions);
            requiredMaterials = Collections.unmodifiableMap(new LinkedHashMap<>(requiredMaterials));
            missingMaterials = Collections.unmodifiableMap(new LinkedHashMap<>(missingMaterials));
        }

        /** True only when every requested cell passed both resource checks. */
        public boolean fullyAffordable() {
            return affordableCells == decisions.size();
        }

    }

    private PlacementBudget() {
    }

    public static Result evaluate(List<Cost> costs, Map<Material, Integer> availableMaterials,
                                  int remainingUses, boolean materialsBypassed,
                                  boolean usesBypassed) {
        Objects.requireNonNull(costs, "costs");
        Objects.requireNonNull(availableMaterials, "availableMaterials");
        if (remainingUses < 0) {
            throw new IllegalArgumentException("remainingUses cannot be negative");
        }

        Map<Material, Integer> available = new HashMap<>();
        availableMaterials.forEach((material, count) -> available.put(material, Math.max(0, count)));
        Map<Material, Integer> required = new LinkedHashMap<>();
        Map<Material, Integer> requiredConsumables = new LinkedHashMap<>();
        Map<Material, Integer> requiredCatalysts = new LinkedHashMap<>();
        List<Decision> decisions = new ArrayList<>(costs.size());
        int useBalance = remainingUses;
        int affordableCells = 0;
        long affordableUses = 0L;
        long requiredUses = 0L;

        for (Cost cost : costs) {
            Objects.requireNonNull(cost, "cost");
            requiredUses = Math.addExact(requiredUses, cost.uses());
            Map<Material, Integer> requirement = cost.consumesFeedstock()
                    ? requiredConsumables
                    : requiredCatalysts;
            requirement.merge(cost.sourceItem(), 1, cost.consumesFeedstock()
                    ? Math::addExact
                    : Math::max);
            required.merge(cost.sourceItem(), 1, cost.consumesFeedstock()
                    ? Math::addExact
                    : Math::max);

            int sourceItems = available.getOrDefault(cost.sourceItem(), 0);
            // Creative bypasses consumed blocks, not a reusable catalyst such as a water bucket.
            boolean materialAffordable = cost.consumesFeedstock()
                    ? materialsBypassed || sourceItems > 0
                    : sourceItems > 0;
            boolean usesAffordable = usesBypassed || useBalance >= cost.uses();
            boolean affordable = materialAffordable && usesAffordable;
            decisions.add(new Decision(affordable, materialAffordable, usesAffordable));
            if (!affordable) {
                continue;
            }
            affordableCells++;
            affordableUses = Math.addExact(affordableUses, cost.uses());
            if (!materialsBypassed && cost.consumesFeedstock()) {
                available.compute(cost.sourceItem(), (ignored, count) -> count == null ? 0 : count - 1);
            }
            if (!usesBypassed) {
                useBalance -= cost.uses();
            }
        }

        Map<Material, Integer> missing = new LinkedHashMap<>();
        if (!materialsBypassed) {
            requiredConsumables.forEach((material, count) -> {
                int shortage = count - Math.max(0, availableMaterials.getOrDefault(material, 0));
                if (shortage > 0) {
                    missing.put(material, shortage);
                }
            });
        }
        requiredCatalysts.forEach((material, count) -> {
            int shortage = count - Math.max(0, availableMaterials.getOrDefault(material, 0));
            if (shortage > 0) {
                missing.merge(material, shortage, Math::max);
            }
        });
        return new Result(decisions, affordableCells, affordableUses, requiredUses, required, missing);
    }
}
