package org.cubexmc.metro.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Java interop shim for Metro's GUI holders.
 *
 * <p>Bukkit annotates {@link InventoryHolder#getInventory()} as {@code @NotNull}, but a Metro
 * holder is constructed before its inventory exists and must keep reporting {@code null} until
 * the view fills it in. Kotlin cannot override a {@code @NotNull} method with a nullable return,
 * so the nullable declaration lives in this base class.
 */
public abstract class NullableInventoryHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return currentInventory();
    }

    /**
     * @return the backing inventory, or {@code null} before it has been created
     */
    protected abstract Inventory currentInventory();
}
