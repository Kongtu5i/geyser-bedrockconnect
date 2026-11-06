package org.geysermc.extension.connect.i18n;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

public final class LanguageService {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_-]*)}");
    private final Map<String, String> messages;
    private LanguageService(Map<String, String> messages) { this.messages = Map.copyOf(messages); }

    private static InputStream resource(String file) throws IOException {
        var stream = LanguageService.class.getResourceAsStream("/languages/" + file);
        if (stream == null) throw new IOException("Missing bundled language: " + file);
        return stream;
    }

    private static void flatten(ConfigurationNode node, String prefix, Map<String, String> result) {
        if (node.raw() instanceof String value) result.put(prefix, value);
        node.childrenMap().forEach((key, child) -> flatten(child,
            prefix.isEmpty() ? key.toString() : prefix + "." + key, result));
    }

    private static Map<String, String> bundledEnglish() throws IOException {
        var loader = YamlConfigurationLoader.builder().source(() ->
            new BufferedReader(new InputStreamReader(resource("en_US.yml"), StandardCharsets.UTF_8))).build();
        Map<String, String> result = new LinkedHashMap<>();
        flatten(loader.load(), "", result);
        return result;
    }

    public static LanguageService builtinEnglish() {
        try { return new LanguageService(bundledEnglish()); }
        catch (IOException e) { throw new IllegalStateException("Missing language resources", e); }
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        var matcher = TOKEN.matcher(value);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    public static LanguageService load(Path directory, String file, Consumer<String> warning) throws IOException {
        var fallback = bundledEnglish();
        var languages = directory.toAbsolutePath().normalize().resolve("languages");
        Files.createDirectories(languages);
        for (String name : List.of("zh_CN.yml", "en_US.yml")) {
            var target = languages.resolve(name);
            if (!Files.exists(target)) {
                try (var source = resource(name)) { Files.copy(source, target); }
            }
        }
        Map<String, String> selected = new LinkedHashMap<>();
        try {
            var path = directory.toAbsolutePath().normalize()
                .resolve(file == null || file.isBlank() ? "languages/zh_CN.yml" : file).normalize();
            if (!path.startsWith(languages) || !path.toRealPath().startsWith(languages.toRealPath()))
                throw new IOException("Language path is outside languages directory");
            flatten(YamlConfigurationLoader.builder().path(path).build().load(), "", selected);
        } catch (IOException | RuntimeException e) {
            warning.accept(new LanguageService(fallback).text("log.language-fallback",Map.of("reason",String.valueOf(e.getMessage()))));
            return new LanguageService(fallback);
        }
        Map<String, String> result = new LinkedHashMap<>(fallback);
        List<String> missing = new ArrayList<>();
        fallback.forEach((key, value) -> {
            var translated = selected.get(key);
            if (translated != null && tokens(value).equals(tokens(translated))) result.put(key, translated);
            else missing.add(key);
        });
        if (!missing.isEmpty()) warning.accept(new LanguageService(result).text("log.missing-keys",Map.of("keys",String.join(", ",missing))));
        return new LanguageService(result);
    }

    public String text(String key) { return text(key, Map.of()); }
    public String text(String key, Map<String, ?> arguments) {
        var template = messages.getOrDefault(key, key);
        var matcher = TOKEN.matcher(template);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            var value = arguments.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(value == null ? matcher.group() : value.toString()));
        }
        matcher.appendTail(output);
        return output.toString();
    }
}
