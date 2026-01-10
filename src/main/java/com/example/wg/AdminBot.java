package com.example.wg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class AdminBot extends TelegramLongPollingBot {
    private final String botToken;
    private final Set<Long> adminIds;
    private final Path scriptsDir;
    private final Path clientDir;
    private final String wgInterface;

    public AdminBot(String botToken, Set<Long> adminIds, Path scriptsDir, Path clientDir, String wgInterface) {
        this.botToken = Objects.requireNonNull(botToken, "botToken");
        this.adminIds = Objects.requireNonNull(adminIds, "adminIds");
        this.scriptsDir = Objects.requireNonNull(scriptsDir, "scriptsDir");
        this.clientDir = Objects.requireNonNull(clientDir, "clientDir");
        this.wgInterface = Objects.requireNonNull(wgInterface, "wgInterface");
    }

    @Override
    public String getBotUsername() {
        return "AdminWireGuardBot";
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        var message = update.getMessage();
        long chatId = message.getChatId();
        Long userId = message.getFrom() != null ? message.getFrom().getId() : null;

        if (userId == null || !adminIds.contains(userId)) {
            sendText(chatId, "Access denied.");
            return;
        }

        String text = message.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        List<String> parts = new ArrayList<>(Arrays.asList(text.split("\\s+")));
        String command = parts.remove(0).toLowerCase(Locale.ROOT);

        switch (command) {
            case "/start" -> sendText(chatId, "Admin WireGuard bot is ready. Try /status, /list, /add <name>, /revoke <name>, /config <name>.");
            case "/status" -> handleCommand(chatId, "wg-status.sh", List.of());
            case "/list" -> handleCommand(chatId, "wg-list.sh", List.of());
            case "/add" -> handleAdd(chatId, parts);
            case "/revoke" -> handleRevoke(chatId, parts);
            case "/config" -> handleConfig(chatId, parts);
            default -> sendText(chatId, "Unknown command.");
        }
    }

    private void handleAdd(long chatId, List<String> args) {
        if (args.isEmpty()) {
            sendText(chatId, "Usage: /add <name>");
            return;
        }
        String name = args.get(0);
        if (!isValidName(name)) {
            sendText(chatId, "Invalid name. Use letters, numbers, dashes, and underscores.");
            return;
        }
        handleCommand(chatId, "wg-add.sh", List.of(name));
    }

    private void handleRevoke(long chatId, List<String> args) {
        if (args.isEmpty()) {
            sendText(chatId, "Usage: /revoke <name>");
            return;
        }
        String name = args.get(0);
        if (!isValidName(name)) {
            sendText(chatId, "Invalid name. Use letters, numbers, dashes, and underscores.");
            return;
        }
        handleCommand(chatId, "wg-revoke.sh", List.of(name));
    }

    private void handleConfig(long chatId, List<String> args) {
        if (args.isEmpty()) {
            sendText(chatId, "Usage: /config <name>");
            return;
        }
        String name = args.get(0);
        if (!isValidName(name)) {
            sendText(chatId, "Invalid name. Use letters, numbers, dashes, and underscores.");
            return;
        }

        Path configPath = clientDir.resolve(name + ".conf");
        if (!Files.exists(configPath)) {
            sendText(chatId, "Config not found for " + name + ".");
            return;
        }

        try {
            SendDocument doc = new SendDocument();
            doc.setChatId(chatId);
            doc.setDocument(new InputFile(configPath.toFile()));
            doc.setCaption("Config for " + name + " (generated " + Instant.ofEpochMilli(configPath.toFile().lastModified()) + ").");
            execute(doc);
        } catch (TelegramApiException e) {
            sendText(chatId, "Failed to send config: " + e.getMessage());
        }
    }

    private void handleCommand(long chatId, String scriptName, List<String> args) {
        Path script = scriptsDir.resolve(scriptName);
        if (!Files.exists(script)) {
            sendText(chatId, "Script not found: " + scriptName);
            return;
        }
        if (!Files.isExecutable(script)) {
            try {
                script.toFile().setExecutable(true, false);
            } catch (SecurityException e) {
                sendText(chatId, "Script is not executable: " + scriptName);
                return;
            }
        }

        List<String> command = new ArrayList<>();
        command.add(script.toAbsolutePath().toString());
        command.addAll(args);

        try {
            ProcessResult result = runCommand(command);
            String output = result.output().trim();
            if (output.isEmpty()) {
                output = "(no output)";
            }
            if (result.exitCode() == 0) {
                sendText(chatId, output);
            } else {
                sendText(chatId, "Command failed (exit " + result.exitCode() + "):\n" + output);
            }
        } catch (IOException | InterruptedException e) {
            sendText(chatId, "Command error: " + e.getMessage());
        }
    }

    private ProcessResult runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("WG_INTERFACE", wgInterface);
        builder.environment().putIfAbsent("WG_CLIENT_DIR", clientDir.toAbsolutePath().toString());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output.toString());
    }

    private void sendText(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException ignored) {
            // Intentionally ignored
        }
    }

    private boolean isValidName(String name) {
        return name.matches("[a-zA-Z0-9_-]{1,64}");
    }

    public static AdminBot fromConfig() {
        BotConfig config = ConfigLoader.load();
        return new AdminBot(
            config.botToken(),
            config.adminIds(),
            config.scriptsDir(),
            config.clientDir(),
            config.wgInterface()
        );
    }

    private record ProcessResult(int exitCode, String output) {}
}
