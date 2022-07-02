package cn.mohistmc.kite;

import java.util.ArrayList;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MohistKiteLogger extends Logger {
    public static ConsoleHandler LOGGER_HANDLER = new ConsoleHandler();
    private static ArrayList<MohistKiteLogger> instances = new ArrayList<>();

    static {
        LOGGER_HANDLER.setFormatter(new Formatter() {
            @SuppressWarnings("deprecation")
            @Override
            public String format(LogRecord record) {
                Date date = new Date(record.getMillis());
                return String.format("[%02d:%02d:%02d %s]: [%s] %s%n", date.getHours(), date.getMinutes(),
                        date.getSeconds(), record.getLevel().getName(), record.getLoggerName(), record.getMessage());
            }
        });
    }

    {
        if (instances != null) {
            setUseParentHandlers(false);
            addHandler(LOGGER_HANDLER);
            instances.add(this);
        }
    }

    private MohistKiteLogger(@Nullable String name) {
        super(name, null);
    }

    @NotNull
    public static Logger getLogger(@Nullable String name) {
        if (name == null) name = "";
        name = "MohistKite" + (name.isEmpty() ? "" : ":" + name);
        Logger logger = new MohistKiteLogger(name);
        if (!LogManager.getLogManager().addLogger(logger)) {
            logger = LogManager.getLogManager().getLogger(name);
        }
        return logger;
    }

    protected static void restore() {
        instances.forEach(it -> {
            it.removeHandler(LOGGER_HANDLER);
            it.setUseParentHandlers(true);
        });
        instances = null;
    }

    public void setParent(@NotNull Logger parent) {
        if (this.getParent() != null) {
            this.warning("Ignoring attempt to change parent of plugin logger");
        } else {
            this.log(Level.FINE, "Setting plugin logger parent to {0}", parent);
            super.setParent(parent);
        }
    }
}
