package thito.fancywaystones.ui;

import com.cryptomorin.xseries.SkullUtils;
import com.cryptomorin.xseries.XMaterial;
import dev.lone.itemsadder.api.CustomStack;
import io.lumine.xikage.mythicmobs.MythicMobs;
import io.th0rgal.oraxen.items.OraxenItems;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import thito.fancywaystones.*;
import thito.fancywaystones.config.ListSection;
import thito.fancywaystones.config.MapSection;
import thito.fancywaystones.config.Section;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class MinecraftItem {
    private static Set<MinecraftItem> activeItems = ConcurrentHashMap.newKeySet();

    public static Set<MinecraftItem> getActiveItems() {
        return activeItems;
    }

    private String plugin = null;
    private String type = "AIR";
    private String amount = "1";
    private String damage;
    private String displayName;
    private String customModelData;
    private String skull;
    private List<String> lore;
    private List<String> flags;
    private Map<String, String> enchantments = new HashMap<>();
    private Map<String, String> storedEnchantments = new HashMap<>();
    private Map<String, byte[]> extraData = new HashMap<>();
    private Map<Consumer<Function<Placeholder, ItemStack>>, Boolean> updateListener = new WeakHashMap<>();
    private Map<BiConsumer<InventoryClickEvent, Placeholder>, Boolean> clickListener = new HashMap<>();
    private Placeholder placeholder = new Placeholder();
    private String environment, model; // for FancyWaystones

    public MinecraftItem clone() {
        MinecraftItem item = new MinecraftItem();
        item.plugin = plugin;
        item.type = type;
        item.amount = amount;
        item.damage = damage;
        item.displayName = displayName;
        item.lore = lore == null ? null : new ArrayList<>(lore);
        item.flags = flags == null ? null : new ArrayList<>(flags);
        item.enchantments.putAll(enchantments);
        item.storedEnchantments.putAll(storedEnchantments);
        item.customModelData = customModelData;
        item.skull = skull;
        return item;
    }

    public void setSkull(String skull) {
        this.skull = skull;
    }

    public void clear() {
        plugin = null;
        placeholder = new Placeholder();
        clickListener.clear();
        extraData.clear();
        storedEnchantments.clear();
        enchantments.clear();
        customModelData = null;
        flags = null;
        lore = null;
        displayName = null;
        damage = null;
        amount = "1";
        type = "AIR";
        skull = null;
        environment = null;
        model = null;
    }

    public Placeholder getPlaceholder() {
        return placeholder;
    }

    public void addClickListener(BiConsumer<InventoryClickEvent, Placeholder> listener) {
        clickListener.put(listener, Boolean.TRUE);
    }

    public void removeClickListener(Object listener) {
        clickListener.remove(listener);
    }

    protected void dispatchClick(InventoryClickEvent event, Placeholder placeholder) {
        new HashMap<>(clickListener).forEach((cons, dummy) -> {
            cons.accept(event, placeholder);
        });
    }

    public Set<String> getDataKeys() {
        return extraData.keySet();
    }

    public byte[] getData(String key) {
        return extraData.get(key);
    }

    public void setData(String key, byte[] data) {
        extraData.put(key, data);
    }

    public boolean hasData(String key) {
        return extraData.containsKey(key);
    }

    public void addEnchantment(Object enchantment, Object level) {
        enchantments.put(String.valueOf(enchantment), String.valueOf(level));
        update();
    }

    public void addStoredEnchantment(Object enchantment, Object level) {
        storedEnchantments.put(String.valueOf(enchantment), String.valueOf(level));
        update();
    }

    public void removeEnchantment(Object enchantment) {
        enchantments.remove(String.valueOf(enchantment));
        update();
    }

    public void removeStoredEnchantment(Object enchantment) {
        storedEnchantments.remove(String.valueOf(enchantment));
        update();
    }

    public void clearEnchantments() {
        enchantments.clear();
        update();
    }

    public void clearStoredEnchantments() {
        storedEnchantments.clear();
        update();
    }

    public void setPlugin(Object plugin) {
        this.plugin = plugin == null ? null : String.valueOf(plugin);
        update();
    }

    public MinecraftItem setType(Object type) {
        if (type == null) type = "AIR";
        if (type instanceof XMaterial) type = ((XMaterial) type).name();
        this.type = String.valueOf(type);
        update();
        return this;
    }

    public void setAmount(Object amount) {
        if (amount == null) amount = 0;
        this.amount = String.valueOf(amount);
        update();
    }

    public void setDamage(Object damage) {
        if (damage == null) damage = 0;
        this.damage = String.valueOf(damage);
        update();
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<?> lore) {
        if (lore == null) lore = Collections.emptyList();
        this.lore = lore.stream().map(String::valueOf).collect(Collectors.toList());
        update();
    }

    public void setFlags(List<?> flags) {
        if (flags == null) flags = Collections.emptyList();
        this.flags = flags.stream().map(String::valueOf).collect(Collectors.toList());
        update();
    }

    public MinecraftItem load(Section section) {
        if (section == null) return this;
        plugin = section.getString("Plugin").orElse(null);
        type = section.getString("Type").orElse("AIR");
        amount = section.getString("Amount").orElse("1");
        damage = section.getString("Damage").orElse(null);
        displayName = section.getString("Display Name").orElse(null);
        lore = section.getList("Lore").orElse(ListSection.empty()).stream().map(String::valueOf).collect(Collectors.toList());
        flags = section.getList("Flags").orElse(ListSection.empty()).stream().map(String::valueOf).collect(Collectors.toList());
        enchantments = enchantmentMap(section.getMap("Enchantments").orElse(MapSection.empty()));
        storedEnchantments = enchantmentMap(section.getMap("Stored Enchantments").orElse(MapSection.empty()));
        customModelData = section.getString("Custom Model Data").orElse(null);
        skull = section.getString("Skull").orElse(null);
        environment = section.getString("Environment").orElse(null);
        model = section.getString("Model").orElse(null);
        return this;
    }

    public MinecraftItem load(ConfigurationSection section) {
        if (section == null) return this;
        plugin = section.getString("Plugin");
        type = section.getString("Type", "AIR");
        amount = section.getString("Amount", "1");
        damage = section.getString("Damage");
        flags = section.getStringList("Flags");
        displayName = section.getString("Display Name");
        lore = section.getStringList("Lore");
        enchantments = enchantmentMap(section.getConfigurationSection("Enchantments"));
        storedEnchantments = enchantmentMap(section.getConfigurationSection("Stored Enchantments"));
        customModelData = section.getString("Custom Model Data");
        skull = section.getString("Skull");
        environment = section.getString("Environment");
        model = section.getString("Model");
        loadDisplay(section);
        return this;
    }

    public MinecraftItem loadDisplay(ConfigurationSection section) {
        displayName = section.getString("Display Name");
        lore = section.getStringList("Lore");
        return this;
    }

    public void load(ItemStack itemStack) {
        type = itemStack.getType().name();
        amount = String.valueOf(itemStack.getAmount());
        ItemMeta meta = itemStack.getItemMeta();
        try {
            if (meta instanceof Damageable) {
                damage = String.valueOf(((Damageable) meta).getDamage());
            }
        } catch (Throwable t) {
            try {
                damage = String.valueOf(itemStack.getDurability());
            } catch (Throwable ignored) {
            }
        }
        if (meta != null) {
            displayName = meta.getDisplayName();
            lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            enchantments = new HashMap<>();
            try {
                if (meta.hasCustomModelData()) {
                    customModelData = meta.getCustomModelData() + "";
                }
            } catch (Throwable ignored) {
            }
            meta.getEnchants().forEach((ench, lvl) -> {
                enchantments.put(ench.getName(), String.valueOf(lvl));
            });
        }
        storedEnchantments = new HashMap<>();
        if (meta instanceof EnchantmentStorageMeta) {
            ((EnchantmentStorageMeta) meta).getStoredEnchants().forEach((ench, lvl) -> {
                storedEnchantments.put(ench.getName(), lvl.toString());
            });
        }
        Map<String, byte[]> dataMap = NBTUtil.getDataMap(itemStack);
        extraData.clear();
        if (dataMap != null) {
            extraData.putAll(dataMap);
        }
    }

    static Map<String, String> enchantmentMap(Section section) {
        Map<String, String> map = new HashMap<>();
        if (section != null) {
            for (String key : ((MapSection) section).keySet()) {
                map.put(key, section.getString(key).orElse(null));
            }
        }
        return map;
    }

    static Map<String, String> enchantmentMap(ConfigurationSection sec) {
        Map<String, String> map = new HashMap<>();
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                map.put(key, sec.getString(key));
            }
        }
        return map;
    }

    private void check() {
        if (updateListener.isEmpty()) {
            activeItems.remove(this);
        } else {
            activeItems.add(this);
        }
    }

    public void addUpdateListener(Consumer<Function<Placeholder, ItemStack>> itemStackConsumer) {
        updateListener.put(itemStackConsumer, Boolean.TRUE);
        check();
    }

    public void removeUpdateListener(Object itemStackConsumer) {
        updateListener.remove(itemStackConsumer);
        check();
    }

    public ItemStack getItemStack(Placeholder placeholder) {
        Placeholder combined = new Placeholder();
        combined.combine(placeholder);
        combined.combine(getPlaceholder());
        placeholder = combined;
        ItemStack item;
        String plugin = this.plugin;
        if (plugin != null) plugin = placeholder.replace(plugin);
        if ("Oraxen".equals(plugin)) {
            item = OraxenItems.getItemById(placeholder.replace(type)).build();
        } else if ("ItemsAdder".equals(plugin)) {
            item = CustomStack.getInstance(placeholder.replace(type)).getItemStack();
        } else if ("MythicMobs".equals(plugin)) {
            item = MythicMobs.inst().getItemManager().getItemStack(type);
        } else if ("FancyWaystones".equals(plugin)) {
            WaystoneData waystoneData = WaystoneManager.getManager().createData(
                    WaystoneManager.getManager().getType(type),
                    World.Environment.valueOf(environment),
                    WaystoneManager.getManager().getModelMap().get(model));
            item = WaystoneManager.getManager().createWaystoneItem(waystoneData, true);
        } else {
            item = Util.material(placeholder.replace(type)).parseItem();
        }
        if (item == null) {
            FancyWaystones.getPlugin().getLogger().log(Level.SEVERE, "Cannot find "+placeholder.replace(type)+" type!");
            item = XMaterial.AIR.parseItem();
            return item;
        }
        item.setAmount(Integer.parseInt(placeholder.replace(amount)));
        ItemMeta meta = item.getItemMeta();
        Placeholder finalPlaceholder = placeholder;
        if (meta != null) {
            enchantments.forEach((ench, level) -> {
                Enchantment enchantment = Enchantment.getByName(finalPlaceholder.replace(ench));
                if (enchantment != null) {
                    try {
                        meta.addEnchant(enchantment, Integer.parseInt(finalPlaceholder.replace(level)), true);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
        if (meta instanceof EnchantmentStorageMeta) {
            storedEnchantments.forEach((ench, level) -> {
                Enchantment enchantment = Enchantment.getByName(finalPlaceholder.replace(ench));
                if (enchantment != null) {
                    try {
                        ((EnchantmentStorageMeta) meta).addStoredEnchant(enchantment, Integer.parseInt(finalPlaceholder.replace(level)), true);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
        if (damage != null) {
            try {
                if (meta instanceof Damageable) {
                    ((Damageable) meta).setDamage(Integer.parseInt(placeholder.replace(damage)));
                }
            } catch (Throwable t) {
                try {
                    item.setDurability(Short.parseShort(placeholder.replace(damage)));
                } catch (Throwable t1) {
                }
            }
        }
        if (meta != null) {
            if (customModelData != null) {
                try {
                    meta.setCustomModelData(Integer.parseInt(placeholder.replace(customModelData)));
                } catch (Throwable ignored) {
                }
            }
            if (displayName != null) {
                meta.setDisplayName(placeholder.replace(displayName));
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(placeholder.replaceWithBreakableLines(lore));
            }
            if (flags != null) {
                meta.addItemFlags(flags.stream().map(ItemFlag::valueOf).toArray(ItemFlag[]::new));
            }
            if (meta instanceof SkullMeta && skull != null) {
                SkullUtils.applySkin(meta, skull);
            }
            item.setItemMeta(meta);
        }
        ItemStack finalItem = item;
        extraData.forEach((key, data) -> {
            NBTUtil.setData(finalItem, key, data);
        });
        return item;
    }

    public void update() {
        check();
        Map<Placeholder, ItemStack> cached = new HashMap<>();
        updateListener.forEach((cons, dummy) -> {
            cons.accept(placeholder -> cached.computeIfAbsent(placeholder, this::getItemStack));
        });
    }
}
