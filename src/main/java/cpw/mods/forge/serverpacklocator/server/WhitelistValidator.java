package cpw.mods.forge.serverpacklocator.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class WhitelistValidator {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path whitelist;

    WhitelistValidator(final Path gameDir) {
        this.whitelist = gameDir.resolve("whitelist.json");
        Executors.newSingleThreadExecutor(r -> SimpleHttpServer.newDaemonThread("ServerPack Whitelist watcher - ", r)).submit(() -> LamdbaExceptionUtils.uncheck(() -> monitorWhitelist(gameDir)));
    }

    private void monitorWhitelist(final Path gameDir) throws IOException, InterruptedException {
        updateWhiteList();
        final WatchService watchService = gameDir.getFileSystem().newWatchService();
        final WatchKey watchKey = gameDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        for (;;) {
            watchService.take();
            final List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
            watchEvents.stream()
                    .filter(e -> Objects.equals(((Path) e.context()).getFileName().toString(), "whitelist.json"))
                    .findAny()
                    .ifPresent(e -> updateWhiteList());
            watchKey.reset();
        }
    }

    private static Optional<Predicate<String>> validator = Optional.empty();

    static boolean validate(final String commonName) {
        return validator.map(v -> v.test(commonName.toLowerCase(Locale.ROOT))).orElse(false);
    }

    static void setValidator(final Predicate<String> uuidTester) {
        validator = Optional.of(uuidTester);
    }

    private void updateWhiteList() {
        try (BufferedReader br = Files.newBufferedReader(this.whitelist)) {
            LOGGER.debug("Detected whitelist change, reloading");
            JsonArray array = new JsonParser().parse(br).getAsJsonArray();
            final Set<String> uuidSet = StreamSupport.stream(array.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .map(element -> element.getAsJsonPrimitive("uuid").getAsString())
                    .map(s->s.replaceAll("-", ""))
                    .map(s->s.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            LOGGER.debug("Found whitelisted UUIDs : {}", uuidSet);
            setValidator(uuidSet::contains);
        } catch (IOException e) {
            // IGNORE
        }

    }
}