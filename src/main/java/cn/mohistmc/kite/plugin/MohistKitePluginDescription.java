package cn.mohistmc.kite.plugin;

import java.util.Collections;
import java.util.List;

public final class MohistKitePluginDescription {
    @SuppressWarnings({"unused", "MismatchedQueryAndUpdateOfCollection"})
    private List<String> mixins;

    public List<String> getMixins() {
        return mixins == null ? Collections.emptyList() : Collections.unmodifiableList(mixins);
    }
}
