package net.weyne1.randomcrafts.core.recipe;

import net.weyne1.randomcrafts.core.item.CoreItem;

import java.util.List;

public record CoreRecipe(
        String id,
        CoreItem output,
        List<CoreItem> inputs,
        int outputCount
) {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(output.name())
                .append(" x")
                .append(outputCount)
                .append(" <- ");

        for (CoreItem item : inputs) {
            sb.append(item.name()).append(", ");
        }
        if (!inputs.isEmpty()) sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
