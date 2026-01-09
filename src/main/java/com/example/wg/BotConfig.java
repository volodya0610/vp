package com.example.wg;

import java.nio.file.Path;
import java.util.Set;

public record BotConfig(
    String botToken,
    Set<Long> adminIds,
    Path scriptsDir,
    Path clientDir,
    String wgInterface
) {}
