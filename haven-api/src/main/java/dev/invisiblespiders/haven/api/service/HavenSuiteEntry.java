package dev.invisiblespiders.haven.api.service;

import dev.invisiblespiders.haven.api.model.ReloadResult;
import dev.invisiblespiders.haven.api.model.SuiteHealthReport;
import java.util.List;

public interface HavenSuiteEntry {
    String pluginName();
    String pluginVersion();
    List<SuiteHealthReport> health();
    ReloadResult reload();
}
